cmake_minimum_required(VERSION 3.18.1)

project(sohook)

if((${ANDROID_ABI} STREQUAL "arm64-v8a") OR (${ANDROID_ABI} STREQUAL "x86_64"))
    set(ARCH_LINK_FLAGS "-Wl,-z,max-page-size=16384")
else()
    set(ARCH_LINK_FLAGS "")
endif()

# 设置C/C++标准
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(DEFAULT_WARNING_FLAGS -Wno-unused-parameter -Wno-implicit-int-float-conversion -Wno-unused-variable)

# 添加编译选项
add_compile_options(-Wall -Wextra -Werror)

 if(DEPENDENCY_ON_LOCAL_LIBRARY)
     message("CJY")
     include(${CMAKE_CURRENT_SOURCE_DIR}/../../../../bytehook/local_dependency.cmake)
 else()
     find_package(bytehook REQUIRED CONFIG)
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
target_compile_options(sohook PUBLIC ${DEFAULT_WARNING_FLAGS})
target_link_options(sohook PUBLIC ${ARCH_LINK_FLAGS})

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
        ${TEST_MEMORY_DIR}/test_memory_cpp.cpp
        ${TEST_MEMORY_DIR}/test_memory_cpp_jni.c
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
    target_compile_options(test_memory PUBLIC ${DEFAULT_WARNING_FLAGS})

    
    # 设置输出目录
    set_target_properties(test_memory PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    )
    
    message(STATUS "✓ test_memory library configured")
else()
    message(STATUS "Test memory sources not found, skipping test library build")
endif()
