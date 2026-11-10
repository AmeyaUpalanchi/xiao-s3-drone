#pragma once
#include <stdbool.h>

/**
 * Initialise the XIAO ESP32-S3 Sense camera (if present) and start the
 * HTTP endpoints on the drone soft-AP (192.168.43.42):
 *   GET /status         -> JSON {camera, sensor, battery_v, battery_pct, ...}
 *   GET /capture        -> single JPEG frame
 *   GET :81/stream      -> MJPEG (multipart/x-mixed-replace)
 * Runs in its own low-priority task; never blocks flight start-up.
 */
void cameraStreamInit(void);

/** true once a camera sensor answered on SCCB and the driver is running. */
bool cameraStreamIsPresent(void);
