# NR Signal Thresholds

A 5G NR signal-threshold tuner for **AOSP / LineageOS-style ROMs**: one LSPosed module, one screen,
one CarrierConfig key.

```text
CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY
  = "5g_nr_ssrsrp_thresholds_int_array"
  = { threshold1, threshold2, threshold3, threshold4 }
```

## Why

AOSP-derived ROMs keep AOSP's default NR level boundaries, which read low in some regions: the same
RSRP shows four bars on a mainstream vendor ROM and two or three on a clean AOSP build. This module
moves the four boundaries closer to the vendor UIs people are used to.

**It changes how the signal is displayed, not the signal itself.** RSRP measurements, network
capabilities, throughput and power consumption are untouched. More optimistic thresholds also mean
weak coverage starts looking full — that is the trade-off, not an improvement.

## Credits

- Forked from **[NKU100/carrier-ims](https://github.com/NKU100/carrier-ims)** — that module enabled
  VoLTE / VoWiFi / VT / VoNR on Pixels. This repo keeps its read-side hook and its on-device findings.
- Threshold values informed by **[vvb2060/Ims](https://github.com/vvb2060/Ims)**
- A related CarrierConfig-override approach: **[kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)**
- Framework and runtime: **[libxposed/api](https://github.com/libxposed/api)**,
  **[libxposed/service](https://github.com/libxposed/service)**, **[LSPosed](https://github.com/LSPosed/LSPosed)**

The original measurements of `CarrierConfigLoader` (real signatures, delegation, priority) still hold
and are kept in [the design doc](docs/superpowers/specs/2026-09-12-carrierims-xposed-design.md).
VoLTE / VoWiFi / VT / VoNR support has been removed; only the NR thresholds remain.

## Features

- Four SS-RSRP thresholds (-140 to -44 dBm) with strict validation
- Live preview of the five AOSP levels, drawn to scale
- Vendor presets: Samsung / OPPO / vivo / HUAWEI, **OPPO is the default**
- Simplified Chinese and English UI (follows the system language)
- Optional hidden launcher icon, reversible in-app
- Configuration stored in LSPosed remote preferences, restored after reboot, no Shizuku

## Fits / does not fit

| | |
| --- | --- |
| Fits | AOSP, LineageOS, Pixel Experience and similar clean ROMs |
| Does not fit | OEM ROMs with heavily modified telephony (MIUI/HyperOS, ColorOS, OriginOS, EMUI/HarmonyOS). The target class may not exist; the module logs an error and gives up instead of crashing |
| System | Android 8.0+ (`minSdk 26`) |
| Framework | LSPosed **2.2.0+** (libxposed API 102) |

## Why not `overrideConfig()`

`CarrierConfigManager.overrideConfig()` requires `MODIFY_PHONE_STATE`. Normal apps can only borrow it
through Shizuku, and since Android 16 QPR2 `persistent=true` is no longer a reliable way to survive a
reboot.

This module intercepts the **read** side: it hooks
`CarrierConfigLoader.getConfigForSubIdWithFeature()` in `com.android.phone` and replaces exactly one
key in the returned `PersistableBundle`. Readers are already past the permission check, so there is
no Shizuku, no root, no system partition change, and no override state that can go stale —
`dumpsys carrier_config` still shows the carrier's values.

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk

./gradlew :core:test :app:testDebugUnitTest   # plain JVM, no device
```

- JDK 21, Gradle 9.7.1 (wrapper), AGP 9.4.0
- `compileSdk = 37` / `compileSdkMinor = 2`, `buildToolsVersion = 37.0.0`
- Without `keystore.properties`, release builds are **debug-signed** (installable, but not for publishing)

## Install

1. Install the APK.
2. Enable **NR Signal Thresholds** in LSPosed. The scope is already `com.android.phone`.
3. Restart the phone process or reboot:

   ```bash
   adb shell su -c 'kill $(pidof com.android.phone)'
   ```

## Usage

The UI follows the system language. One screen:

| Area | Content |
| --- | --- |
| Header | Name, subtitle, **real hook status** (from the framework, not a guess) |
| Thresholds | Preset row + four dBm inputs |
| Preview | Proportionally drawn five-band level strip and ranges |
| App | Launcher-icon switch |
| Footer | **Apply** / **Save** / **Restore defaults** |

- **Apply** validates, writes to the framework's remote preferences, then re-checks hook status.
- **Save** performs the same validation and write without the extra check.
- **Restore defaults** fills the OPPO preset; nothing is written until you apply.

| Status | Meaning |
| --- | --- |
| `Hook active` | `com.android.phone` runs the installed module code |
| `Hook inactive` | not loaded there: not enabled, wrong scope, or process not restarted |
| `Restart required` | the process runs older module code |
| `Framework unavailable` | cannot reach the LSPosed service |
| `Status unknown` | framework API below 102 |

## Presets

The preset row only **fills the fields**; nothing is written until you tap Apply. Vendor tables are
usually published T1→T4 descending, while Android requires strictly increasing values, so the stored
numbers are the ascending version:

| Preset | Threshold 1 | Threshold 2 | Threshold 3 | Threshold 4 |
| --- | ---: | ---: | ---: | ---: |
| Samsung | -115 | -105 | -95 | -85 |
| **OPPO (default)** | **-120** | **-113** | **-105** | **-97** |
| vivo | -115 | -110 | -105 | -97 |
| HUAWEI | -120 | -113 | -103 | -90 |

Typing a custom combination clears the selection.

## Level boundaries (calibrated against AOSP)

| Level | Constant | SS-RSRP range |
| --- | --- | --- |
| 0 | `SIGNAL_STRENGTH_NONE_OR_UNKNOWN` | `< threshold1` |
| 1 | `SIGNAL_STRENGTH_POOR` | `[threshold1, threshold2)` |
| 2 | `SIGNAL_STRENGTH_MODERATE` | `[threshold2, threshold3)` |
| 3 | `SIGNAL_STRENGTH_GOOD` | `[threshold3, threshold4)` |
| 4 | `SIGNAL_STRENGTH_GREAT` | `>= threshold4` (cap -44) |

Below `threshold1` is level 0, **not** POOR. The top level is `EXCELLENT` in the CarrierConfig docs
and `GREAT` in `SignalStrength` — the same level. The Chinese UI shows 无信号 / 弱 / 中 / 良 / 优 and
names the constants in the preview hint.

## Verifying

`dumpsys carrier_config` shows the loader's internal bundle; this module never writes state, so the
injection is invisible there. Verify from the reading side:

```bash
ANDROID_HOME=$SDK bash tools/probe/run-probe.sh                       # current values
ANDROID_HOME=$SDK bash tools/probe/run-probe.sh --expect=-120,-113,-105,-97
```

To prove no other key changed: dump with the module disabled (`--dump > before.txt`), enable it,
dump again (`--dump > after.txt`), then `diff`. Only `5g_nr_ssrsrp_thresholds_int_array` may differ.

Logs: `adb logcat -s NrThresholds`. Do not infer "module not loaded" from an empty log — logd's main
buffer is 256 KiB and boot traffic can flush it within a minute.

## Storage

An LSPosed-managed remote preference group (`nr_ssrsrp_thresholds`) shared by the GUI and the hook:

- no `MODE_WORLD_READABLE`, no app-private files readable by another uid;
- survives reboots and is read automatically on hook start (no Shizuku at boot);
- every field is written in one `commit()`, so the hook sees the old record or the new one, never a
  partial write.

Version 1 record: `schema_version`, `threshold_1..4`, `checksum` (FNV-1a over `1|t1|t2|t3|t4`), plus a
display-only `applied_at`. The hook re-validates version, types, checksum, range and ordering, and
injects nothing when any check fails.

## Hiding the app icon

The switch in the *App* card disables only the launcher entry (an `activity-alias`). The module and
hook keep working. Two ways back to the screen:

- LSPosed → Modules → **NR Signal Thresholds**
- `adb shell am start -n io.github.nku100.carrierims/.ui.MainActivity`

## Compatibility

| System | Status |
| --- | --- |
| Android 16 QPR2 | Builds; **not verified on a device** |
| Android 17 / API 37 | Builds; **not verified on a device** (the same hook was measured on a Pixel 6 Pro / Android 17 by the original project) |
| Other versions / ROMs | Unverified; method discovery is by name + return type and fails closed |

## Not widely tested

This project has **not been tested across many devices and ROMs**, and has no on-device end-to-end
verification yet. What exists: debug and release builds (including R8), 68 JVM unit tests, lint, and
APK content checks.

Issues are welcome. Please include:

- device, ROM name and version, Android version, LSPosed version
- `adb logcat -s NrThresholds` output
- full `ANDROID_HOME=$SDK bash tools/probe/run-probe.sh` output
- the status shown at the top of the UI

## Known limits

- Only these four thresholds are touched; every other carrier config key is left alone
- Depends on AOSP's `com.android.phone.CarrierConfigLoader`, which OEM ROMs may not have
- If Google renames the read method, add the new name to `HookTargets`
- `staticScope=true`: users cannot change the scope
- Release builds are debug-signed by default; configure `keystore.properties` before distributing

## Layout

```text
core/                                 pure JVM: validation, presets, record codec, level mapping, patch
app/                                  Android: GUI + hooks + storage adapters
  ui/  data/  hook/
tools/probe/                          on-device probe (dump / expect)
```

See [docs/design.md](docs/design.md) (Chinese) for the architecture and the Android 16/17 analysis.

## License

[Apache-2.0](LICENSE)
