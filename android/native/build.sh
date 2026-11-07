#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/native build/apk/lib/arm64-v8a
HOST_CXX="${CXX:-clang++}"
"$HOST_CXX" -std=c++17 -O2 -DSYNTH_TEST_ALLOC -pthread native/Synth.cpp native/SynthTest.cpp -o build/native/synth-test
/usr/bin/time -l build/native/synth-test assets/FluidR3_GM.sf2 2> build/native/memory.txt | tee ../tests/reports/android-native-test.txt
/usr/bin/time -l build/native/synth-test assets/FluidR3_GM.sf2 --measure > build/native/single-engine-memory.txt 2>&1
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK="$SDK/ndk/23.1.7779620"
CXX="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang++"
"$CXX" -std=c++17 -O2 -fPIC -shared -static-libstdc++ -Wl,-z,max-page-size=16384 native/Synth.cpp native/AndroidAudio.cpp -laaudio -o build/apk/lib/arm64-v8a/libz1instrument.so
