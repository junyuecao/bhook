cmake_minimum_required(VERSION 3.18.1)

project(test_memory)

# 设置C标准
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 添加编译选项
add_compile_options(-Wall -Wextra)

# 创建测试内存库
add_library(test_memory SHARED
    test_memory.c
    test_memory_jni.c
)

# 包含头文件目录
target_include_directories(test_memory PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 链接库
target_link_libraries(test_memory
    log
    android
)

# 设置输出目录
set_target_properties(test_memory PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)
