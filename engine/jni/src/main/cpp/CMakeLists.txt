cmake_minimum_required(VERSION 3.22.1)
project(bambuprinterlan_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Phase 1 bring-up: a minimal native library that proves the NDK toolchain and
# JNI bridge work end-to-end. libslic3r + bundled deps are added on top of this
# (this CMakeLists will consume ../../../../../src/libslic3r once vendored).
add_library(bambuprinterlan_engine SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(bambuprinterlan_engine ${log-lib})
