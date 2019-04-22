#!/bin/sh
set -e

export ANDROID_NDK=$PWD/../android-ndk-r18b
export ANDROID_SDK=$PWD/../sdk
export PATH=$PATH:$ANDROID_SDK/tools:$ANDROID_SDK/tools/bin

#sdkmanager --licenses



./compile.sh -l --release -a all
