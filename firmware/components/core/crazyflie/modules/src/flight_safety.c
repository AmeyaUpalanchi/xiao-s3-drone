/*
 * flight_safety.c - arm / disarm, auto-land, motor kill, failsafe landing
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 *
 * The app talks to this module through three parameters (group "safety"):
 *   safety.armed  1 = arm (only accepted while throttle is at zero), 0 = disarm
 *   safety.land   1 = start auto-land, 0 = cancel and give control back
 *   safety.kill   1 = stop the motors immediately (also disarms)
 * and reads back the log variables safety.state and safety.height.
 *
 * Landing has two profiles:
 *  - Height sensor (VL53L1X on a Qwiic port, altitude-hold mode): descend at
 *    0.3 m/s and disarm when the sensor reads under 6 cm.
 *  - No height sensor: timed descent, ~85 % of the pilot's hover thrust for
 *    3 s, then ramp to zero over 1.5 s.
 */
#include <string.h>
#include "FreeRTOS.h"
#include "task.h"
#include "stm32_legacy.h"

#include "flight_safety.h"
#include "commander.h"
#include "pm_esplane.h"
#include "range.h"
#include "zranger2.h"
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
#define LAND_VELOCITY           -0.3f   // m/s, height-sensor landing
#define GROUND_HEIGHT_M         0.06f
#define FLYING_HEIGHT_M         0.10f
#define HEIGHT_LAND_TIMEOUT_MS  20000

static uint8_t armRequest = 0;
static uint8_t landRequest = 0;
static uint8_t killRequest = 0;

static uint8_t state = SAFETY_DISARMED;
static bool landWithHeight = false;
static float pilotThrust = 0.0f;        // filtered thrust while armed, ~= hover thrust
static float landStartThrust = 0.0f;
static uint32_t landStartTick = 0;
static uint32_t groundSinceTick = 0;
static float outputThrust = 0.0f;
static float heightM = -1.0f;

static bool heightSensorPresent(void)
{
  return zRanger2Test();
}

float flightSafetyGetHeight(void)
{
  return heightM;
}

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

static bool isFlying(const setpoint_t *sp)
{
  if (heightM >= 0.0f && sp->mode.z == modeVelocity) {
    return heightM > FLYING_HEIGHT_M;
  }
  return pilotThrust > MIN_FLYING_THRUST;
}

static void startLanding(safetyState_t kind, const setpoint_t *sp)
{
  if (!isFlying(sp)) {
    disarm("landing requested on the ground");
    return;
  }
  state = kind;
  landWithHeight = (heightM >= 0.0f && sp->mode.z == modeVelocity);
  landStartThrust = pilotThrust;
  landStartTick = xTaskGetTickCount();
  groundSinceTick = 0;
  DEBUG_PRINTI("%s (%s)\n", kind == SAFETY_FAILSAFE ? "failsafe landing" : "auto-land",
               landWithHeight ? "height sensor" : "timed");
}

static void levelAttitude(setpoint_t *sp)
{
  sp->mode.x = modeDisable;
  sp->mode.y = modeDisable;
  sp->mode.roll = modeAbs;
  sp->mode.pitch = modeAbs;
  sp->mode.yaw = modeVelocity;
  sp->attitude.roll = 0;
  sp->attitude.pitch = 0;
  sp->attitudeRate.yaw = 0;
}

void flightSafetyUpdate(setpoint_t *sp)
{
  bool linkLost = commanderGetInactivityTime() > M2T(LINK_LOST_MS);
  heightM = heightSensorPresent() ? rangeGet(rangeDown) / 1000.0f : -1.0f;

  // Service mode (motor test / beeper) owns the motors; never arm meanwhile.
  if (state == SAFETY_SERVICE) {
    armRequest = 0;
    landRequest = 0;
    killRequest = 0;
    sp->thrust = 0.0f;
    outputThrust = 0.0f;
    return;
  }

  // 1. Kill switch wins over everything.
  if (killRequest) {
    killRequest = 0;
    disarm("kill switch");
  }

  // 2. Arm / disarm requests.
  if (state == SAFETY_DISARMED) {
    if (armRequest && sp->thrust <= 0.0f && !linkLost) {
      state = SAFETY_ARMED;
      DEBUG_PRINTI("armed\n");
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
        startLanding(SAFETY_LANDING, sp);
      } else if (linkLost && isFlying(sp)) {
        startLanding(SAFETY_FAILSAFE, sp);
      } else if (pmIsBatteryLow() && isFlying(sp)) {
        startLanding(SAFETY_FAILSAFE, sp);
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

    default:
      break;
  }

  uint32_t now = xTaskGetTickCount();
  uint32_t elapsed = T2M(now - landStartTick);
  levelAttitude(sp);

  if (landWithHeight) {
    // Altitude-hold landing: sink at a fixed rate, stop on the ground.
    sp->mode.z = modeVelocity;
    sp->velocity.z = LAND_VELOCITY;
    sp->thrust = 0.0f;
    if (heightM >= 0.0f && heightM < GROUND_HEIGHT_M) {
      if (groundSinceTick == 0) {
        groundSinceTick = now;
      } else if (T2M(now - groundSinceTick) > 300) {
        disarm("landed (height)");
        return;
      }
    } else {
      groundSinceTick = 0;
    }
    if (elapsed > HEIGHT_LAND_TIMEOUT_MS) {
      disarm("landing timeout");
    }
    return;
  }

  // Timed landing profile
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
  sp->mode.z = modeDisable;
  sp->thrust = thrust;
  outputThrust = thrust;
}

bool flightSafetyMotorsAllowed(void)
{
  return state != SAFETY_DISARMED && state != SAFETY_SERVICE;
}

bool flightSafetyServiceActive(void)
{
  return state == SAFETY_SERVICE;
}

bool flightSafetyBeginService(void)
{
  if (state != SAFETY_DISARMED || armRequest) {
    return false;
  }
  state = SAFETY_SERVICE;
  return true;
}

void flightSafetyEndService(void)
{
  if (state == SAFETY_SERVICE) {
    state = SAFETY_DISARMED;
  }
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
LOG_ADD(LOG_FLOAT, height, &heightM)
LOG_GROUP_STOP(safety)
