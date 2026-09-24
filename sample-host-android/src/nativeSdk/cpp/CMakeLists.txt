cmake_minimum_required(VERSION 3.22.1)
project(gs_native_validation_jni LANGUAGES CXX)

if(NATIVE_VALIDATION)
    add_library(gs_native_validation_jni SHARED validation_jni.cpp)
    target_link_libraries(gs_native_validation_jni PRIVATE dl)
    target_compile_features(gs_native_validation_jni PRIVATE cxx_std_17)
endif()
