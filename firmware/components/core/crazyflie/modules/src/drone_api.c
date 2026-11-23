/*
 * drone_api.c - HTTP API on the drone's Wi-Fi (http://192.168.43.42)
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 *
 *   GET  /status            camera, battery, flight state, height, Wi-Fi name, firmware
 *   GET  /capture           JPEG photo (XIAO ESP32-S3 Sense only)
 *   POST /api/motor?m=1..4  spin one motor for 0.7 s (m=0: all four in turn)
 *   POST /api/beep          "find my drone" - beeps through the motors
 *   POST /api/level         level calibration (drone still, on flat ground)
 *   POST /api/level/reset   forget the level calibration
 *   GET  /api/wifi          current SSID
 *   POST /api/wifi          name=<1-20 chars>&password=<8-63 chars>, then reboots
 *   POST /api/ota           raw ESPDrone.bin in the body, then reboots
 *   POST /api/reboot
 * Everything that moves motors, changes settings or reboots is refused unless
 * the drone is disarmed.
 */
#include <string.h>
#include <stdio.h>
#include <ctype.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_system.h"
#include "esp_http_server.h"
#include "esp_ota_ops.h"
#include "esp_app_format.h"
#include "nvs.h"
#include "sdkconfig.h"

#include "drone_api.h"
#include "flight_safety.h"
#include "level_calib.h"
#include "pm_esplane.h"
#include "motors.h"
#include "wifi_esp32.h"
#include "system.h"
#ifdef CONFIG_DRONE_CAMERA
#include "camera_stream.h"
#endif

static const char *TAG = "API";

/* motors.c */
extern void motorsPlayTone(uint16_t frequency, uint16_t duration_msec);

static httpd_handle_t server;
static SemaphoreHandle_t serviceLock;

/* ---------------------------------------------------------------- helpers */

static esp_err_t send_json(httpd_req_t *req, const char *status, const char *json)
{
    httpd_resp_set_status(req, status);
    httpd_resp_set_type(req, "application/json");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store");
    return httpd_resp_sendstr(req, json);
}

static esp_err_t send_error(httpd_req_t *req, const char *status, const char *msg)
{
    char buf[160];
    snprintf(buf, sizeof(buf), "{\"ok\":false,\"error\":\"%s\"}", msg);
    return send_json(req, status, buf);
}

static esp_err_t send_error_fail(httpd_req_t *req, const char *status, const char *msg)
{
    send_error(req, status, msg);
    return ESP_FAIL;
}

static void restartCb(void *arg)
{
    esp_restart();
}

static void restartSoon(void)
{
    static esp_timer_handle_t t;
    if (!t) {
        const esp_timer_create_args_t args = { .callback = restartCb, .name = "restart" };
        esp_timer_create(&args, &t);
    }
    esp_timer_start_once(t, 1500 * 1000);
}

static int urlDecode(char *s)
{
    char *o = s;
    for (char *p = s; *p; p++) {
        if (*p == '+') {
            *o++ = ' ';
        } else if (*p == '%' && isxdigit((unsigned char)p[1]) && isxdigit((unsigned char)p[2])) {
            char hex[3] = { p[1], p[2], 0 };
            *o++ = (char)strtol(hex, NULL, 16);
            p += 2;
        } else {
            *o++ = *p;
        }
    }
    *o = 0;
    return o - s;
}

/* ---------------------------------------------------------------- status */

static esp_err_t status_handler(httpd_req_t *req)
{
    char json[600];
    float lp, lr;
    levelCalibGet(&lp, &lr);
    float h = flightSafetyGetHeight();
#ifdef CONFIG_DRONE_CAMERA
    bool cam = cameraStreamIsPresent();
    const char *sensor = cameraStreamSensorName();
    const char *frame = cameraStreamFrameName();
#else
    bool cam = false;
    const char *sensor = "none";
    const char *frame = "";
#endif
    snprintf(json, sizeof(json),
             "{\"camera\":%s,\"sensor\":\"%s\",\"frame\":\"%s\","
             "\"battery_v\":%.2f,\"battery_pct\":%u,"
             "\"state\":%d,\"height\":%.2f,\"tof\":%s,"
             "\"level_pitch\":%.2f,\"level_roll\":%.2f,"
             "\"ssid\":\"%s\",\"channel\":%u,\"fw\":\"%s\",\"ota\":true,\"stream_port\":81,"
             "\"imu\":%s,\"selftest\":%s,\"board\":\"XIAO_ESP32S3\"}",
             cam ? "true" : "false", sensor, frame,
             pmGetBatteryVoltage(), (unsigned)pmGetBatteryPercent(),
             (int)flightSafetyGetState(), h, h >= 0 ? "true" : "false",
             lp, lr, wifiGetSsid(), (unsigned)wifiGetChannel(), DRONE_FW_VERSION,
             sensorsImuPresent() ? "true" : "false", systemIsSelftestPassed() ? "true" : "false");
    return send_json(req, "200 OK", json);
}

/* ---------------------------------------------------------------- service */

static bool beginService(httpd_req_t *req)
{
    if (xSemaphoreTake(serviceLock, 0) != pdTRUE) {
        send_error(req, "409 Conflict", "busy");
        return false;
    }
    if (!flightSafetyBeginService()) {
        xSemaphoreGive(serviceLock);
        send_error(req, "409 Conflict", "disarm the drone first");
        return false;
    }
    return true;
}

static void endService(void)
{
    for (int i = 0; i < NBR_OF_MOTORS; i++) {
        motorsSetRatio(i, 0);
    }
    flightSafetyEndService();
    xSemaphoreGive(serviceLock);
}

static esp_err_t motor_handler(httpd_req_t *req)
{
    /* A cut-off query (e.g. a long cache-buster after m=1) must not fall
     * back to m=0, which spins all four motors. */
    char q[96] = {0}, v[8] = {0};
    int m = 0;
    esp_err_t qe = httpd_req_get_url_query_str(req, q, sizeof(q));
    if (qe == ESP_OK) {
        esp_err_t ve = httpd_query_key_value(q, "m", v, sizeof(v));
        if (ve == ESP_OK) {
            m = atoi(v);
        } else if (ve != ESP_ERR_NOT_FOUND) {
            return send_error(req, "400 Bad Request", "m must be 0-4");
        }
    } else if (qe != ESP_ERR_NOT_FOUND) {
        return send_error(req, "400 Bad Request", "query too long");
    }
    if (m < 0 || m > NBR_OF_MOTORS) {
        return send_error(req, "400 Bad Request", "m must be 0-4");
    }
    if (!beginService(req)) {
        return ESP_OK;
    }
    int first = m ? m - 1 : 0, last = m ? m - 1 : NBR_OF_MOTORS - 1;
    for (int i = first; i <= last; i++) {
        motorsSetRatio(i, MOTORS_TEST_RATIO);
        vTaskDelay(pdMS_TO_TICKS(700));
        motorsSetRatio(i, 0);
        vTaskDelay(pdMS_TO_TICKS(300));
    }
    endService();
    return send_json(req, "200 OK", "{\"ok\":true}");
}

static esp_err_t beep_handler(httpd_req_t *req)
{
    if (!beginService(req)) {
        return ESP_OK;
    }
    for (int r = 0; r < 3; r++) {
        motorsPlayTone(C6, 120);
        motorsPlayTone(E6, 120);
        motorsPlayTone(G6, 220);
        vTaskDelay(pdMS_TO_TICKS(350));
    }
    endService();
    return send_json(req, "200 OK", "{\"ok\":true}");
}

/* ---------------------------------------------------------------- level */

static esp_err_t level_handler(httpd_req_t *req)
{
    /* Service mode for the whole measurement, so nobody can arm meanwhile. */
    if (!beginService(req)) {
        return ESP_OK;
    }
    float p, r;
    const char *err = "unknown";
    bool ok = levelCalibRun(&p, &r, &err);
    endService();
    if (!ok) {
        return send_error(req, "422 Unprocessable Entity", err);
    }
    char json[96];
    snprintf(json, sizeof(json), "{\"ok\":true,\"pitch\":%.2f,\"roll\":%.2f}", p, r);
    return send_json(req, "200 OK", json);
}

static esp_err_t level_reset_handler(httpd_req_t *req)
{
    if (!beginService(req)) {
        return ESP_OK;
    }
    levelCalibReset();
    endService();
    return send_json(req, "200 OK", "{\"ok\":true}");
}

/* ---------------------------------------------------------------- wifi */

static esp_err_t wifi_get_handler(httpd_req_t *req)
{
    char json[96];
    snprintf(json, sizeof(json), "{\"ssid\":\"%s\"}", wifiGetSsid());
    return send_json(req, "200 OK", json);
}

static esp_err_t wifi_post(httpd_req_t *req);

static esp_err_t wifi_post_handler(httpd_req_t *req)
{
    /* Held until the restart: the drone can't be armed in between. */
    if (!beginService(req)) {
        return ESP_OK;
    }
    esp_err_t res = wifi_post(req);
    if (res != ESP_OK) {
        endService();
    }
    return ESP_OK;
}

/* Returns ESP_OK only when the settings were saved and a restart is coming. */
static esp_err_t wifi_post(httpd_req_t *req)
{
    /* A 63-character password can be up to 189 bytes once URL-encoded. */
    char body[320] = {0};
    if (req->content_len >= (int)sizeof(body)) {
        return send_error_fail(req, "400 Bad Request", "too long");
    }
    int len = req->content_len;
    int got = 0;
    while (got < len) {
        int r = httpd_req_recv(req, body + got, len - got);
        if (r <= 0) {
            return send_error_fail(req, "400 Bad Request", "no body");
        }
        got += r;
    }
    char name[64] = {0}, pass[200] = {0};
    /* A truncated value would save a different password than the user typed. */
    esp_err_t ne = httpd_query_key_value(body, "name", name, sizeof(name));
    esp_err_t pe = httpd_query_key_value(body, "password", pass, sizeof(pass));
    if (ne == ESP_ERR_HTTPD_RESULT_TRUNC || pe == ESP_ERR_HTTPD_RESULT_TRUNC) {
        return send_error_fail(req, "400 Bad Request", "too long");
    }
    /* No name= at all means "keep the name"; name= with nothing means "default". */
    bool setName = (ne == ESP_OK);
    urlDecode(name);
    urlDecode(pass);

    size_t nl = strlen(name), pl = strlen(pass);
    if (nl > 20) {
        return send_error_fail(req, "400 Bad Request", "name: up to 20 characters");
    }
    for (size_t i = 0; i < nl; i++) {
        if (!isalnum((unsigned char)name[i]) && name[i] != '-' && name[i] != '_') {
            return send_error_fail(req, "400 Bad Request", "name: letters, numbers, - and _ only");
        }
    }
    if (pl != 0 && (pl < 8 || pl > 63)) {
        return send_error_fail(req, "400 Bad Request", "password: 8 to 63 characters");
    }
    for (size_t i = 0; i < pl; i++) {
        if (pass[i] < 0x20 || pass[i] > 0x7e || pass[i] == '"' || pass[i] == '\\') {
            return send_error_fail(req, "400 Bad Request", "password: plain characters only");
        }
    }

    nvs_handle_t h;
    if (nvs_open("tdc", NVS_READWRITE, &h) != ESP_OK) {
        return send_error_fail(req, "500 Internal Server Error", "storage");
    }
    esp_err_t e = ESP_OK;
    if (setName) {
        if (nl) {
            e = nvs_set_str(h, "wifi_name", name);
        } else if (nvs_erase_key(h, "wifi_name") == ESP_ERR_NVS_NOT_FOUND) {
            e = ESP_OK;
        }
    }
    if (e == ESP_OK && pl) {
        e = nvs_set_str(h, "wifi_pass", pass);
    }
    if (e == ESP_OK) {
        e = nvs_commit(h);
    }
    nvs_close(h);
    if (e != ESP_OK) {
        return send_error_fail(req, "500 Internal Server Error", "could not save");
    }

    char ssid[48];
    if (!setName) {
        snprintf(ssid, sizeof(ssid), "%s", wifiGetSsid());
    } else {
        snprintf(ssid, sizeof(ssid), "ESP-DRONE_%s", nl ? name : "<mac>");
    }
    char json[128];
    snprintf(json, sizeof(json), "{\"ok\":true,\"ssid\":\"%s\",\"restart\":true}", ssid);
    send_json(req, "200 OK", json);
    restartSoon();
    return ESP_OK;
}

/* ---------------------------------------------------------------- ota */

static esp_err_t ota_upload(httpd_req_t *req);

static esp_err_t ota_handler(httpd_req_t *req)
{
    /* Service mode for the whole upload and until the restart, so the drone
     * can't be armed and flown while flash writes stall the flight loop. */
    if (!beginService(req)) {
        return ESP_OK;
    }
    if (ota_upload(req) != ESP_OK) {
        endService();
    }
    return ESP_OK;
}

static esp_err_t ota_upload(httpd_req_t *req)
{
    const esp_partition_t *part = esp_ota_get_next_update_partition(NULL);
    if (!part) {
        return send_error_fail(req, "500 Internal Server Error", "no OTA partition (flash once over USB)");
    }
    if (req->content_len <= 0 || (size_t)req->content_len > part->size) {
        return send_error_fail(req, "400 Bad Request", "file size does not fit");
    }

    char *buf = malloc(4096);
    if (!buf) {
        return send_error_fail(req, "500 Internal Server Error", "out of memory");
    }
    esp_ota_handle_t ota = 0;
    bool started = false;
    int remaining = req->content_len;
    esp_err_t err = ESP_OK;
    const char *msg = NULL;

    const size_t descOff = sizeof(esp_image_header_t) + sizeof(esp_image_segment_header_t);
    const int headLen = (int)(descOff + sizeof(esp_app_desc_t));
    int timeouts = 0;
    int have = 0;       // bytes in buf not yet written
    while (remaining > 0) {
        int r = httpd_req_recv(req, buf + have, (remaining < 4096 - have) ? remaining : 4096 - have);
        if (r == HTTPD_SOCK_ERR_TIMEOUT) {
            /* A phone that walked out of range never resets the connection;
             * don't keep the only web server task waiting forever. */
            if (++timeouts > 3) {
                msg = "upload interrupted";
                break;
            }
            continue;
        }
        if (r <= 0) {
            msg = "upload interrupted";
            break;
        }
        timeouts = 0;
        remaining -= r;
        have += r;
        if (!started && have < headLen && remaining > 0) {
            continue;       // need the whole header before checking it
        }
        r = have;
        have = 0;
        if (!started) {
            if (r < headLen || (uint8_t)buf[0] != ESP_IMAGE_HEADER_MAGIC) {
                msg = "not an ESP32 firmware file";
                break;
            }
            const esp_app_desc_t *d = (const esp_app_desc_t *)(buf + descOff);
            if (d->magic_word != ESP_APP_DESC_MAGIC_WORD || strncmp(d->project_name, "ESPDrone", 8) != 0) {
                msg = "this is not drone firmware (ESPDrone.bin)";
                break;
            }
            ESP_LOGI(TAG, "OTA %s %s -> %s", d->project_name, d->version, part->label);
            err = esp_ota_begin(part, OTA_WITH_SEQUENTIAL_WRITES, &ota);
            if (err != ESP_OK) {
                msg = "could not start update";
                break;
            }
            started = true;
        }
        err = esp_ota_write(ota, buf, r);
        if (err != ESP_OK) {
            msg = "flash write failed";
            break;
        }
    }
    free(buf);

    if (msg) {
        if (started) {
            esp_ota_abort(ota);
        }
        return send_error_fail(req, "400 Bad Request", msg);
    }
    if (esp_ota_end(ota) != ESP_OK) {
        return send_error_fail(req, "400 Bad Request", "firmware image failed verification");
    }
    if (esp_ota_set_boot_partition(part) != ESP_OK) {
        return send_error_fail(req, "500 Internal Server Error", "could not select new firmware");
    }
    send_json(req, "200 OK", "{\"ok\":true,\"restart\":true}");
    restartSoon();
    return ESP_OK;
}

static esp_err_t reboot_handler(httpd_req_t *req)
{
    if (!beginService(req)) {       // held until the restart
        return ESP_OK;
    }
    send_json(req, "200 OK", "{\"ok\":true}");
    restartSoon();
    return ESP_OK;
}

/* ---------------------------------------------------------------- init */

void droneApiInit(void)
{
    if (server) {
        return;
    }
    serviceLock = xSemaphoreCreateMutex();

    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.task_priority = 1;           // below flight, sensor and radio tasks
    config.stack_size = 8192;
    config.max_open_sockets = 4;
    config.max_uri_handlers = 16;
    config.lru_purge_enable = true;
    config.recv_wait_timeout = 10;
    config.server_port = 80;
    config.ctrl_port = 32768;
    if (httpd_start(&server, &config) != ESP_OK) {
        ESP_LOGE(TAG, "could not start web server");
        server = NULL;
        return;
    }

    const httpd_uri_t uris[] = {
        { .uri = "/status",          .method = HTTP_GET,  .handler = status_handler },
        { .uri = "/api/motor",       .method = HTTP_POST, .handler = motor_handler },
        { .uri = "/api/beep",        .method = HTTP_POST, .handler = beep_handler },
        { .uri = "/api/level",       .method = HTTP_POST, .handler = level_handler },
        { .uri = "/api/level/reset", .method = HTTP_POST, .handler = level_reset_handler },
        { .uri = "/api/wifi",        .method = HTTP_GET,  .handler = wifi_get_handler },
        { .uri = "/api/wifi",        .method = HTTP_POST, .handler = wifi_post_handler },
        { .uri = "/api/ota",         .method = HTTP_POST, .handler = ota_handler },
        { .uri = "/api/reboot",      .method = HTTP_POST, .handler = reboot_handler },
    };
    for (size_t i = 0; i < sizeof(uris) / sizeof(uris[0]); i++) {
        httpd_register_uri_handler(server, &uris[i]);
    }
#ifdef CONFIG_DRONE_CAMERA
    cameraStreamRegisterHandlers(server);
#endif
    ESP_LOGI(TAG, "drone API on http://192.168.43.42 (fw %s)", DRONE_FW_VERSION);
}
