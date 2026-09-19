#!/bin/bash
# ============================================================
#  Kutu Home — Gradle'siz derleme
#  aapt2 -> javac -> d8 -> zipalign -> apksigner
#
#  Kullanim:
#    ./build.sh          normal uygulama olarak derle (test icin)
#    ./build.sh home     HOME kategorili derle (launcher olur)
#
#  Gereken: JDK 17+, Android SDK (platform-30, build-tools 34.0.0)
#  SDK yolu otomatik bulunur; bulunamazsa ANDROID_HOME ver:
#    ANDROID_HOME=/yol/sdk ./build.sh home
# ============================================================
set -e
cd "$(dirname "$0")"

# ---- SDK bul ----
find_sdk() {
  for c in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" \
           "/opt/homebrew/share/android-commandlinetools" \
           "/usr/local/share/android-commandlinetools" \
           "$HOME/Library/Android/sdk" \
           "$HOME/Android/Sdk" \
           "$LOCALAPPDATA/Android/Sdk"; do
    [ -n "$c" ] && [ -d "$c/platforms" ] && { echo "$c"; return; }
  done
}
SDK="$(find_sdk)"
[ -z "$SDK" ] && { echo "HATA: Android SDK bulunamadi. ANDROID_HOME ayarla."; exit 1; }

PLATFORM=$(ls -1 "$SDK/platforms" | grep -E 'android-(3[0-9]|[4-9][0-9])' | sort -V | head -1)
[ -z "$PLATFORM" ] && { echo "HATA: android-30+ platform yok. sdkmanager ile kur."; exit 1; }
BT_VER=$(ls -1 "$SDK/build-tools" | sort -V | tail -1)
BT="$SDK/build-tools/$BT_VER"
AJAR="$SDK/platforms/$PLATFORM/android.jar"
echo "[*] SDK: $SDK  ($PLATFORM, build-tools $BT_VER)"

OUT="./build"
KS="${KEYSTORE:-./debug.keystore}"
KS_PASS="${KEYSTORE_PASS:-android}"

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

# ---- imzalama anahtari ----
# Yayina cikaracaksan KENDI anahtarini uret ve KEYSTORE/KEYSTORE_PASS ver.
# Anahtar dosyasini ASLA repoya ekleme (.gitignore'da).
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias key -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Kutu Home, OU=Dev, O=Kutu, C=TR" >/dev/null 2>&1
  echo "[*] Gelistirme anahtari uretildi: $KS"
fi

# ---- HOME kategorisi ----
if [ "${1:-}" = "home" ]; then
  sed 's|@@HOME@@|<category android:name="android.intent.category.HOME" />|' \
    AndroidManifest.xml.in > AndroidManifest.xml
  echo "[*] mod: LAUNCHER"
else
  sed 's|@@HOME@@||' AndroidManifest.xml.in > AndroidManifest.xml
  echo "[*] mod: normal uygulama"
fi

echo "[1/6] kaynaklar derleniyor"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

echo "[2/6] kaynaklar birlestiriliyor"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJAR" \
  --manifest AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version 21 --target-sdk-version 30 --auto-add-overlay \
  "$OUT/res.zip"

echo "[3/6] java derleniyor"
find src "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 \
  -bootclasspath "$AJAR" -classpath "$AJAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] dex uretiliyor"
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
"$BT/d8" --lib "$AJAR" --min-api 21 --output "$OUT/dex" @"$OUT/classes.txt"

echo "[5/6] apk paketleniyor"
(cd "$OUT/dex" && zip -q -u "../base.apk" classes.dex)

echo "[6/6] hizalama + imzalama"
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT/kutu-home.apk" "$OUT/aligned.apk"

echo "[+] HAZIR: $OUT/kutu-home.apk  ($(du -h "$OUT/kutu-home.apk" | cut -f1))"
echo "    Kurmak icin: adb install -r $OUT/kutu-home.apk"
