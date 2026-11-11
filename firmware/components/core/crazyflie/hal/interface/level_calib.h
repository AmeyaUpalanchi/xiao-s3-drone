/*
 * level_calib.h - store how the IMU sits on the frame, so "level" really is level
 *
 * Copyright (C) 2026 The Drone Community
 * Licensed under the GNU General Public License v3 (same as ESP-Drone).
 */
#ifndef LEVEL_CALIB_H_
#define LEVEL_CALIB_H_

#include <stdbool.h>

/**
 * Average the accelerometer for ~1 s with the drone resting on flat ground and
 * store the mounting tilt in NVS. Blocking. Returns false (and sets *err) if the
 * drone is moving or tilted more than 15 degrees.
 */
bool levelCalibRun(float *pitchDeg, float *rollDeg, const char **err);

/** Forget the stored calibration (back to the Kconfig defaults). */
void levelCalibReset(void);

/** Current calibration angles in degrees. */
void levelCalibGet(float *pitchDeg, float *rollDeg);

/** false if the MPU-6050 did not answer at boot (drone stays grounded). */
bool sensorsImuPresent(void);

#endif /* LEVEL_CALIB_H_ */
