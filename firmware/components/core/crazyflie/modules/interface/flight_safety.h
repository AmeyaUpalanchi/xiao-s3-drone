/*
 * flight_safety.h - arm / disarm, auto-land, motor kill and failsafe landing
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
} safetyState_t;

/**
 * Call once per stabilizer tick (1 kHz) right after commanderGetSetpoint().
 * Overrides the setpoint while landing and zeroes thrust while disarmed.
 */
void flightSafetyUpdate(setpoint_t *setpoint);

/** false while disarmed: the stabilizer must keep the motors stopped. */
bool flightSafetyMotorsAllowed(void);

safetyState_t flightSafetyGetState(void);

#endif /* FLIGHT_SAFETY_H_ */
