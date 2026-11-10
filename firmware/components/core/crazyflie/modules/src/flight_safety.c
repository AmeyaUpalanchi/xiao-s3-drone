/*
 * flight_safety.c - arm / disarm, auto-land, motor kill and failsafe landing
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 *
 * The app talks to this module through three parameters (group "safety"):
 *   safety.armed  1 = arm (only accepted while throttle is at zero), 0 = disarm
 *   safety.land   1 = start auto-land, 0 = cancel and give control back
 *   safety.kill   1 = stop the motors immediately (also disarms)
 * and reads back the log variable safety.state (see safetyState_t).
 *
 * There is no altitude sensor on PCB1, so auto-land is a timed descent:
 * hold ~85 % of the thrust the pilot was using (a slow sink from a hover),
 * then ramp to zero. Land from low height for the softest touchdown.
 */
#include <string.h>
#include "FreeRTOS.h"
#include "task.h"
#include "stm32_legacy.h"

#include "flight_safety.h"
#include "commander.h"
#include "pm_esplane.h"
#include "param.h"
#include "log.h"
#define DEBUG_MODULE "SAFETY"
#include "debug_cf.h"

#define LINK_LOST_MS            500     // same as the commander "stabilize" watchdog
#define DESCENT_THRUST_FACTOR   0.85f   // fraction of hover thrust during the sink phase
#define DESCENT_TIME_MS         3000
#define RAMP_DOWN_TIME_MS       1500
#define MIN_FLYING_THRUST       8000.0f // below this the drone is considered on the ground
#define THRUST_FILTER           0.02f   // low-pass of the pilot thrust (1 kHz loop)

static uint8_t armRequest = 0;
static uint8_t landRequest = 0;
static uint8_t killRequest = 0;

static uint8_t state = SAFETY_DISARMED;
static float pilotThrust = 0.0f;        // filtered thrust while armed, ~= hover thrust
static float landStartThrust = 0.0f;
static uint32_t landStartTick = 0;
static float outputThrust = 0.0f;

static void disarm(const char *why)
{
  if (state != SAFETY_DISARMED) {
    DEBUG_PRINTI("disarmed (%s)\n", why);
  }
  state = SAFETY_DISARMED;
  armRequest = 0;
  landRequest = 0;
  pilotThrust = 0.0f;
  outputThrust = 0.0f;
}

static void startLanding(safetyState_t kind)
{
  if (pilotThrust < MIN_FLYING_THRUST) {
    disarm("landing requested on the ground");
    return;
  }
  state = kind;
  landStartThrust = pilotThrust;
  landStartTick = xTaskGetTickCount();
  DEBUG_PRINTI("%s from thrust %.0f\n", kind == SAFETY_FAILSAFE ? "failsafe landing" : "auto-land",
               (double)landStartThrust);
}

static void levelSetpoint(setpoint_t *sp, float thrust)
{
  sp->mode.x = modeDisable;
  sp->mode.y = modeDisable;
  sp->mode.z = modeDisable;
  sp->mode.roll = modeAbs;
  sp->mode.pitch = modeAbs;
  sp->mode.yaw = modeVelocity;
  sp->attitude.roll = 0;
  sp->attitude.pitch = 0;
  sp->attitudeRate.yaw = 0;
  sp->thrust = thrust;
}

void flightSafetyUpdate(setpoint_t *sp)
{
  bool linkLost = commanderGetInactivityTime() > M2T(LINK_LOST_MS);

  // 1. Kill switch wins over everything.
  if (killRequest) {
    killRequest = 0;
    disarm("kill switch");
  }

  // 2. Arm / disarm requests.
  if (state == SAFETY_DISARMED) {
    if (armRequest) {
      if (sp->thrust <= 0.0f && !linkLost) {
        state = SAFETY_ARMED;
        DEBUG_PRINTI("armed\n");
      }
      // else: keep waiting until the throttle stick is down
    }
  } else if (!armRequest) {
    disarm("disarm request");
  }

  switch (state) {
    case SAFETY_DISARMED:
      sp->thrust = 0.0f;
      outputThrust = 0.0f;
      return;

    case SAFETY_ARMED:
      pilotThrust += THRUST_FILTER * (sp->thrust - pilotThrust);
      if (landRequest) {
        startLanding(SAFETY_LANDING);
      } else if (linkLost && pilotThrust > MIN_FLYING_THRUST) {
        startLanding(SAFETY_FAILSAFE);
      } else if (pmIsBatteryLow() && pilotThrust > MIN_FLYING_THRUST) {
        startLanding(SAFETY_FAILSAFE);
      }
      if (state == SAFETY_ARMED) {
        outputThrust = sp->thrust;
        return;
      }
      break;

    case SAFETY_LANDING:
      if (!landRequest) {          // app cancelled the landing
        state = SAFETY_ARMED;
        outputThrust = sp->thrust;
        return;
      }
      break;

    case SAFETY_FAILSAFE:
      if (!linkLost && !pmIsBatteryLow() && !landRequest) {
        state = SAFETY_ARMED;      // link is back: pilot takes over again
        outputThrust = sp->thrust;
        return;
      }
      break;
  }

  // Landing profile (LANDING or FAILSAFE)
  uint32_t elapsed = T2M(xTaskGetTickCount() - landStartTick);
  float thrust;
  if (elapsed < DESCENT_TIME_MS) {
    thrust = landStartThrust * DESCENT_THRUST_FACTOR;
  } else if (elapsed < DESCENT_TIME_MS + RAMP_DOWN_TIME_MS) {
    float k = 1.0f - (float)(elapsed - DESCENT_TIME_MS) / RAMP_DOWN_TIME_MS;
    thrust = landStartThrust * DESCENT_THRUST_FACTOR * k;
  } else {
    disarm("landed");
    sp->thrust = 0.0f;
    return;
  }
  levelSetpoint(sp, thrust);
  outputThrust = thrust;
}

bool flightSafetyMotorsAllowed(void)
{
  return state != SAFETY_DISARMED;
}

safetyState_t flightSafetyGetState(void)
{
  return (safetyState_t)state;
}

PARAM_GROUP_START(safety)
PARAM_ADD(PARAM_UINT8, armed, &armRequest)
PARAM_ADD(PARAM_UINT8, land, &landRequest)
PARAM_ADD(PARAM_UINT8, kill, &killRequest)
PARAM_GROUP_STOP(safety)

LOG_GROUP_START(safety)
LOG_ADD(LOG_UINT8, state, &state)
LOG_ADD(LOG_FLOAT, thrust, &outputThrust)
LOG_GROUP_STOP(safety)
