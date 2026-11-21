/*
 * motor_auto_test.c - spins the four motors one by one at power-up, on its own.
 *
 * Enabled with CONFIG_DRONE_MOTOR_AUTO_TEST (off by default). No Wi-Fi, no app,
 * no flight stack. PROPS OFF.
 *
 *   1. Motor pins LOW at once.
 *   2. Waits 5 s after power-up (connect the battery and let go).
 *   3. Motor N gives N short pulses at 20 %: M1 = 1 pulse, M2 = 2, M3 = 3, M4 = 4,
 *      with a 1.5 s gap between motors, so you can tell which motor is which.
 *   4. Repeats that 3 times with a 4 s pause, then stops for good (reset to rerun).
 *   Stops early if the battery reads below 3.3 V (ignored when no battery is seen).
 *   Everything is also printed on the USB-C console.
 */
#include <stdio.h>
#include <stdbool.h>
#include "soc/usb_serial_jtag_struct.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "sdkconfig.h"
#include "motors.h"
#include "adc_esp32.h"
#include "imu_bench_test.h"
#include "motor_auto_test.h"

#define TEST_RATIO      ((uint16_t)(0.20f * 65535))
#define PULSE_ON_MS     400
#define PULSE_OFF_MS    300
#define MOTOR_GAP_MS    1500
#define CYCLE_GAP_MS    4000
#define CYCLES          3
#define START_DELAY_MS  5000
#define VBAT_MIN        3.3f

static const char *names[NBR_OF_MOTORS] = {
    "M1 front-right", "M2 back-right", "M3 back-left", "M4 front-left"
};

static float readBattery(void)
{
    return 2.0f * analogReadVoltage(CONFIG_ADC1_PIN);   // 100k/100k divider
}

/* A USB host sends a start-of-frame every 1 ms, so a changing SOF frame index
 * means a computer is plugged in. Battery-only: the index never moves. */
static bool usbHostConnected(void)
{
    uint32_t a = USB_SERIAL_JTAG.fram_num.sof_frame_index;
    vTaskDelay(pdMS_TO_TICKS(20));
    return USB_SERIAL_JTAG.fram_num.sof_frame_index != a;
}

static void allOff(void)
{
    for (int i = 0; i < NBR_OF_MOTORS; i++) {
        motorsSetRatio(i, 0);
    }
}

void motorAutoTestRun(void)
{
    imuBenchHoldMotorsLow();
    adcInit();

    printf("\n=== MOTOR AUTO TEST: props OFF. Wi-Fi and flight stack are OFF ===\n");
    /* Never spin with a computer on USB: on boards with the battery on VUSB the
     * motors would pull their current from the computer's USB port. */
    if (usbHostConnected()) {
        printf("USB computer connected: motors will NOT spin.\n"
               "Unplug USB and power the drone from the battery to run the test.\n");
        while (1) {
            vTaskDelay(pdMS_TO_TICKS(1000));
        }
    }
    printf("Motor N gives N pulses at 20%%. Starting in %d s.\n", START_DELAY_MS / 1000);
    vTaskDelay(pdMS_TO_TICKS(START_DELAY_MS));

    motorsInit(motorMapDefaultBrushed);
    allOff();

    for (int c = 1; c <= CYCLES; c++) {
        float v = readBattery();
        printf("\n--- round %d of %d, battery %.2f V ---\n", c, CYCLES, v);
        if (v > 1.0f && v < VBAT_MIN) {
            printf("Battery low (%.2f V < %.1f V). Stopping.\n", v, VBAT_MIN);
            break;
        }
        for (int m = 0; m < NBR_OF_MOTORS; m++) {
            printf("%s: %d pulse%s\n", names[m], m + 1, m ? "s" : "");
            for (int p = 0; p <= m; p++) {
                motorsSetRatio(m, TEST_RATIO);
                vTaskDelay(pdMS_TO_TICKS(PULSE_ON_MS));
                motorsSetRatio(m, 0);
                vTaskDelay(pdMS_TO_TICKS(PULSE_OFF_MS));
            }
            vTaskDelay(pdMS_TO_TICKS(MOTOR_GAP_MS));
        }
        if (c < CYCLES) {
            vTaskDelay(pdMS_TO_TICKS(CYCLE_GAP_MS));
        }
    }

    allOff();
    printf("\nMotor auto test done. Motors off. Press reset (or reconnect the battery) to run again.\n");
    while (1) {
        allOff();
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}
