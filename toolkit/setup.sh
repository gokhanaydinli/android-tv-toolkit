#!/bin/bash
# ============================================================
#  Kutu Home + Debloat — tek komutluk kurulum
#
#  Gereken:
#    1) Bilgisayarda adb kurulu olmali
#         macOS : brew install --cask android-platform-tools
#         Linux : sudo apt install android-tools-adb
#         Windows: platform-tools indirip PATH'e ekle
#    2) Kutuda gelistirici secenekleri + USB hata ayiklama acik
#    3) Launcher derlenmis olmali:  cd ../launcher && ./build.sh home
#       veya hazir APK ver:  APK=/yol/kutu-home.apk ./setup.sh <IP>
#
#  Kullanim:  ./kutu-kurulum.sh <KUTU_IP>
#  Ornek:     ./kutu-kurulum.sh 192.168.1.50
#
#  Her sey geri alinabilir. Script calisirken geri_al.sh uretir.
# ============================================================

set -u
IP="${1:-}"
[ -z "$IP" ] && { echo "Kullanim: $0 <KUTU_IP>   (orn: $0 192.168.1.50)"; exit 1; }
D="$IP:5555"
DIR="$(cd "$(dirname "$0")" && pwd)"
APK="${APK:-$DIR/../launcher/build/kutu-home.apk}"
OUT="$HOME/kutu-kurulum"
mkdir -p "$OUT"
GERI="$OUT/geri_al.sh"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()  { printf '  \033[32m✓\033[0m %s\n' "$*"; }
uyar(){ printf '  \033[33m!\033[0m %s\n' "$*"; }
hata(){ printf '  \033[31m✗\033[0m %s\n' "$*"; }

# ---------- 1. Baglanti ----------
say "[1/7] Kutuya baglaniliyor"
adb disconnect >/dev/null 2>&1
adb connect "$D" >/dev/null 2>&1
sleep 3
if ! adb devices | grep -q "$D	device"; then
  hata "Baglanamadi."
  echo "     - Kutuda 'USB hata ayiklama' acik mi?"
  echo "     - TV ekraninda izin penceresi cikmis olabilir, onayla ve tekrar dene."
  echo "     - IP dogru mu? (Ayarlar > Ag > Durum)"
  exit 1
fi
ok "Baglandi: $(adb -s $D shell getprop ro.product.model | tr -d '\r')"

# ---------- 2. Cihaz kontrolu ----------
say "[2/7] Cihaz uyumlulugu"
ABI=$(adb -s $D shell getprop ro.product.cpu.abi | tr -d '\r')
SDK=$(adb -s $D shell getprop ro.build.version.sdk | tr -d '\r')
ok "ABI: $ABI, Android API: $SDK"
[ "$SDK" -lt 21 ] && { hata "Android surumu cok eski (API $SDK), minimum 21 gerekiyor."; exit 1; }

# ---------- 3. Yedek ----------
say "[3/7] Mevcut durumun fotografi aliniyor"
adb -s $D shell pm list packages | sed 's/package://' | sort | tr -d '\r' > "$OUT/paketler_ONCE.txt"
ok "$(wc -l < "$OUT/paketler_ONCE.txt" | tr -d ' ') paket kaydedildi -> $OUT/paketler_ONCE.txt"
printf '#!/bin/bash\nD="%s"\n' "$D" > "$GERI"
chmod +x "$GERI"

# ---------- 4. Debloat ----------
say "[4/7] Gereksiz paketler kaldiriliyor (APK silinmiyor, geri alinabilir)"
PAKETLER=(
  com.mft.googletvhomecustomization      # ureticinin promo satirlari
  com.mft.googletv.setupcustomization    # kurulum promosyonlari
  com.oem.qa                             # fabrika test araci
  com.sdt.producttest                    # fabrika test araci
  com.google.android.videos              # Google TV film magazasi (reklam kaynagi)
  com.google.android.play.games
  com.google.android.youtube.tvmusic     # YouTube Music
  com.android.camera2                    # kutuda kamera yok
  com.android.printspooler               # yazici yok
  com.android.bluetoothmidiservice       # MIDI
  com.google.android.marvin.talkback     # ekran okuyucu
  com.google.android.feedback
  com.google.android.onetimeinitializer
  com.android.dynsystem
  com.android.cts.ctsshim
  com.android.cts.priv.ctsshim
  com.google.android.apps.nbu.smartconnect.tv
  com.google.android.apps.tv.dreamx      # ekran koruyucu (~88 MB)
)
SILINEN=0
for p in "${PAKETLER[@]}"; do
  if adb -s $D shell "pm list packages" 2>/dev/null | grep -q "^package:$p\$"; then
    R=$(adb -s $D shell "pm uninstall -k --user 0 $p" 2>&1 | tr -d '\r')
    if [[ "$R" == *Success* ]]; then
      ok "$p"
      echo "adb -s \$D shell pm install-existing $p" >> "$GERI"
      SILINEN=$((SILINEN+1))
    else
      uyar "$p -> $R"
    fi
  fi
done
ok "$SILINEN paket kaldirildi"

# DIKKAT: asla dokunulmayanlar (kutuyu bozar)
#   android, systemui, tv.settings, gms, gsf, vending, providers.*,
#   webview, droidlogic*, networkstack*, packageinstaller,
#   permissioncontroller, inputmethod.latin (klavye),
#   tv.remote.service + mft.rcupair (kumanda),
#   tv.axel (KIZILOTESI KUMANDA), bluetooth, frontpanelledsservice

# ---------- 5. Ayarlar ----------
say "[5/7] Ayarlar"
adb -s $D shell settings put global private_dns_mode hostname
adb -s $D shell settings put global private_dns_specifier dns.adguard.com
ok "AdGuard DNS (uygulama ici reklamlar DNS'te dusuyor)"
echo 'adb -s $D shell settings put global private_dns_mode off' >> "$GERI"

for k in window_animation_scale transition_animation_scale; do
  echo "adb -s \$D shell settings put global $k 1.0" >> "$GERI"
  adb -s $D shell settings put global $k 0
done
adb -s $D shell settings put global animator_duration_scale 0.5
echo 'adb -s $D shell settings put global animator_duration_scale 1.0' >> "$GERI"
ok "Animasyonlar hizlandirildi (pencere/gecis 0, uygulama-ici 0.5)"

# ---------- 6. Kutu Home ----------
say "[6/7] Kutu Home kuruluyor"
if [ ! -f "$APK" ]; then
  uyar "kutu-home.apk bulunamadi ($APK) — launcher adimi atlandi."
  uyar "APK'yi bu klasore koyup scripti tekrar calistirabilirsin."
else
  if adb -s $D install -r "$APK" 2>&1 | grep -q Success; then
    ok "Kuruldu"
    echo 'adb -s $D uninstall local.kutu.home' >> "$GERI"
  else
    hata "Kurulum basarisiz"; exit 1
  fi

  # ---- KRITIK SIRA: once calistigini dogrula, sonra HOME yap ----
  adb -s $D shell am start -n local.kutu.home/.HomeActivity >/dev/null 2>&1
  sleep 6
  if adb -s $D shell dumpsys window 2>/dev/null | grep -q "local.kutu.home"; then
    ok "Acilis testi gecti"
  else
    hata "Uygulama acilmadi — HOME yapilmiyor, kutu guvende."
    echo "     Geri almak icin: $GERI"
    exit 1
  fi

  say "[7/7] Ana ekran degistiriliyor"
  adb -s $D shell cmd package set-home-activity \
      local.kutu.home/local.kutu.home.HomeActivity >/dev/null 2>&1
  sleep 2
  H=$(adb -s $D shell cmd package resolve-activity -c android.intent.category.HOME \
      -a android.intent.action.MAIN 2>/dev/null | grep -m1 packageName | tr -d ' \r')

  if [[ "$H" != *"local.kutu.home"* ]]; then
    uyar "Stok launcher hala one cikiyor, devre disi birakiliyor (SILINMIYOR)"
    adb -s $D shell pm disable-user --user 0 com.google.android.apps.tv.launcherx >/dev/null 2>&1
    echo 'adb -s $D shell pm enable com.google.android.apps.tv.launcherx' >> "$GERI"
    sleep 2
    adb -s $D shell cmd package set-home-activity \
        local.kutu.home/local.kutu.home.HomeActivity >/dev/null 2>&1
    sleep 2
    H=$(adb -s $D shell cmd package resolve-activity -c android.intent.category.HOME \
        -a android.intent.action.MAIN 2>/dev/null | grep -m1 packageName | tr -d ' \r')
  fi

  if [[ "$H" == *"local.kutu.home"* ]]; then
    adb -s $D shell input keyevent KEYCODE_HOME
    ok "Kutu Home artik ana ekran"
  else
    hata "HOME yapilamadi — geri aliniyor"
    bash "$GERI"
    exit 1
  fi
fi

# ---------- Ozet ----------
adb -s $D shell pm list packages | sed 's/package://' | sort | tr -d '\r' > "$OUT/paketler_SONRA.txt"
say "BITTI"
echo "  Paket:  $(wc -l < "$OUT/paketler_ONCE.txt" | tr -d ' ')  ->  $(wc -l < "$OUT/paketler_SONRA.txt" | tr -d ' ')"
adb -s $D shell cat /proc/meminfo | grep MemAvailable | tr -d '\r' | sed 's/^/  Bos RAM: /'
echo
echo "  Geri almak icin:  $GERI"
echo "  Kayitlar:         $OUT"
echo
echo "  SON ADIM: her sey yolundaysa kutuda 'USB hata ayiklama'yi kapat."
