/*
 * IMU bench test (CONFIG_DRONE_IMU_BENCH_TEST)
 *
 * Replaces the flight stack with a bench test: no Wi-Fi, no motors, no app.
 *   1. Holds every motor gate LOW.
 *   2. Scans I2C and stops if the MPU6050 (0x68) is missing.
 *   3. Sets the MPU6050 to +-500 deg/s, +-4 g, 42 Hz DLPF, 1 kHz output, polled at 500 Hz.
 *   4. Gyro bias calibration: the drone must be still for 2 s. Redone if it moves.
 *   5. Mahony fusion (sensfusion6.c, the same filter the flight estimator uses)
 *      with the axis mapping from imu_orientation.h.
 *   6. Prints pitch, roll, yaw and battery voltage 20 times a second.
 *
 * Angles: pitch + = nose up, roll + = right side down, yaw + = clockwise seen from above.
 * Yaw is integrated gyro only (no magnetometer): it starts at 0 at power-up and
 * drifts slowly, typically a few degrees per minute.
 *
 * Props off. Switch CONFIG_DRONE_IMU_BENCH_TEST off again before flying.
 */
#include <math.h>
#include <stdio.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "driver/i2c.h"
#include "esp_timer.h"

#include "i2cdev.h"
#include "mpu6050.h"
#include "sensfusion6.h"
#include "adc_esp32.h"
#include "imu_orientation.h"
#include "imu_bench_test.h"

#define BENCH_LOOP_HZ          500
#define BENCH_PRINT_HZ         20
#define BENCH_CAL_SAMPLES      (2 * BENCH_LOOP_HZ)   // 2 s
#define BENCH_CAL_GYRO_STD_MAX 1.0f    // deg/s, more than this means the drone moved
#define BENCH_CAL_ACC_STD_MAX  0.03f   // g
#define BENCH_GYRO_DPS_PER_LSB MPU6050_DEG_PER_LSB_500
#define BENCH_ACC_G_PER_LSB    MPU6050_G_PER_LSB_4
#define BENCH_RAD2DEG          (180.0f / (float)M_PI)

static const int motorPins[] = {
    CONFIG_MOTOR01_PIN, CONFIG_MOTOR02_PIN, CONFIG_MOTOR03_PIN, CONFIG_MOTOR04_PIN
};

void imuBenchHoldMotorsLow(void)
{
    for (int i = 0; i < sizeof(motorPins) / sizeof(motorPins[0]); i++) {
        gpio_set_level(motorPins[i], 0);   // latch 0 before the pin becomes an output
        gpio_config_t io = {
            .pin_bit_mask = 1ULL << motorPins[i],
            .mode = GPIO_MODE_OUTPUT,
            .pull_down_en = GPIO_PULLDOWN_ENABLE,
            .pull_up_en = GPIO_PULLUP_DISABLE,
            .intr_type = GPIO_INTR_DISABLE,
        };
        gpio_config(&io);
        gpio_set_level(motorPins[i], 0);
    }
}

static void halt(const char *msg)
{
    while (1) {
        printf("\n*** STOPPED: %s\n", msg);
        vTaskDelay(pdMS_TO_TICKS(3000));
    }
}

static bool i2cProbe(uint8_t addr)
{
    i2c_cmd_handle_t cmd = i2c_cmd_link_create();
    i2c_master_start(cmd);
    i2c_master_write_byte(cmd, (addr << 1) | I2C_MASTER_WRITE, true);
    i2c_master_stop(cmd);
    esp_err_t err = i2c_master_cmd_begin(sensorsBus.def->i2cPort, cmd, pdMS_TO_TICKS(10));
    i2c_cmd_link_delete(cmd);
    return err == ESP_OK;
}

static bool i2cScan(void)
{
    bool mpuFound = false;
    int found = 0;
    printf("I2C scan on SDA=GPIO%d SCL=GPIO%d:", CONFIG_I2C0_PIN_SDA, CONFIG_I2C0_PIN_SCL);
    for (uint8_t a = 0x08; a < 0x78; a++) {
        if (i2cProbe(a)) {
            printf(" 0x%02X", a);
            found++;
            if (a == MPU6050_ADDRESS_AD0_LOW) {
                mpuFound = true;
            }
        }
    }
    printf(found ? "\n" : " nothing found\n");
    return mpuFound;
}

static uint8_t readReg(uint8_t reg)
{
    uint8_t v = 0xFF;
    i2cdevReadByte(I2C0_DEV, MPU6050_ADDRESS_AD0_LOW, reg, &v);
    return v;
}

static void mpuConfigure(void)
{
    mpu6050Init(I2C0_DEV);
    mpu6050Reset();
    vTaskDelay(pdMS_TO_TICKS(100));
    mpu6050SetSleepEnabled(false);                       // wake from sleep
    vTaskDelay(pdMS_TO_TICKS(50));
    mpu6050SetClockSource(MPU6050_CLOCK_PLL_XGYRO);
    vTaskDelay(pdMS_TO_TICKS(50));
    mpu6050SetIntEnabled(false);                         // INT pin not wired, we poll
    mpu6050SetI2CMasterModeEnabled(false);
    mpu6050SetFullScaleGyroRange(MPU6050_GYRO_FS_500);   // +-500 deg/s
    mpu6050SetFullScaleAccelRange(MPU6050_ACCEL_FS_4);   // +-4 g
    mpu6050SetDLPFMode(MPU6050_DLPF_BW_42);              // ~42 Hz low-pass
    mpu6050SetRate(0);                                   // 1 kHz / (1 + 0) = 1 kHz output
    vTaskDelay(pdMS_TO_TICKS(50));

    printf("MPU6050 WHO_AM_I=0x%02X  PWR_MGMT_1=0x%02X  CONFIG=0x%02X  SMPLRT_DIV=%d  "
           "GYRO_CONFIG=0x%02X  ACCEL_CONFIG=0x%02X\n",
           readReg(MPU6050_RA_WHO_AM_I), readReg(MPU6050_RA_PWR_MGMT_1), readReg(MPU6050_RA_CONFIG),
           readReg(MPU6050_RA_SMPLRT_DIV), readReg(MPU6050_RA_GYRO_CONFIG), readReg(MPU6050_RA_ACCEL_CONFIG));
    if (readReg(MPU6050_RA_WHO_AM_I) != 0x68) {
        printf("Note: WHO_AM_I is not 0x68, this may be an MPU6050 clone. Continuing.\n");
    }
}

/* One burst read of accel + temp + gyro, in sensor axes, in g and deg/s */
static bool mpuRead(float acc[3], float gyro[3])
{
    uint8_t b[14];
    if (!i2cdevReadReg8(I2C0_DEV, MPU6050_ADDRESS_AD0_LOW, MPU6050_RA_ACCEL_XOUT_H, sizeof(b), b)) {
        return false;
    }
    for (int i = 0; i < 3; i++) {
        acc[i] = (int16_t)((b[2 * i] << 8) | b[2 * i + 1]) * BENCH_ACC_G_PER_LSB;
        gyro[i] = (int16_t)((b[8 + 2 * i] << 8) | b[9 + 2 * i]) * BENCH_GYRO_DPS_PER_LSB;
    }
    return true;
}

static void toBody(const float s[3], float out[3])
{
    out[0] = IMU_TO_BODY_X(s[0], s[1], s[2]);
    out[1] = IMU_TO_BODY_Y(s[0], s[1], s[2]);
    out[2] = IMU_TO_BODY_Z(s[0], s[1], s[2]);
}

/* Averages gyro and accel over 2 s; returns false if the drone moved */
static bool calibrate(float gyroBias[3], float accMean[3])
{
    double gs[3] = {0}, gss[3] = {0}, as[3] = {0}, ass[3] = {0};
    int n = 0;
    TickType_t wake = xTaskGetTickCount();

    while (n < BENCH_CAL_SAMPLES) {
        vTaskDelayUntil(&wake, pdMS_TO_TICKS(1000 / BENCH_LOOP_HZ));
        float a[3], g[3];
        if (!mpuRead(a, g)) {
            continue;
        }
        for (int i = 0; i < 3; i++) {
            gs[i] += g[i];
            gss[i] += (double)g[i] * g[i];
            as[i] += a[i];
            ass[i] += (double)a[i] * a[i];
        }
        n++;
    }

    bool still = true;
    float gStd[3], aStd[3];
    for (int i = 0; i < 3; i++) {
        gyroBias[i] = gs[i] / n;
        accMean[i] = as[i] / n;
        gStd[i] = sqrtf(fmaxf(0, gss[i] / n - gyroBias[i] * gyroBias[i]));
        aStd[i] = sqrtf(fmaxf(0, ass[i] / n - accMean[i] * accMean[i]));
        if (gStd[i] > BENCH_CAL_GYRO_STD_MAX || aStd[i] > BENCH_CAL_ACC_STD_MAX) {
            still = false;
        }
    }
    if (!still) {
        printf("Movement seen: gyro noise %.2f %.2f %.2f deg/s (limit %.2f), accel noise %.3f %.3f %.3f g (limit %.3f)\n",
               (double)gStd[0], (double)gStd[1], (double)gStd[2], (double)BENCH_CAL_GYRO_STD_MAX,
               (double)aStd[0], (double)aStd[1], (double)aStd[2], (double)BENCH_CAL_ACC_STD_MAX);
    }
    return still;
}

static float wrap180(float a)
{
    while (a > 180.0f) a -= 360.0f;
    while (a < -180.0f) a += 360.0f;
    return a;
}

void imuBenchTestRun(void)
{
    imuBenchHoldMotorsLow();

    printf("\n=== IMU bench test: flight stack, Wi-Fi and motors are OFF ===\n");
    printf("Motor pins held LOW: GPIO%d GPIO%d GPIO%d GPIO%d\n",
           motorPins[0], motorPins[1], motorPins[2], motorPins[3]);

    i2cdevInit(I2C0_DEV);
    if (!i2cScan()) {
        halt("MPU6050 not found at 0x68. Check 3.3 V, GND, SDA=D4 (GPIO5), SCL=D5 (GPIO6), "
             "and that AD0 is tied to GND.");
    }
    printf("MPU6050 found at 0x68\n");

    mpuConfigure();
    adcInit();
    sensfusion6Init();

    float gyroBias[3], accMean[3];
    while (1) {
        printf("Calibrating gyro: keep the drone still for 2 s...\n");
        if (calibrate(gyroBias, accMean)) {
            break;
        }
        printf("The drone moved during calibration. Put it down and leave it alone. Retrying.\n");
    }
    printf("Gyro bias (sensor axes): %+.2f %+.2f %+.2f deg/s\n",
           (double)gyroBias[0], (double)gyroBias[1], (double)gyroBias[2]);
    printf("Gravity seen by the sensor: X %+.2f  Y %+.2f  Z %+.2f g (chip facing up should give Z about +1)\n",
           (double)accMean[0], (double)accMean[1], (double)accMean[2]);
    printf("Axis mapping: %s\n", IMU_ORIENTATION_STRING);

    /* Settle the filter on the resting gravity vector so it does not start tilted */
    float accBody[3];
    toBody(accMean, accBody);
    for (int i = 0; i < 2000; i++) {
        sensfusion6UpdateQ(0, 0, 0, accBody[0], accBody[1], accBody[2], 0.01f);
    }
    float r0, p0, yaw0;
    sensfusion6GetEulerRPY(&r0, &p0, &yaw0);

    printf("Ready. Angles in degrees: pitch + = nose up, roll + = right side down, "
           "yaw + = clockwise from above.\n");

    TickType_t wake = xTaskGetTickCount();
    int64_t last = esp_timer_get_time();
    int printDiv = 0;
    float rawSum[3] = {0};   // sensor-axis accel, averaged between prints
    while (1) {
        vTaskDelayUntil(&wake, pdMS_TO_TICKS(1000 / BENCH_LOOP_HZ));
        float a[3], g[3], aB[3], gB[3];
        if (!mpuRead(a, g)) {
            continue;
        }
        for (int i = 0; i < 3; i++) {
            g[i] -= gyroBias[i];
            rawSum[i] += a[i];
        }
        toBody(a, aB);
        toBody(g, gB);

        int64_t now = esp_timer_get_time();
        float dt = (now - last) * 1e-6f;
        last = now;
        sensfusion6UpdateQ(gB[0], gB[1], gB[2], aB[0], aB[1], aB[2], dt);

        if (++printDiv >= BENCH_LOOP_HZ / BENCH_PRINT_HZ) {
            printDiv = 0;
            float roll, pitch, yaw;
            sensfusion6GetEulerRPY(&roll, &pitch, &yaw);
            /* The estimator's yaw is counter-clockwise-positive; flip it for clockwise-positive.
             * Gyro-only yaw: 0 at power-up and drifts slowly (no magnetometer). */
            float yawCw = -wrap180(yaw - yaw0);
            float vbat = 2.0f * analogReadVoltage(CONFIG_ADC1_PIN);   // 100k/100k divider
            const float k = (float)BENCH_PRINT_HZ / BENCH_LOOP_HZ;
            /* raw = accelerometer in the sensor's own axes (before mapping), for checking the mapping */
            printf("pitch %+7.1f   roll %+7.1f   yaw %+7.1f   |  battery %.2f V   |  raw g X%+.2f Y%+.2f Z%+.2f\n",
                   (double)pitch, (double)roll, (double)yawCw, (double)vbat,
                   (double)(rawSum[0] * k), (double)(rawSum[1] * k), (double)(rawSum[2] * k));
            rawSum[0] = rawSum[1] = rawSum[2] = 0;
        }
    }
}
