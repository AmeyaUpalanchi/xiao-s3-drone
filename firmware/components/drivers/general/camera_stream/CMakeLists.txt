# Live camera stream for the Seeed XIAO ESP32-S3 Sense (OV2640/OV3660).
# The camera driver comes from the ESP component registry (idf_component.yml).
idf_component_register(SRCS "camera_stream.c"
                       INCLUDE_DIRS "include"
                       REQUIRES esp_http_server esp_timer
                       PRIV_REQUIRES esp32-camera)
