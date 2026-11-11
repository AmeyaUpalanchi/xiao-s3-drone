#pragma once
#include <stdbool.h>
#include "esp_http_server.h"

/**
 * Initialise the XIAO ESP32-S3 Sense camera (if present) and start the MJPEG
 * stream on :81/stream. Runs in its own low-priority task; never blocks
 * flight start-up. /status and the other endpoints live in drone_api.c.
 */
void cameraStreamInit(void);

/** true once a camera sensor answered on SCCB and the driver is running. */
bool cameraStreamIsPresent(void);

/** Adds GET /capture (single JPEG) to the drone's main web server. */
void cameraStreamRegisterHandlers(httpd_handle_t server);

/** Camera sensor name ("OV2640", "none", ...) and stream resolution name. */
const char *cameraStreamSensorName(void);
const char *cameraStreamFrameName(void);
