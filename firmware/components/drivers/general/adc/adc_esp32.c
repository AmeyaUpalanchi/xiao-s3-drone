/**
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
 * adc.c - Analog Digital Conversion
 *
 *
 */

#include "esp_idf_version.h"
#include "driver/adc.h"
#include "esp_adc_cal.h"
#include "adc_esp32.h"
#include "config.h"
#include "pm_esplane.h"
#include "stm32_legacy.h"
#define DEBUG_MODULE "ADC"
#include "debug_cf.h"

static bool isInit;

static esp_adc_cal_characteristics_t *adc_chars;
/* ADC1 channel derived from CONFIG_ADC1_PIN.
 * ESP32:       GPIO36..39 -> CH0..3, GPIO32..35 -> CH4..7
 * ESP32-S2/S3: GPIO1..10  -> CH0..9  (XIAO ESP32-S3: D1 = GPIO2 = ADC1_CH1)
 * The old code hard-coded ADC_CHANNEL_6, which is GPIO7 on S2/S3, so the
 * battery voltage was never read from the VBAT_SENSE pin. */
static adc_channel_t channel = ADC_CHANNEL_0;

static adc_channel_t adcChannelFromGpio(int gpio)
{
#ifdef CONFIG_IDF_TARGET_ESP32
    switch (gpio) {
    case 36: return ADC_CHANNEL_0;
    case 37: return ADC_CHANNEL_1;
    case 38: return ADC_CHANNEL_2;
    case 39: return ADC_CHANNEL_3;
    case 32: return ADC_CHANNEL_4;
    case 33: return ADC_CHANNEL_5;
    case 34: return ADC_CHANNEL_6;
    default: return ADC_CHANNEL_7; // GPIO35
    }
#else
    if (gpio < 1 || gpio > 10) {
        DEBUG_PRINTE("GPIO%d is not an ADC1 pin, falling back to GPIO2", gpio);
        gpio = 2;
    }
    return (adc_channel_t)(gpio - 1);
#endif
}

static const adc_bits_width_t width = ADC_WIDTH_MAX-1;
static const adc_atten_t atten = ADC_ATTEN_DB_11;   //11dB attenuation (ADC_ATTEN_DB_11) gives full-scale voltage 3.9V
static const adc_unit_t unit = ADC_UNIT_1;
#define DEFAULT_VREF 1100 //Use adc2_vref_to_gpio() to obtain a better estimate
#define NO_OF_SAMPLES   30          //Multisampling

static void print_char_val_type(esp_adc_cal_value_t val_type)
{
    if (val_type == ESP_ADC_CAL_VAL_EFUSE_TP) {
        printf("Characterized using Two Point Value\n");
    } else if (val_type == ESP_ADC_CAL_VAL_EFUSE_VREF) {
        printf("Characterized using eFuse Vref\n");
    } else {
        printf("Characterized using Default Vref\n");
    }
}

float analogReadVoltage(uint32_t pin)
{
    uint32_t adc_reading = 0;
    for (int i = 0; i < NO_OF_SAMPLES; i++) {
        if (unit == ADC_UNIT_1) {
            adc_reading += adc1_get_raw((adc1_channel_t)channel);
        } else {
            int raw;
            adc2_get_raw((adc2_channel_t)channel, width, &raw);
            adc_reading += raw;
        }
    }
    adc_reading /= NO_OF_SAMPLES;
    //Convert adc_reading to voltage in mV
    uint32_t voltage = esp_adc_cal_raw_to_voltage(adc_reading, adc_chars);
    return voltage / 1000.0;
}

void adcInit(void)
{
    if (isInit) {
        return;
    }

    channel = adcChannelFromGpio(CONFIG_ADC1_PIN);
    DEBUG_PRINTI("battery ADC on GPIO%d (ADC1_CH%d)", CONFIG_ADC1_PIN, (int)channel);

    if (unit == ADC_UNIT_1) {
        adc1_config_width(width);
        adc1_config_channel_atten(channel, atten);
    } else {
        adc2_config_channel_atten((adc2_channel_t)channel, atten);
    }

    //Characterize ADC
    adc_chars = calloc(1, sizeof(esp_adc_cal_characteristics_t));
    esp_adc_cal_value_t val_type = esp_adc_cal_characterize(unit, atten, width, DEFAULT_VREF, adc_chars);
    print_char_val_type(val_type);

    isInit = true;
}

bool adcTest(void)
{
    return isInit;
}
