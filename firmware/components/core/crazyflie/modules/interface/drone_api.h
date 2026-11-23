/*
 * drone_api.h - small HTTP API on the drone's Wi-Fi (http://192.168.43.42)
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 */
#ifndef DRONE_API_H_
#define DRONE_API_H_

#include "sdkconfig.h"

#ifdef CONFIG_DRONE_AUTO_ARM
#define DRONE_FW_VERSION "1.2.5-tdc-ios"
#else
#define DRONE_FW_VERSION "1.2.5-tdc"
#endif

/** Start the web server on :80 (status, tools, firmware update). */
void droneApiInit(void);

#endif /* DRONE_API_H_ */
