#!/bin/bash

set -e
cd $(dirname $0)

echo "Using ndk at $NDK"

COMMAND=${1:-build}

case $COMMAND in
  build)
    # configure the library to build
    ./libvpx/configure --target=armv7-android-gcc \
      --disable-examples --disable-docs --disable-webm-io \
      --disable-vp9 --disable-vp8-encoder --enable-static \
      --disable-libyuv --sdk-path=$NDK

    # and build it
    export NDK_PROJECT_PATH=$PWD/..
    export APP_BUILD_SCRIPT=$PWD/Android.mk
    $NDK/ndk-build
    ;;

  clean)
    make clean -s
    rm -rf vpx_config.c vpx_config.h *-armv7-*mk *config.mk armeabi-v7a Makefile
    rm -rf ../obj
    ;;

esac
