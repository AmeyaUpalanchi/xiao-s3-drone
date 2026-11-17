#ifndef IMU_BENCH_TEST_H_
#define IMU_BENCH_TEST_H_

/* Drives every motor gate pin LOW */
void imuBenchHoldMotorsLow(void);

/* Runs the IMU bench test (see imu_bench_test.c). Never returns. */
void imuBenchTestRun(void);

#endif /* IMU_BENCH_TEST_H_ */
