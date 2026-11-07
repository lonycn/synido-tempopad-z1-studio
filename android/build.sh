#!/bin/bash
# Offline SDK build: no Gradle/Maven downloads, no device actions.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
TOOLS="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
rm -rf build/classes build/tests build/controller-tests build/dex build/generated
mkdir -p build/{generated/java,classes,tests,controller-tests,dex} local ../dist/android ../tests/reports
python3 verify_instruments.py
python3 prepare_assets.py
# tests/*.java includes the sibling DiamondTest without altering its assertions.
javac -encoding UTF-8 -d build/tests src/local/orange/z1androidtest/{PadProtocol,RgbFrame,Diffusion,LightingState,LifecycleGate,SendAttemptLimiter,MidiSessionCoordinator,Instruments,UiPublisher}.java tests/*.java
java -cp build/tests local.orange.z1androidtest.InstrumentsTest | tee ../tests/reports/android-instruments-test.txt
java -cp build/tests local.orange.z1androidtest.UiPublisherTest | tee ../tests/reports/android-ui-publisher-test.txt
java -cp build/tests local.orange.z1androidtest.ModeFeedbackTest | tee ../tests/reports/android-mode-feedback-test.txt
./native/build.sh
java -cp build/tests local.orange.z1androidtest.ProtocolTest | tee ../tests/reports/android-protocol-test.txt
java -cp build/tests local.orange.z1androidtest.ProtocolFuzzTest | tee ../tests/reports/android-protocol-fuzz-test.txt
java -cp build/tests local.orange.z1androidtest.LightingTest | tee ../tests/reports/android-lighting-test.txt
java -cp build/tests local.orange.z1androidtest.DiamondTest | tee ../tests/reports/android-diamond-test.txt
java -cp build/tests local.orange.z1androidtest.LifecycleTest | tee ../tests/reports/android-lifecycle-test.txt
java -cp build/tests local.orange.z1androidtest.SendAttemptLimiterTest | tee ../tests/reports/android-send-limiter-test.txt
find tests/controller-stubs tests/controller -name '*.java' -print > build/controller-test-sources.txt
javac -encoding UTF-8 -cp build/tests -d build/controller-tests @build/controller-test-sources.txt src/local/orange/z1androidtest/MidiLighting.java
java -cp build/tests:build/controller-tests local.orange.z1androidtest.MidiLightingTest | tee ../tests/reports/android-midi-lighting-test.txt
java -cp build/tests:build/controller-tests local.orange.z1androidtest.InstrumentsLightingTest | tee ../tests/reports/android-instruments-lighting-test.txt
"$TOOLS/aapt" package -f -m -M AndroidManifest.xml -S build/generated/res -I "$JAR" -J build/generated/java -F build/base.apk -A assets -0 wav -0 sf2
find src build/generated/java -name '*.java' -print > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$TOOLS/core-lambda-stubs.jar:$JAR" -d build/classes @build/sources.txt
find build/classes -name '*.class' -print > build/classes.txt
"$TOOLS/d8" --lib "$JAR" --min-api 26 --output build/dex @build/classes.txt
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes*.dex)
(cd build/apk && zip -q ../unsigned.apk lib/arm64-v8a/libz1instrument.so)
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f local/test.keystore ]]; then
 keytool -genkeypair -keystore local/test.keystore -storepass android -keypass android -alias z1-test -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Z1 Studio Local Test'
fi
APK=../dist/android/Z1-Studio-Android-0.3.1-test.apk
"$TOOLS/apksigner" sign --ks local/test.keystore --ks-key-alias z1-test --ks-pass pass:android --key-pass pass:android --out "$APK" build/aligned.apk
"$TOOLS/apksigner" verify --verbose "$APK" > ../tests/reports/android-apk-signature.txt
"$TOOLS/aapt" dump badging "$APK" > ../tests/reports/android-apk-badging.txt
(cd ../dist/android && shasum -a 256 Z1-Studio-Android-0.3.1-test.apk > Z1-Studio-Android-0.3.1-test.apk.sha256)
python3 verify_instruments.py "$APK" | tee ../tests/reports/android-instruments-apk-audit.txt
"$TOOLS/aapt" dump permissions "$APK" > ../tests/reports/android-apk-permissions.txt
if grep -q uses-permission ../tests/reports/android-apk-permissions.txt; then echo 'Unexpected permission'; exit 1; fi
echo "Built $APK (local test signing, not store release)"
