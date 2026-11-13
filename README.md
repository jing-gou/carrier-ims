# NR Signal Thresholds

面向 **AOSP / LineageOS 系类原生 ROM** 的 5G NR 信号阈值调节器：一个 LSPosed 模块 + 单页 GUI，
只改 CarrierConfig 里的一个 key。

```text
CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY
  = "5g_nr_ssrsrp_thresholds_int_array"
  = { threshold1, threshold2, threshold3, threshold4 }
```

## 为什么做这个

类原生 ROM 在部分地区（尤其国内）沿用 AOSP 默认的 NR 分级边界，信号格偏保守：同样的 RSRP，
主流品牌 ROM 显示 4 格，原生可能只有 2–3 格。本模块把四个分级边界调到更贴近主流品牌 UI 的位置，
让信号显示符合本地用户的阅读习惯。

**它只改变「怎么显示」，不改变「信号本身」**：RSRP 测量值、网络能力、速率、功耗都不受影响。
阈值调得越乐观，弱覆盖下越容易显示满格——这是取舍，不是信号提升。

## 致谢

- Fork 自 **[NKU100/carrier-ims](https://github.com/NKU100/carrier-ims)** —— 原模块在 Pixel 上打开
  VoLTE / VoWiFi / VT / VoNR，本仓库沿用了它的「拦读取」Hook 方案与实测结论
- 阈值取值参考 **[vvb2060/Ims](https://github.com/vvb2060/Ims)**
- 同类的 CarrierConfig 覆写方案：**[kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)**
- 框架与运行时：**[libxposed/api](https://github.com/libxposed/api)**、
  **[libxposed/service](https://github.com/libxposed/service)**、**[LSPosed](https://github.com/LSPosed/LSPosed)**

原项目关于 `CarrierConfigLoader` 真实签名、委托关系与优先级的实测记录仍然准确，
保留在 [设计文档](docs/superpowers/specs/2026-09-12-carrierims-xposed-design.md)。
VoLTE / VoWiFi / VT / VoNR 等能力已从本仓库移除，只保留 NR 阈值这一项。

## 功能

- 四个 SS-RSRP 阈值（-140 ~ -44 dBm）可编辑，严格递增校验
- 实时预览：按 AOSP 规则把区间渲染成五段信号等级
- 品牌预设：三星 / OPPO / vivo / 华为，**OPPO 为默认**
- 简体中文 / English 双语界面（跟随系统语言）
- 可隐藏桌面图标（应用内开关，可随时恢复）
- 配置持久化在 LSPosed 远程配置里，重启自动恢复，不需要 Shizuku

## 适用与不适用

| | |
| --- | --- |
| 适用 | AOSP、LineageOS、Pixel Experience 等类原生 ROM |
| 不适用 | 深度改造 telephony 的 OEM ROM（MIUI/HyperOS、ColorOS、OriginOS、EMUI/HarmonyOS 等）。这些 ROM 里可能找不到 `com.android.phone.CarrierConfigLoader`，模块会记录错误并放弃，不会崩溃 |
| 系统 | Android 8.0+（`minSdk 26`） |
| 框架 | LSPosed **2.2.0+**（libxposed API 102） |

## 为什么不用 `overrideConfig()`

`CarrierConfigManager.overrideConfig()` 需要 `MODIFY_PHONE_STATE`，普通应用只能经 Shizuku 借 shell
身份；从 Android 16 QPR2 起，Google 进一步收紧了这条写入路径，`persistent=true` 已不能可靠跨重启保存。

本模块改拦**读取**：在 `com.android.phone` 里拦截 `CarrierConfigLoader.getConfigForSubIdWithFeature()`，
在返回的 `PersistableBundle` 中只覆盖一个 key。读取方本就在权限检查之内，所以不需要 Shizuku、
不需要 root、不改系统分区，也没有会失效的 override 状态——`dumpsys carrier_config` 看到的仍是运营商原值。

## 编译

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk

# 单元测试（纯 JVM，不需要设备）
./gradlew :core:test :app:testDebugUnitTest
```

- JDK 21、Gradle 9.7.1（wrapper 自带）、AGP 9.4.0
- `compileSdk = 37` / `compileSdkMinor = 2`，`buildToolsVersion = 37.0.0`
- 签名：在仓库根目录放 `keystore.properties`（已在 `.gitignore` 中）：

  ```properties
  storeFile=keystore/nrst-release.jks
  storePassword=…
  keyAlias=nrst
  keyPassword=…
  ```

  也可以用环境变量 `NRST_KEYSTORE` / `NRST_KEYSTORE_PASSWORD` / `NRST_KEY_ALIAS` / `NRST_KEY_PASSWORD`。
  没有配置时 release 回落到 **debug key 签名**，仍可安装，但不适合分发。

- 当前发布版的签名证书 SHA-256：`76d7c9407d21a666a47069f095ff341cecf8fc2578d595abe853834e212e3ec8`

## 安装

0. **如果装过旧包** `io.github.nku100.carrierims`（v1 / v2），先卸载：

   ```bash
   adb uninstall io.github.nku100.carrierims
   ```

   新旧包名不同，会同时存在并各自 hook 同一个 key；而且签名不同，无法原地升级。
1. 安装 APK。
2. 在 LSPosed 中启用 **NR Signal Thresholds**，作用域已固定为 `com.android.phone`。
3. 重启 `com.android.phone` 进程，或重启手机：

   ```bash
   adb shell su -c 'kill $(pidof com.android.phone)'
   ```

## 使用

界面跟随系统语言。打开即是一个单页：

| 区域 | 内容 |
| --- | --- |
| 顶部 | 应用名、副标题、**Hook 真实状态**（来自框架，不是猜的） |
| 阈值 | 预设行 + 四个整数输入框，单位 dBm |
| 等级预览 | 按当前值实时画出五段信号等级条与区间文案 |
| 应用 | 隐藏桌面图标的开关 |
| 底部 | **应用** / **保存** / **恢复默认** |

- **应用**：校验 → 写入框架远程配置 → 复核 Hook 状态，并报告已应用
- **保存**：同一套校验与写入，不额外复核
- **恢复默认**：填入 OPPO 预设，不会直接写入，确认后再点应用

状态含义：

| 状态 | 含义 |
| --- | --- |
| `Hook 已生效` | 框架报告 `com.android.phone` 正在运行当前版本模块代码 |
| `Hook 未生效` | 模块没被加载：未启用、作用域不对，或进程没重启 |
| `需要重启` | 进程里跑的是旧版本模块代码 |
| `框架不可用` | 连不上 LSPosed 服务 |
| `状态未知` | 框架 API 低于 102，无法查询运行中的目标 |

## 预设

预设行只**填入输入框**，仍需点「应用」才写入。品牌表通常按 T1→T4 从大到小列出，
而 Android 要求严格递增，所以内部存的是升序版本：

| 预设 | 阈值 1 | 阈值 2 | 阈值 3 | 阈值 4 |
| --- | ---: | ---: | ---: | ---: |
| 三星 | -115 | -105 | -95 | -85 |
| **OPPO（默认）** | **-120** | **-113** | **-105** | **-97** |
| vivo | -115 | -110 | -105 | -97 |
| 华为 | -120 | -113 | -103 | -90 |

手动改成任何非预设组合时，预设行的选中状态会自动取消。

## 等级边界（按 AOSP 校准）

`CarrierConfigManager` 的注释与 `CellSignalStrengthNr.updateLevelWithMeasure()` 共同定义了：

| Level | 常量名 | SS-RSRP 区间 |
| --- | --- | --- |
| 0 | `SIGNAL_STRENGTH_NONE_OR_UNKNOWN` | `< threshold1` |
| 1 | `SIGNAL_STRENGTH_POOR` | `[threshold1, threshold2)` |
| 2 | `SIGNAL_STRENGTH_MODERATE` | `[threshold2, threshold3)` |
| 3 | `SIGNAL_STRENGTH_GOOD` | `[threshold3, threshold4)` |
| 4 | `SIGNAL_STRENGTH_GREAT` | `>= threshold4`（上界 -44） |

注意：**低于 threshold1 是 level 0（无信号/未知），不是 POOR**；最高一级在 CarrierConfig 文档里叫
`EXCELLENT`，在 `SignalStrength` 里叫 `GREAT`，是同一个东西。中文界面把五级显示为
无信号 / 弱 / 中 / 良 / 优，预览卡片说明里注明了对应的英文常量。

## 验证

`dumpsys carrier_config` 打印的是 `CarrierConfigLoader` 的内部 bundle，本模块不写任何状态，
所以**注入不会出现在 dumpsys 里**。必须从读取方验证：

```bash
# 打印当前阈值
ANDROID_HOME=$SDK bash tools/probe/run-probe.sh

# 断言四个值
ANDROID_HOME=$SDK bash tools/probe/run-probe.sh --expect=-120,-113,-105,-97

# 证明「其他 carrier config key 完全没变」：
#   1. 关闭模块并重启 com.android.phone
#   2. ./run-probe.sh --dump > before.txt
#   3. 启用模块并重启 com.android.phone
#   4. ./run-probe.sh --dump > after.txt
#   5. diff before.txt after.txt   # 只允许 5g_nr_ssrsrp_thresholds_int_array 一行不同
```

日志：`adb logcat -s NrThresholds`。不要用「日志为空」判断模块没加载——logd 的 main 缓冲区默认
只有 256 KiB，开机日志能在一分钟内把它冲掉。

## 配置存储

写在 LSPosed 托管的远程配置组 `nr_ssrsrp_thresholds` 里，GUI 与 Hook 共享同一份数据：

- 不需要 `MODE_WORLD_READABLE`，也不依赖应用私有目录被别的 uid 读取；
- 手机重启后仍在，Hook 启动时自动读取，不需要每次开机跑 Shizuku；
- 一次 `commit()` 写入全部字段，Hook 只会读到旧记录或新记录，不会读到半份。

记录格式（version 1）：

```text
schema_version : int    固定 1
threshold_1    : int    dBm
threshold_2    : int
threshold_3    : int
threshold_4    : int
checksum       : int    FNV-1a over "1|t1|t2|t3|t4"
applied_at     : long   仅用于展示，不参与校验
```

Hook 端会重新校验版本、字段类型、校验和、范围与严格递增；任何一项不通过就**不注入**并记日志，
绝不把未经校验的数据塞进 CarrierConfig。

## 隐藏应用图标

「应用」卡片里的开关只禁用桌面启动入口（一个 `activity-alias`），模块与 Hook 不受影响。
隐藏后要再打开界面，只有两条路：

- LSPosed → 模块 → **NR Signal Thresholds**
- `adb shell am start -n io.github.jingou.nrst/.ui.MainActivity`

## 兼容性

| 系统 | 状态 |
| --- | --- |
| Android 16 QPR2 | 构建通过，**未在真机验证** |
| Android 17 / API 37 | 构建通过，**未在真机验证**（原项目曾在 Pixel 6 Pro / Android 17 验证过同一 Hook 机制） |
| 其他版本 / ROM | 未验证；模块按方法名 + 返回类型探测，找不到目标时只记错误、不崩溃 |

## 尚未大范围测试

本项目目前**没有在多种机型与 ROM 上做过大范围测试**，也没有完成真机端到端验证。
已完成的只有：debug/release 构建（含 R8）、68 个 JVM 单元测试、lint、APK 内容检查。

遇到问题欢迎提 issue，请附上：

- 机型、ROM 名称与版本、Android 版本、LSPosed 版本
- `adb logcat -s NrThresholds` 的输出
- `ANDROID_HOME=$SDK bash tools/probe/run-probe.sh` 的完整输出
- 界面顶部显示的状态（Hook 已生效 / 未生效 / 需要重启 / 框架不可用）

## 已知限制

- 只改这四个阈值，其他 carrier config 一律不碰
- 依赖 AOSP 的 `com.android.phone.CarrierConfigLoader`；OEM ROM 里可能不存在
- Google 若重命名读取方法，需要往 `HookTargets` 的候选列表补名字
- `staticScope=true`：作用域不可由用户修改
- 签名密钥不在仓库里（`keystore.properties`、`*.jks` 均已忽略）。**请备份**：丢失后无法再发布能覆盖升级的新版本

## 目录

```text
core/                                 纯 JVM：校验、预设、存储编码、等级边界、单-key 补丁
app/                                  Android：GUI + Hook + 存储适配
  ui/                                 MainActivity、SignalBandView、AppIcon、ServiceBridge
  data/                               ThresholdStore、ThresholdSource
  hook/                               CarrierConfigModule、HookTargets
tools/probe/                          真机探针（dump / expect）
```

架构与 Android 16/17 差异分析见 [docs/design.md](docs/design.md)。

## License

[Apache-2.0](LICENSE)
