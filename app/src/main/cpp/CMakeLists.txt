cmake_minimum_required(VERSION 3.22.1)
project(mineways_core C CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 桌面源码把 and/or/not 等当作普通标识符（MSVC 下非关键字）；clang 需关闭替代运算符关键字
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-operator-names")

# 屏蔽大量历史告警，聚焦功能性编译通过
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -w")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -w")

# Android 15+ 16KB page-size 设备要求 native 库按 16KB 对齐（LOAD 段 p_align=0x4000）。
# Google 的要求覆盖 arm64-v8a 与 x86_64；armeabi-v7a 仍按 4KB 常规对齐（32 位设备无 16KB 页要求）。
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
endif()

add_library(mineways_core SHARED
    mineways_jni.cpp
    jni_export.cpp
    export_diag.cpp
    android_port.cpp
    core/region.cpp
    core/nbt.cpp
    core/blockInfo.cpp
    core/biomes.cpp
    core/cache.cpp
    core/lodepng.cpp
    core/rwpng.cpp
    core/terrainExtData.cpp
    core/culling.cpp
    core/ObjFileManip.cpp
    core/mw_loadblock.cpp
    core/export_setup.cpp
)

target_include_directories(mineways_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/core
)

# NDK 自带 zlib
find_library(ZLIB_LIB z)
target_link_libraries(mineways_core PRIVATE ${ZLIB_LIB} log)