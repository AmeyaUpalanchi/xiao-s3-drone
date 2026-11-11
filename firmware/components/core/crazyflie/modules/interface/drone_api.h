/*
 * drone_api.h - small HTTP API on the drone's Wi-Fi (http://192.168.43.42)
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 */
#ifndef DRONE_API_H_
#define DRONE_API_H_

#define DRONE_FW_VERSION "1.2.1-tdc"

/** Start the web server on :80 (status, tools, firmware update). */
void droneApiInit(void);

#endif /* DRONE_API_H_ */
