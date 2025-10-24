cmake_minimum_required(VERSION 3.18.1)

project(sohook)

# 设置C标准
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 添加编译选项
add_compile_options(-Wall -Wextra -Werror)

# 查找bytehook库
find_package(bytehook REQUIRED CONFIG)

# 创建sohook共享库
add_library(sohook SHARED
    sohook_jni.c
    memory_tracker.c
)

# 包含头文件目录
target_include_directories(sohook PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 链接库
target_link_libraries(sohook
    bytehook::bytehook
    log
    android
)
target_compile_options(sohook PUBLIC -Wno-unused-parameter)
target_compile_options(sohook PUBLIC -Wno-implicit-int-float-conversion)

# 设置输出目录
set_target_properties(sohook PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)
