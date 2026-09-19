# Debloating an Android TV box and replacing its launcher

A method validated on a real device. Keep the order — the failures listed here
actually happened and left the box unusable until recovered.

## Order

1. **Read first, change nothing.** Inventory the device before touching it:
   `getprop` (model, ABI, API level, security patch), `pm list packages`,
   `pm list packages -d`, `dumpsys meminfo`, `/proc/meminfo`, `df -h`,
   open ports, running services, current launcher.
   **Always check the ABI** — cheap boxes are frequently 32-bit
   (`armeabi-v7a`) and will reject the wrong APK.

2. **Build the rollback before the first change.** Snapshot the package list.
   Write the undo command for every operation into a script as you go.

3. **Ask before removing.** Do not guess which apps the user wants. Voice
   assistant and the Cast receiver cost real memory but removing them costs a
   feature — that is the user's call, not yours.

4. Debloat, then settings, then app installs, and **the launcher last**.

5. Reboot test after each phase: does it boot normally, does the remote work,
   does a chooser appear, did anything crash.

6. Remind the user to turn ADB off when the work is done.

## Never touch

```
com.google.android.tv.axel          INFRARED REMOTE (.remote.IrService)
                                    Unrecognisable from the name. Removing it
                                    kills the remote.
com.google.android.tv.remote.service, *rcupair     remote pairing
com.android.bluetooth                              remote
com.google.android.inputmethod.latin               keyboard
*frontpanelledsservice                             front panel LED
com.droidlogic*, *.vendor.*                        hardware layer
gms, gsf, vending, providers.*, webview, networkstack*
packageinstaller, permissioncontroller, systemui, tv.settings, android
```

If you cannot say what a package does, leave it. To find out:
`dumpsys package <pkg>` and `dumpsys activity services <pkg>` — service names
are far more informative than package names.

## Removal method

`pm uninstall -k --user 0 <pkg>` — the APK stays on disk and
`pm install-existing <pkg>` restores it. `pm disable-user --user 0` +
`pm enable` is the more conservative equivalent. Both are reversible.

**Never modify `/system`, never root, never flash.**

Never copy a package list from a different manufacturer — names differ.

## Launcher replacement — the dangerous step

**This order matters. Skipping step 1 drops the box into a recovery screen.**

1. Install the new launcher **without** the HOME category, as a normal app.
2. Open it. Have the user test it with the physical remote.
3. Only then install the build that declares HOME.
4. `cmd package set-home-activity <pkg>/<activity>`
5. **Verify:** `cmd package resolve-activity -c android.intent.category.HOME
   -a android.intent.action.MAIN`
6. If the stock launcher still wins, `pm disable-user` it — **never uninstall it**.
7. Verify again. If it still fails, **roll back immediately**.
8. Reboot and confirm it reaches HOME on its own.

**Trap:** once the stock launcher is disabled,
`com.google.android.tungsten.setupwraith` → `.RecoveryActivity` outranks
third-party launchers. The shell cannot disable that component
(`SecurityException`); it must be removed with
`pm uninstall -k --user 0 com.google.android.tungsten.setupwraith`.

## ADB notes

- Network ADB closed? Toggle USB debugging off and on — on most boxes 5555 opens.
- Lost the device? The MAC or IP may have changed:
  `nmap -p 5555,8008,8009 --open <subnet>`
- **Third-party app components cannot be disabled from the shell.** Drive the UI
  instead: `input tap <x> <y>`, and `exec-out screencap -p` to read coordinates
  off an actual screenshot.
- D-pad navigation gets trapped inside text fields. Tapping coordinates is more
  reliable than sending arrow keys.
- Prefer the Play Store for installs. If an APK is required, use F-Droid or the
  project's own GitHub/GitLab release — never a mirror or MOD site.

## Performance — be honest

Measure before promising. Sample `top` while the user navigates. If CPU usage
sits below ~30% with most cores idle while the UI stutters, the bottleneck is
**single-core speed** (efficiency cores such as Cortex-A35), not memory.
More debloating will not fix it. Say so plainly.

What actually helps:

- `window_animation_scale` and `transition_animation_scale` → `0`
  (leave `animator_duration_scale` at `0.5`; `0` breaks some apps' animation
  callbacks)
- Ad filtering via Private DNS: `private_dns_mode hostname` +
  `private_dns_specifier dns.adguard.com`
- The ambient screensaver (`*.dreamx`) holds ~90 MB permanently — remove it if
  it is not wanted

## Closing out

- Save a baseline of packages and settings, and leave a comparison script — an
  OTA update can resurrect everything that was removed.
- Consider disabling the OTA updater (`*dfuservice`), but check the security
  patch date first: if the vendor abandoned the device years ago there is no
  cost, and if it is still supported there is.
- If you built an APK, **back up the signing key**. Losing it makes future
  updates impossible without uninstalling.
- Turn ADB off.
