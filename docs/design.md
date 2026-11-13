# 设计：单一 CarrierConfig 设置（5G NR SS-RSRP 阈值）

状态：已实现并通过构建与单元测试；**未做真机端到端验证**（见 §7）。

## 1. 范围

| 项 | 决定 |
| --- | --- |
| 唯一可写 key | `5g_nr_ssrsrp_thresholds_int_array` |
| 值 | 4 个严格递增的整数，单位 dBm，范围 `[-140, -44]` |
| 其他 carrier config | 一律不读、不写、不改 |
| 持久化 | LSPosed 远程配置组 `nr_ssrsrp_thresholds` |
| 生效方式 | 拦截读取，每次读取时合并 |

原模块的 VoLTE / VoWiFi / VT / UT / 跨 SIM / 增强 4G / VoNR / NR availability /
`ImsProvisioning` 全部删除，对应的类与测试一并移除。

## 2. 分层

```text
:core  (纯 JVM，无 Android 依赖)
  Thresholds          值对象：校验、AOSP 等级映射、区间文案
  Preset              品牌预设（三星/OPPO/vivo/华为），OPPO 为默认
  ThresholdInput      文本 → 值对象（空值、非整数、溢出、越界、非递增）
  ThresholdRecord     版本化记录编码/解码 + FNV-1a 校验和
  CarrierConfigPatch  唯一允许触碰的 key（纯函数，便于证明「只改一个 key」）

:app   (Android)
  ui/MainActivity     单页 GUI；所有框架调用走后台线程
  ui/SignalBandView   比例化五段信号条 + 四个刻度
  ui/ServiceBridge    libxposed service 连接与生命周期
  ui/AppIcon          启动图标的启用/禁用（activity-alias，可逆）
  data/ThresholdStore 读写（GUI 与 Hook 共用同一实现）
  data/ThresholdSource Hook 端缓存、变更监听、TTL 兜底
  hook/CarrierConfigModule  LSPosed 入口
  hook/HookTargets    按名字 + 返回类型探测读取方法
```

**为什么是「一个 Android 模块 + 一个纯 JVM 模块」而不是三个 Gradle 模块**：APK 只有一个，
Hook 类必须和 GUI 在同一个 APK 里；真正的分层收益在于「校验/编码不依赖 Android，因而可以
在 JVM 上完整测试」，这一点由 `:core` 提供。GUI 与 Hook 之间没有直接引用，只共享
`ThresholdStore` 与数据格式，耦合点只有一个。

## 3. 数据流

```text
GUI 进程                                    com.android.phone 进程
─────────                                   ─────────────────────
输入 4 个整数
  → ThresholdInput.parse()  （shared 校验）
  → ThresholdStore.save()   （单次 commit）
  → XposedService.getRemotePreferences(group)
        │
        └──── LSPosed 数据库（持久化，跨重启）
                       │
                       ├─ 变更推送 ──→ ThresholdSource 缓存失效
                       │
CarrierConfigManager.getConfigForSubId(subId)
  → binder → CarrierConfigLoader.getConfigForSubIdWithFeature()
        → 拦截器：result.putIntArray("5g_nr_ssrsrp_thresholds_int_array", values)
        → 返回调用方（其余 key 原样）
```

Hook 只在 `result instanceof PersistableBundle` 时动作；没有有效配置时**原样返回**。

## 4. 存储与完整性

记录（v1）与校验规则见 README。三个要点：

1. **原子性**：`ThresholdStore.save()` 把所有字段放进同一个 `Editor`，一次 `commit()`。
   LSPosed 的远程配置更新是单次 IPC（`updateRemotePreferences(group, bundle)`），因此 Hook
   观察到的是旧记录或新记录，不存在「写了一半」的中间态。
2. **版本**：`schema_version` 参与校验和；未知版本直接判为无效，不做猜测性迁移。
3. **失败即放弃**：Hook 端任何一项校验失败都返回 `null`，拦截器不写入。这样「配置损坏」的
   后果是回到系统默认值，而不是把垃圾数据注入 CarrierConfig。

`ThresholdRecord.decode()` 的判定顺序：空 → `ABSENT`；缺版本/版本不支持 → `INVALID`；
缺字段/类型错误 → `INVALID`；校验和不符 → `INVALID`；范围或顺序不合法 → `INVALID`。

## 5. 变更如何到达 Hook

两条独立路径：

1. **推送**（正常路径）：`ThresholdSource` 在构造时注册
   `registerOnSharedPreferenceChangeListener`。LSPosed 的远程配置会在 GUI 提交后向已注入进程
   推送更新，监听器立刻置空缓存。
2. **轮询兜底**：`get()` 每次检查 `SystemClock.elapsedRealtime()`，超过 3 秒才重新读一次。
   防止某些框架实现不推送监听回调。`getAll()` 在 LSPosed 实现里是内存 Map 拷贝，不是 IPC。

Hot path 成本：一次 volatile 读 + 一次 `elapsedRealtime()`，只有超过 TTL 时才做一次 Map 拷贝。
这是为了不给「全系统最热的读取路径」增加可感知开销。

## 6. Hook 状态不是装饰

`MainActivity.probeStatus()` 调 `XposedService.getRunningTargets()`（API 102），匹配
`processName == "com.android.phone"`：

- 有且 `UP_TO_DATE` → `Hook active`
- 有但 `STALE` / `RELOADING` / `FAILED` → `Restart required`
- 没有 → `Hook inactive`
- 服务未连接 → `Framework unavailable`
- API < 102 或调用异常 → `Status unknown`

**不存在「假装成功」的分支**：写入成功但 Hook 未激活时，界面会同时显示 `Saved/Applied` 和
`Hook inactive` 的提示。

## 7. 验证矩阵

| 要求 | 覆盖方式 | 状态 |
| --- | --- | --- |
| 4 个合法值保存/读取 | `ThresholdStoreTest.saveThenLoadRoundTrips` | ✅ 通过 |
| 非递增校验 | `ThresholdsTest`、`ThresholdInputTest` | ✅ 通过 |
| 非整数 / 越界 / 溢出 | `ThresholdInputTest` | ✅ 通过 |
| 配置损坏回退 | `ThresholdRecordTest`、`ThresholdStoreTest`、`ThresholdSourceTest` | ✅ 通过 |
| 修改后 Hook 返回值 | `ThresholdSourceTest.updateThroughPreferencesIsObserved`（单元层） | ⚠️ 仅单元层 |
| 其他 key 不受影响 | `CarrierConfigPatchTest`（纯函数层） + 探针 `--dump` diff（设备层） | ⚠️ 设备层待执行 |
| 重启后恢复 | 依赖 LSPosed 数据库；无设备无法验证 | ❌ 未验证 |
| Telephony 进程重启后重新注入 | 依赖 `onPackageReady` 重新加载；无设备无法验证 | ❌ 未验证 |
| Android 16 QPR2 / 17 兼容 | 见 §8；构建通过，未上机 | ❌ 未验证 |

已执行：`:core:test`（37）、`:app:testDebugUnitTest`（26）、`:app:assembleDebug`、
`:app:assembleRelease`（含 R8）、`:app:lintDebug`、APK 内容检查（`java_init.list` 被
`-adaptresourcefilecontents` 正确重写为混淆后的类名）。

## 8. Android 16 QPR2 / 17 差异分析

**变化的是写入侧，不是读取侧。**

- Android 16 QPR2 起，`overrideConfig(persistent=true)` 在 Pixel telephony 上不再可靠持久化：
  `CarrierConfigLoader` 增加了身份校验，普通应用（即使转成系统应用）也过不了这层。原项目实测
  到 `persistent=false` 生效、重启即失效。
- 本模块不经过 `overrideConfig`，也不调用 `isSystemApp()` 之类的校验，因此这些加固与它无关。
- 读取路径在两个版本上都是 `CarrierConfigLoader.getConfigForSubIdWithFeature(int, String, String)`
  返回新建的 `PersistableBundle`，且 `getConfigForSubId` 与
  `getConfigSubsetForSubIdWithFeature` 都委托给它。原项目在 Pixel 6 Pro / Android 17 上实测过
  这三个签名，设计文档保留了原始记录。
- 因此适配策略是「版本无关」的：不按 `Build.VERSION.SDK_INT` 分支，而是按**方法名 + 返回类型**
  探测（`HookTargets`），取参数最少的重载，避免 Android 每版给私有方法加参数就失效。
- 唯一需要版本意识的点是 API 102 的服务能力：`getRunningTargets()` 不存在时状态退化为
  `Status unknown`，不影响 Hook 本身。

仍未验证的风险：Android 16 QPR2 的具体构建是否把读取方法改名/挪类。模块在找不到目标时只写
`Log.e` 并放弃安装，不会崩溃，但也就不会生效——这正是需要真机执行 §7 中「设备层」两步的原因。

## 9. 已知风险与取舍

| 风险 | 处理 |
| --- | --- |
| OEM ROM 里没有 `CarrierConfigLoader` | 记录 `Log.e` 并放弃；不抛异常、不影响 telephony |
| Google 重命名读取方法 | 往 `HookTargets.find(...)` 的候选列表补名字（与原项目维护成本一致） |
| 远程配置不可用（嵌入式框架） | Hook 不安装并记日志；GUI 显示 `Framework unavailable` |
| 并发写 | 只有 GUI 写；写入是单次 commit，天然串行 |
| 用户输入非法 | GUI 阻止写入；Hook 端二次校验，非法即不注入 |
| release 混淆掉入口类名 | `-adaptresourcefilecontents` 重写 `java_init.list`；已验证产物中为 `b` 且类存在 |
| 未签名 release 被误当正式版 | 无 `keystore.properties` 时用 debug key，构建日志会警告 |
| 隐藏图标后找不到入口 | 启动入口独立为 `activity-alias`，主 Activity 保持 exported；界面内提示 LSPosed 与 adb 两种入口，开关读组件真实状态而非本地偏好 |
