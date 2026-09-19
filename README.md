# android-tv-toolkit

A **36 KB** Android TV launcher, plus an ADB toolkit for debloating Google TV boxes.

No network permission. No background services. No telemetry. No GMS. No Gradle.

---

## Why

Cheap Android TV boxes ship with 2 GB of RAM and a home screen that spends
130 MB of it advertising films you did not buy.

The alternatives are not much lighter â€” Projectivy measures **110 MB** resident
on the same hardware. This launcher measures **25 MB** and its APK is **36 KB**.

It does that by being deliberately poor: plain Java and platform Views, no
AndroidX, no Compose, no dependency graph. It asks for no permission that lets
it talk to the internet, so it cannot phone home even if it wanted to.

| | Stock Google TV | Projectivy 4.71 | Kutu Home |
|---|---|---|---|
| Resident memory | 131 MB | 110 MB | **25 MB** |
| APK size | â€” | ~15 MB | **36 KB** |
| Idle CPU | â€” | â€” | **0.0%** |
| Network permission | yes | yes | **none** |

Measured on an Amlogic Cortex-A35 box, Android 11, `armeabi-v7a`.

---

## What is in here

```
launcher/    Kutu Home â€” the launcher. Java source + Gradle-free build script.
toolkit/     setup.sh â€” one-command debloat + install over ADB.
docs/        The method written out, including the mistakes worth avoiding.
```

---

## Launcher

### Build

Needs JDK 17+ and an Android SDK (platform 30+, build-tools 34+).
No Gradle, no Android Studio.

```bash
cd launcher
./build.sh home          # HOME category â€” becomes a launcher
./build.sh               # plain app â€” for testing before you commit
adb install -r build/kutu-home.apk
```

The SDK is found automatically. Override with `ANDROID_HOME=/path ./build.sh home`.

A development keystore is generated on first build. **For anything you
distribute, use your own key** and keep it out of the repository:

```bash
KEYSTORE=~/my-release.keystore KEYSTORE_PASS=... ./build.sh home
```

### Make it a launcher

Do this in order. Skipping step 1 is how you end up at a recovery screen with
no way back in.

```bash
# 1. install WITHOUT the HOME category and actually use it with the remote
./build.sh && adb install -r build/kutu-home.apk

# 2. only once it works, install the launcher build
./build.sh home && adb install -r build/kutu-home.apk

# 3. claim HOME
adb shell cmd package set-home-activity local.kutu.home/local.kutu.home.HomeActivity

# 4. verify â€” do not skip
adb shell cmd package resolve-activity -c android.intent.category.HOME \
    -a android.intent.action.MAIN

# 5. if the stock launcher still wins, DISABLE it (never uninstall)
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

On some Google TV builds `com.google.android.tungsten.setupwraith` contains a
`RecoveryActivity` that outranks third-party launchers once the stock one is
disabled. The shell cannot disable that component (`SecurityException`); it has
to be removed for user 0:

```bash
adb shell pm uninstall -k --user 0 com.google.android.tungsten.setupwraith
```

Everything above is reversible: `pm enable`, `pm install-existing`.

### Customise

| What | Where |
|---|---|
| Name shown at the top | `res/values/strings.xml` â†’ `brand_owner` (empty hides the line) |
| Default dock order | `DockStore.java` â†’ `SEED` |
| Grid width | `HomeActivity.java` â†’ `COLS` |
| Colours, sizes, overscan margins | `res/values/colors.xml`, `dimens.xml` |
| Screen-mirroring chip target | `HomeActivity.java` â†’ `MIRROR_PKG` |

The user's own dock layout is stored on device and is **never** overwritten by
`SEED` after the first run. Bump `KEY_SEEDED` if you want to reseed.

### What it does

Reads launchable apps (Leanback entries first, normal launcher entries as a
fallback), renders icons as consistent rounded cards â€” composing adaptive icons
itself rather than accepting the firmware mask, detecting transparent logos and
placing them on a dark plate, falling back to a monogram â€” and caches the result
to disk keyed by package and version. Long-press opens move / remove / app info
/ uninstall. Move mode reorders with left and right. Three chips at the bottom:
screen mirroring, settings, all apps.

The clock updates from `ACTION_TIME_TICK`, so there is no timer running.

---

## Toolkit

```bash
cd toolkit
./setup.sh 192.168.1.50
```

Connects over ADB, checks the device is compatible, snapshots the package list,
removes bloat, applies settings, installs the launcher, **verifies it opens
before making it HOME**, and writes a rollback script for every single change.

Removal uses `pm uninstall -k --user 0` â€” the APK stays on disk and
`pm install-existing` brings it back. Nothing touches `/system`. No root.

### Never remove these

```
com.google.android.tv.axel        the INFRARED REMOTE service (.remote.IrService)
                                  the name gives nothing away; remove it and the
                                  remote stops responding
com.google.android.tv.remote.service, *rcupair     remote pairing
com.android.bluetooth                              remote
com.google.android.inputmethod.latin               keyboard
*frontpanelledsservice                             front panel LED
com.droidlogic*                                    hardware layer
gms, gsf, vending, providers.*, webview, networkstack*
packageinstaller, permissioncontroller, systemui, tv.settings, android
```

If you do not know what a package does, leave it. `dumpsys activity services
<pkg>` names its services, which is usually more honest than the package name.

**Do not copy another manufacturer's removal list.** Package names differ.

---

## Method

[`docs/method.md`](docs/method.md) is the full procedure: what to inventory
before touching anything, what must never be removed and why, the exact order
for replacing a launcher, and the traps that cost a working box the first time
round.

---

## Honest limits

- Removing packages does not make a slow box fast. If CPU sits at 30% while the
  UI stutters and three cores are idle, the bottleneck is single-core speed â€”
  a Cortex-A35 ceiling â€” and no amount of debloating moves it.
- TV overscan cannot be fixed from the box on Android 11. `wm overscan` was
  removed and the vendor sysfs node needs root. This launcher simply keeps its
  content inside a safe margin.
- DRM-protected video cannot be screen-mirrored from a phone. Cast it instead;
  the box then fetches the stream itself.

---

## License

MIT. See [LICENSE](LICENSE).

Do what you like with it. If it bricks your box, that is between you and your box â€”
though every step here is reversible, and the toolkit writes the undo commands
before it changes anything.
