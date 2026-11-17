/**
 * imu_orientation.h - How the MPU6050 sits on the drone, and how its axes map
 * to the drone's body axes.
 *
 * Used by the flight sensor driver (sensors_mpu6050_hm5883L_ms5611.c) and by
 * the IMU bench test (main/imu_bench_test.c), so both always agree.
 *
 * Body frame used by the firmware (Crazyflie convention, right-handed):
 *   x = forward (towards the XIAO's USB-C), y = left, z = up.
 * With this frame the estimator's pitch/roll come out as:
 *   pitch > 0 = nose up, roll > 0 = right side down.
 * Its yaw is counter-clockwise-positive; the bench test prints it negated so
 * that clockwise seen from above is positive.
 *
 * PCB1 mounting (Seeed XIAO ESP32-S3 drone PCB, GY-521 breakout on U1):
 *   - GY-521 under the FC, chip side up (facing the FC), so sensor +Z = up.
 *   - Its pin row runs front-to-back along the FC's left edge, module inward.
 *   - Measured with the bench test tilt check (2026-11-18): sensor +X points
 *     to the drone's right and sensor +Y points forward (towards USB-C).
 *   So: body x (fwd)  = +sensor Y
 *       body y (left) = -sensor X
 *       body z (up)   = +sensor Z
 * Check it with main/imu_bench_test.c and tools/imu-viewer.
 * Gyro and accelerometer share the same axes, so one mapping covers both.
 */
#ifndef IMU_ORIENTATION_H_
#define IMU_ORIENTATION_H_

#define IMU_TO_BODY_X(sx, sy, sz)   ((sy))
#define IMU_TO_BODY_Y(sx, sy, sz)   (-(sx))
#define IMU_TO_BODY_Z(sx, sy, sz)   ((sz))

/* Human-readable form of the mapping above, printed at boot by the bench test */
#define IMU_ORIENTATION_STRING      "body x(fwd)=+sY  y(left)=-sX  z(up)=+sZ"

#endif /* IMU_ORIENTATION_H_ */
