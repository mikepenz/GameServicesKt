cmake_minimum_required(VERSION 3.24)
project(gameservices_play_pc LANGUAGES CXX)
if(NOT WIN32 OR NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
  message(FATAL_ERROR "Play PC bridge requires Windows x64")
endif()
include(FetchContent)
FetchContent_Declare(play_pc
  URL https://dl.google.com/play/games/native/sdk/play_pc_sdk_26.3.312.0.zip
  URL_HASH SHA256=fcd458ce4b7936da959553835a5ec39cc165241a71056847e57d14aecbd4f391)
FetchContent_MakeAvailable(play_pc)
find_package(JNI REQUIRED)
add_library(gs_play_pc SHARED bridge.cpp jni.cpp)
target_compile_features(gs_play_pc PRIVATE cxx_std_17)
target_include_directories(gs_play_pc PRIVATE "${play_pc_SOURCE_DIR}/includes" ${JNI_INCLUDE_DIRS})
target_link_libraries(gs_play_pc PRIVATE "${play_pc_SOURCE_DIR}/imports/x64/play_pc_sdk.lib")
add_custom_command(TARGET gs_play_pc POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${play_pc_SOURCE_DIR}/imports/x64/play_pc_sdk.dll" "$<TARGET_FILE_DIR:gs_play_pc>"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${play_pc_SOURCE_DIR}/LICENSE" "$<TARGET_FILE_DIR:gs_play_pc>/PLAY_PC_LICENSE"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${play_pc_SOURCE_DIR}/THIRD_PARTY_NOTICES" "$<TARGET_FILE_DIR:gs_play_pc>/PLAY_PC_THIRD_PARTY_NOTICES")

enable_testing()
add_executable(bridge_test bridge.cpp bridge_test.cpp)
target_compile_features(bridge_test PRIVATE cxx_std_17)
target_include_directories(bridge_test PRIVATE "${play_pc_SOURCE_DIR}/includes")
add_test(NAME bridge_lifetime COMMAND bridge_test)
