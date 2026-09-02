cmake_minimum_required(VERSION 3.22.1)
project(vaprobes)

add_library(vaprobes SHARED native_probe.cpp)

# Explicitly keep our own JNI library compatible with Android 15+ 16 KiB-page
# devices even when the project is built with an older NDK.
target_link_options(vaprobes PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

target_link_libraries(vaprobes android log)
