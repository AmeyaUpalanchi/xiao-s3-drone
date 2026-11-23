/*
 * camera_stream.c - MJPEG live view + photo capture for the XIAO ESP32-S3 Sense
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 */
#include <string.h>
#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_http_server.h"
#include "esp_camera.h"
#include "lwip/sockets.h"
#include "sdkconfig.h"
#include "camera_stream.h"

static const char *TAG = "CAMERA";

/* Seeed Studio XIAO ESP32-S3 Sense camera connector (B2B), from the Seeed wiki.
 * None of these pins are used by the drone PCB (motors GPIO1/3/4/44,
 * I2C GPIO5/6, VBAT GPIO2, LED GPIO21). */
#define CAM_PIN_PWDN    -1
#define CAM_PIN_RESET   -1
#define CAM_PIN_XCLK    10
#define CAM_PIN_SIOD    40
#define CAM_PIN_SIOC    39
#define CAM_PIN_D7      48
#define CAM_PIN_D6      11
#define CAM_PIN_D5      12
#define CAM_PIN_D4      14
#define CAM_PIN_D3      16
#define CAM_PIN_D2      18
#define CAM_PIN_D1      17
#define CAM_PIN_D0      15
#define CAM_PIN_VSYNC   38
#define CAM_PIN_HREF    47
#define CAM_PIN_PCLK    13

#if defined(CONFIG_DRONE_CAM_FRAME_QVGA)
#define CAM_FRAME_SIZE FRAMESIZE_QVGA
#define CAM_FRAME_NAME "QVGA"
#elif defined(CONFIG_DRONE_CAM_FRAME_SVGA)
#define CAM_FRAME_SIZE FRAMESIZE_SVGA
#define CAM_FRAME_NAME "SVGA"
#elif defined(CONFIG_DRONE_CAM_FRAME_HD)
#define CAM_FRAME_SIZE FRAMESIZE_HD
#define CAM_FRAME_NAME "HD"
#else
#define CAM_FRAME_SIZE FRAMESIZE_VGA
#define CAM_FRAME_NAME "VGA"
#endif

#ifndef CONFIG_DRONE_CAM_JPEG_QUALITY
#define CONFIG_DRONE_CAM_JPEG_QUALITY 12
#endif
#ifndef CONFIG_DRONE_CAM_MAX_FPS
#define CONFIG_DRONE_CAM_MAX_FPS 20
#endif

/* Low priority so camera/HTTP work never pre-empts the stabilizer (prio 5),
 * sensors (4) or Wi-Fi/CRTP (3). */
#define CAM_HTTP_TASK_PRIO   1
#define CAM_INIT_TASK_PRIO   1

#define PART_BOUNDARY "espdroneframe"
static const char *STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
/* Boundary and part header go out as one write, so each frame is two sends. */
static const char *STREAM_PART = "\r\n--" PART_BOUNDARY "\r\n"
                                 "Content-Type: image/jpeg\r\nContent-Length: %u\r\nX-Timestamp: %lld\r\n\r\n";

static bool cameraPresent = false;
static const char *sensorName = "none";
static volatile int streamClients = 0;

bool cameraStreamIsPresent(void)
{
    return cameraPresent;
}

static esp_err_t camera_hw_init(void)
{
    camera_config_t config = {
        .pin_pwdn = CAM_PIN_PWDN,
        .pin_reset = CAM_PIN_RESET,
        .pin_xclk = CAM_PIN_XCLK,
        .pin_sccb_sda = CAM_PIN_SIOD,
        .pin_sccb_scl = CAM_PIN_SIOC,
        .pin_d7 = CAM_PIN_D7,
        .pin_d6 = CAM_PIN_D6,
        .pin_d5 = CAM_PIN_D5,
        .pin_d4 = CAM_PIN_D4,
        .pin_d3 = CAM_PIN_D3,
        .pin_d2 = CAM_PIN_D2,
        .pin_d1 = CAM_PIN_D1,
        .pin_d0 = CAM_PIN_D0,
        .pin_vsync = CAM_PIN_VSYNC,
        .pin_href = CAM_PIN_HREF,
        .pin_pclk = CAM_PIN_PCLK,

        .xclk_freq_hz = 20000000,
        /* Motors use LEDC timer 0 (ch 4-7), buzzer uses timer 1. */
        .ledc_timer = LEDC_TIMER_2,
        .ledc_channel = LEDC_CHANNEL_2,

        .pixel_format = PIXFORMAT_JPEG,
        .frame_size = CAM_FRAME_SIZE,
        .jpeg_quality = CONFIG_DRONE_CAM_JPEG_QUALITY,
        .fb_count = 2,
        .fb_location = CAMERA_FB_IN_PSRAM,
        .grab_mode = CAMERA_GRAB_LATEST,
        /* I2C_NUM_0 is the flight sensor bus; the camera gets I2C_NUM_1. */
        .sccb_i2c_port = 1,
    };

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        return err;
    }

    sensor_t *s = esp_camera_sensor_get();
    if (s) {
        camera_sensor_info_t *info = esp_camera_sensor_get_info(&s->id);
        if (info && info->name) {
            sensorName = info->name;
        }
        if (s->id.PID == OV3660_PID) {
            s->set_vflip(s, 1);
            s->set_brightness(s, 1);
            s->set_saturation(s, -2);
        }
    }
    return ESP_OK;
}

/* ---------- HTTP handlers ---------- */

static void add_common_headers(httpd_req_t *req)
{
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store");
}

static esp_err_t capture_handler(httpd_req_t *req)
{
    if (!cameraPresent) {
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "no camera attached");
        return ESP_FAIL;
    }
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) {
        httpd_resp_send_500(req);
        return ESP_FAIL;
    }
    add_common_headers(req);
    httpd_resp_set_type(req, "image/jpeg");
    httpd_resp_set_hdr(req, "Content-Disposition", "inline; filename=capture.jpg");
    esp_err_t res = httpd_resp_send(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
    return res;
}

static esp_err_t stream_handler(httpd_req_t *req)
{
    if (!cameraPresent) {
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "no camera attached");
        return ESP_FAIL;
    }

    esp_err_t res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
    if (res != ESP_OK) {
        return res;
    }
    add_common_headers(req);

    /* Without this, Nagle holds the small header write until the phone ACKs
     * the previous frame, which adds a delayed-ACK stall to every frame. */
    int nodelay = 1;
    setsockopt(httpd_req_to_sockfd(req), IPPROTO_TCP, TCP_NODELAY, &nodelay, sizeof(nodelay));

    const int64_t min_frame_us = 1000000LL / CONFIG_DRONE_CAM_MAX_FPS;
    int64_t last_frame = 0;
    char part_buf[128];
    streamClients++;
    ESP_LOGI(TAG, "stream client connected (%d)", streamClients);

    while (true) {
        int64_t now = esp_timer_get_time();
        int64_t wait = min_frame_us - (now - last_frame);
        if (wait > 1000) {
            vTaskDelay(pdMS_TO_TICKS(wait / 1000));
        }
        last_frame = esp_timer_get_time();

        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) {
            ESP_LOGW(TAG, "frame capture failed");
            vTaskDelay(pdMS_TO_TICKS(50));
            continue;
        }
        size_t hlen = snprintf(part_buf, sizeof(part_buf), STREAM_PART,
                               (unsigned)fb->len, (long long)last_frame);
        res = httpd_resp_send_chunk(req, part_buf, hlen);
        if (res == ESP_OK) {
            res = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
        }
        esp_camera_fb_return(fb);
        if (res != ESP_OK) {
            break;   // client went away
        }
    }

    streamClients--;
    ESP_LOGI(TAG, "stream client disconnected (%d)", streamClients);
    return res;
}

static void start_stream_server(void)
{
    /* The stream blocks its socket for as long as the client watches,
     * so it gets its own small server on :81. */
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.task_priority = CAM_HTTP_TASK_PRIO;
    config.stack_size = 4096;
    config.max_open_sockets = 2;
    config.lru_purge_enable = true;
    config.server_port = 81;
    /* Same core as Wi-Fi and the camera DMA task, away from the flight loop. */
    config.core_id = 0;
    config.ctrl_port = 32769;
    httpd_handle_t stream = NULL;
    if (httpd_start(&stream, &config) == ESP_OK) {
        httpd_uri_t stream_uri = { .uri = "/stream", .method = HTTP_GET, .handler = stream_handler };
        httpd_register_uri_handler(stream, &stream_uri);
    } else {
        ESP_LOGE(TAG, "failed to start stream server on :81");
    }
}

void cameraStreamRegisterHandlers(httpd_handle_t server)
{
    httpd_uri_t capture_uri = { .uri = "/capture", .method = HTTP_GET, .handler = capture_handler };
    httpd_register_uri_handler(server, &capture_uri);
}

const char *cameraStreamSensorName(void)
{
    return sensorName;
}

const char *cameraStreamFrameName(void)
{
    return CAM_FRAME_NAME;
}

static void camera_init_task(void *arg)
{
    /* Let Wi-Fi and the flight stack come up first. */
    vTaskDelay(pdMS_TO_TICKS(1500));

    esp_err_t err = camera_hw_init();
    if (err == ESP_OK) {
        cameraPresent = true;
        ESP_LOGI(TAG, "camera %s ready, %s q=%d, stream on :81/stream",
                 sensorName, CAM_FRAME_NAME, CONFIG_DRONE_CAM_JPEG_QUALITY);
    } else {
        cameraPresent = false;
        ESP_LOGW(TAG, "no camera detected (0x%x) - flying without video", err);
    }

    if (cameraPresent) {
        start_stream_server();
    }
    vTaskDelete(NULL);
}

void cameraStreamInit(void)
{
    static bool started = false;
    if (started) {
        return;
    }
    started = true;
    xTaskCreatePinnedToCore(camera_init_task, "CAM_INIT", 4096, NULL,
                            CAM_INIT_TASK_PRIO, NULL, tskNO_AFFINITY);
}
