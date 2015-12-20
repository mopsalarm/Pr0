#!/bin/bash

set -e
cd $(dirname $0)

function default_makefile() {
  local ARCH=$1
  cat << EOF
APP_ABI := $ARCH
TARGET_PLATFORM := android-14
APP_PLATFORM := android-14
APP_CFLAGS := -funwind-tables -Wl,--no-merge-exidx-entries
EOF
}

function clean() {
  rm -rf build_*
}

function build_x86() {
  # configure the library to build
  PATH=$NDK/toolchains/x86-4.9/prebuilt/linux-x86_64/bin:$PATH \
  CROSS=i686-linux-android- \
  LDFLAGS="--sysroot=$NDK/platforms/android-14/arch-x86" \
  libvpx/configure \
    --extra-cflags="--sysroot=$NDK/platforms/android-14/arch-x86" \
    --target=x86-android-gcc \
    --disable-examples --disable-docs --disable-webm-io \
    --disable-spatial-resampling --disable-postproc \
    --disable-vp9 --disable-vp10 --disable-vp8-encoder --enable-static \
    --disable-runtime-cpu-detect --disable-neon --disable-neon-asm \
    --disable-avx --disable-avx2 --enable-pic \
    --disable-libyuv --sdk-path=$NDK

  default_makefile "x86" > Application.mk

  $NDK/ndk-build
}

function build_x86_64() {
  # configure the library to build
  libvpx/configure \
    --target=x86_64-android-gcc \
    --disable-examples --disable-docs --disable-webm-io \
    --disable-spatial-resampling --disable-postproc \
    --disable-vp9 --disable-vp10 --disable-vp8-encoder --enable-static \
    --disable-runtime-cpu-detect --disable-neon --disable-neon-asm \
    --disable-avx --disable-avx2 --enable-pic \
    --disable-libyuv --sdk-path=$NDK

  default_makefile "x86_64" > Application.mk

  $NDK/ndk-build
}

function build_arm() {
  # configure the library to build
  libvpx/configure \
    --target=armv7-android-gcc \
    --disable-examples --disable-docs --disable-webm-io \
    --disable-spatial-resampling --disable-postproc \
    --disable-vp9 --disable-vp10 --disable-vp8-encoder --enable-static \
    --disable-libyuv --sdk-path=$NDK

  default_makefile "armeabi-v7a" > Application.mk

  LOCAL_ARM_NEON=true $NDK/ndk-build
}

function build_arm64() {
  # monkey-patch the libvpx configure script to add
  # support for arm64
  sed -i -e s/arm64-darwin-gcc/armv8-android-gcc/g libvpx/configure

  # configure the library to build
  PATH=$NDK/toolchains/x86-4.9/prebuilt/linux-x86_64/bin:$PATH \
  CROSS=i686-linux-android- \
  LDFLAGS="--sysroot=$NDK/platforms/android-21/arch-arm64" \
  libvpx/configure \
    --extra-cflags="--sysroot=$NDK/platforms/android-21/arch-arm64" \
    --target=armv8-android-gcc \
    --disable-examples --disable-docs --disable-webm-io \
    --disable-spatial-resampling --disable-postproc \
    --disable-vp9 --disable-vp10 --disable-vp8-encoder --enable-static \
    --disable-libyuv --sdk-path=$NDK

  default_makefile "arm64-v8a" > Application.mk

  LOCAL_ARM_NEON=true $NDK/ndk-build
}

echo "Using ndk at $NDK"

for COMMAND in $* ; do
  case $COMMAND in
    build_all)
    $0 build_arm
    $0 build_arm64
    $0 build_x86
    $0 build_x86_64
    ;;

    build_*)
      # copy the sources to the build directory
      BUILD_DIR=$COMMAND/jni
      rm -rf $BUILD_DIR
      mkdir -p $BUILD_DIR
      cp -raf $PWD/libvpx $PWD/coffeecatch $PWD/vpx-wrapper Android.mk $BUILD_DIR

      # go to the build directory and start the build
      pushd $BUILD_DIR
      $COMMAND

      # install libraries to the correct target direcotry
      popd
      cp -rav $BUILD_DIR/../libs ../
      ;;

    clean)
      clean
      ;;

  esac
done
