/*
 * ESP-Drone Firmware
 * 
 * Copyright 2019-2020  Espressif Systems (Shanghai) 
 * Copyright (C) 2011-2012 Bitcraze AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, in version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/portmacro.h"
#include "nvs_flash.h"

#include "stm32_legacy.h"
#include "platform.h"
#include "system.h"
#define DEBUG_MODULE "APP_MAIN"
#include "debug_cf.h"
#include "esp_system.h"
#include "sdkconfig.h"
#ifdef CONFIG_DRONE_IMU_BENCH_TEST
#include "imu_bench_test.h"
#endif
#ifdef CONFIG_DRONE_MOTOR_AUTO_TEST
#include "motor_auto_test.h"
#endif

void app_main()
{
#ifdef CONFIG_DRONE_MOTOR_AUTO_TEST
    /* Motor auto test: M1..M4 one by one, then motors off. Never returns. */
    motorAutoTestRun();
#endif
#ifdef CONFIG_DRONE_IMU_BENCH_TEST
    /* Bench test: motor pins LOW first, then IMU attitude printing. Never returns. */
    imuBenchHoldMotorsLow();
    imuBenchTestRun();
#endif
    esp_reset_reason_t rr = esp_reset_reason();
    printf("\n\n### LAST RESET REASON = %d ###\n\n", rr);
    /*
    * Initialize the platform and Launch the system task
    * app_main will initialize and start everything
    */

    /* initialize nvs flash prepare for Wi-Fi */
    esp_err_t ret = nvs_flash_init();

    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }

    ESP_ERROR_CHECK(ret);

    /*Initialize the platform.*/
    if (platformInit() == false) {
        while (1);//if  firmware is running on the wrong hardware, Halt
    }

    /*launch the system task */
    systemLaunch();

}
