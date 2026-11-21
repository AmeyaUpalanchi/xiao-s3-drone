/*
 * flight_safety.h - arm / disarm, auto-land, motor kill, failsafe landing
 *                   and the disarmed-only "service" mode (motor test, beeper)
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 */
#ifndef FLIGHT_SAFETY_H_
#define FLIGHT_SAFETY_H_

#include <stdbool.h>
#include "stabilizer_types.h"

typedef enum {
  SAFETY_DISARMED = 0,     // motors always off
  SAFETY_ARMED = 1,        // pilot has control
  SAFETY_LANDING = 2,      // auto-land requested from the app
  SAFETY_FAILSAFE = 3,     // auto-land because the link was lost or the battery is critical
  SAFETY_SERVICE = 4,      // disarmed, motors driven by a service routine (test / beep)
} safetyState_t;

/** Call once per stabilizer tick (1 kHz) right after commanderGetSetpoint(). */
void flightSafetyUpdate(setpoint_t *setpoint);

/** false while disarmed: the stabilizer must keep the motors stopped. */
bool flightSafetyMotorsAllowed(void);

/** true while a service routine owns the motors (stabilizer must not touch them). */
bool flightSafetyServiceActive(void);

/** True when a web tool may run: disarmed, or (iOS auto-arm build) armed but idle on
 *  the ground, in which case it disarms and holds off auto-arm for 15 s. */
bool flightSafetyDisarmForTools(void);

/** Enter / leave service mode. Begin fails unless the drone is disarmed. */
bool flightSafetyBeginService(void);
void flightSafetyEndService(void);

safetyState_t flightSafetyGetState(void);

/** Height above ground from the VL53L1X in metres, or -1 when no sensor. */
float flightSafetyGetHeight(void);

#endif /* FLIGHT_SAFETY_H_ */
