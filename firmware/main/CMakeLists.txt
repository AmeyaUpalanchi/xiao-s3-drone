idf_component_register(SRCS "main.c" "imu_bench_test.c" "motor_auto_test.c"
                       INCLUDE_DIRS "."
                       LDFRAGMENTS linker_fragment.lf)