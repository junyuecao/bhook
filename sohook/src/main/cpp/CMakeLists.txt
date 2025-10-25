cmake_minimum_required(VERSION 3.18.1)

project(sohook)

# 设置C标准
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 添加编译选项
add_compile_options(-Wall -Wextra -Werror)

# 查找bytehook库
find_package(bytehook REQUIRED CONFIG)

# 调试信息
message(STATUS "bytehook_FOUND: ${bytehook_FOUND}")
if(TARGET bytehook::bytehook)
    message(STATUS "✓ bytehook::bytehook target found")
    get_target_property(BYTEHOOK_LOCATION bytehook::bytehook IMPORTED_LOCATION)
    message(STATUS "  IMPORTED_LOCATION: ${BYTEHOOK_LOCATION}")
else()
    message(FATAL_ERROR "✗ bytehook::bytehook target NOT found")
endif()

# 创建sohook共享库
add_library(sohook SHARED
    sohook_jni.c
    memory_tracker.c
    memory_hash_table.c
    memory_pool.c
    leak_report.c
    memory_stats.c
    backtrace.c
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

# ============================================
# 测试库：test_memory
# 用于准确性测试的独立内存分配库
# ============================================

# 检查测试源文件是否存在
set(TEST_MEMORY_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../androidTest/cpp")
if(EXISTS "${TEST_MEMORY_DIR}/test_memory.c")
    message(STATUS "Building test_memory library for accuracy tests")
    
    # 创建测试内存库
    add_library(test_memory SHARED
        ${TEST_MEMORY_DIR}/test_memory.c
        ${TEST_MEMORY_DIR}/test_memory_jni.c
    )
    
    # 包含头文件
    target_include_directories(test_memory PRIVATE
        ${TEST_MEMORY_DIR}
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
    
    message(STATUS "✓ test_memory library configured")
else()
    message(STATUS "Test memory sources not found, skipping test library build")
endif()
