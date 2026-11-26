# HANDOFF：小米天气 RUST 通用化 —— 项目接手总览

> **这份文档的用途**：让下一个 Agent 在不通读仓库、不重复踩坑的前提下，准确理解这个项目的本质、当前状态、构建方式和工作约定。
>
> **快照信息**：2026-11-26 ｜ 版本 `v1.5.0` ｜ 分支 `main` ｜ 提交 `6030573` ｜ 发布人 fork：`Supremesir/miui-weather-rust-universal`
>
> **阅读顺序**：第 0、1、2 节必读；要改代码先读第 5、12 节；要发版读第 9 节。旧文档 `HANDOFF-PAD-LANDSCAPE.md` 只作为平板适配的历史调研记录，本文档是当前唯一权威交接。

---

## 0. 给接手 Agent 的三句话

1. **这不是标准 Android 工程**：没有 `settings.gradle`、`build.gradle`、`gradlew`，也不是完整的 Flutter 源码工程。它是「Android 原生宿主 + 上游 Flutter/Dart AOT 二进制 + 原版预编译 DEX」的混合体，唯一构建入口是仓库根目录的 `build.py`。
2. **你只能自由修改宿主层**：`port/`、`manifests/`、`weather-pages-module/`、`native/` 是自有代码；`variants/*/libapp.so`、`pages/original-weather-pages.dex`、`runtime/` 是上游冻结二进制，改动它们要走二进制补丁脚本，风险极高。
3. **任何 UI 改动都会让 `--strict-payload` 失败**，这是设计行为，不是 bug。开发阶段不加该参数；发布前必须同步 `references/*.json`，且 DEX 基准要用云端 JDK 17 的实际产物，不能用本机 JDK 21 的结果覆盖。

---

## 1. 项目是什么 / 不是什么

| | 说明 |
|---|---|
| **是什么** | 把小米天气 `18.0.0.27-R`（HyperOS 发行版）移植成普通 Android 单 APK：无需 Root、无需小米桌面/智能助理/额外运行库 APK。保留原版界面、动态天气背景、MiSans 字体、图标和预报资源。 |
| **「RUST 版本」含义** | 指原版 HyperOS 天气的发行版本代号，**不是**把应用重写成 Rust。 |
| **不是标准 Gradle 工程** | 没有 Gradle 包装器，不用 Android Studio 直接 Build。 |
| **不是完整 Flutter 工程** | 没有 `.dart` 源码、没有 `pubspec.yaml`。Flutter 页面以 AOT 快照 `libapp.so` 形式提供。 |
| **不是从零重写** | 核心页面来自上游二进制，本仓库做的是兼容层、桥接、资源和打包。 |
| **产物是合法 APK** | 通过 aapt2 + D8 + zipalign + apksigner 生成，可正常安装、签名、走 Android 升级链。 |
| **平台限制** | 仅 Windows 构建（`build.py` 硬性检查 `os.name == "nt"`）；产物面向 ARM64、Android 11+（`minSdk 28`，`targetSdk 36`）。 |

---

## 2. 当前发布状态快照（v1.5.0）

| 项 | 值 |
|---|---|
| 版本号 | `versionCode 10500`，`versionName 18.0.0.27-normal-v1.5.0` / `18.0.0.27-test-v1.5.0` |
| 包名 | 普通版 `dev.weather.oneplus`；试验版 `dev.weather.test` |
| 分支 / HEAD | `main` / `6030573`（Align CI DEX payload references） |
| 远端 | `origin` = 用户 fork；`upstream` = `Maga-King/miui-weather-rust-universal` |
| Release | `v1.5.0 · 平板布局与小组件适配`，Latest、非草稿、非预发布 |
| Release URL | `https://github.com/Supremesir/miui-weather-rust-universal/releases/tag/v1.5.0` |
| 发布构建 | `https://github.com/Supremesir/miui-weather-rust-universal/actions/runs/45616586094` |
| 普通版 APK | `weather-oneplus.apk`，云端 SHA-256 `30fad6fa9dcb75dffa3f4c27671de7e4ac466d4b77bb032c5f9764e062b99f15` |
| 试验版 APK | `weather-oneplus-experimental.apk`，云端 SHA-256 `70c69030d8c9a8e1e61072eeb231c8e8dc4ab01cbeaa0891764010efc5872b72` |
| 其他附件 | 两份 `dist/*.json` 构建报告 + `SHA256SUMS.txt` |
| 签名 | 本地 PKCS#12：`.build/signing/pad.p12`，别名 `weather`，RSA 2048，10000 天；私钥只存在于本机与 Actions Secrets |
| 严格比对结果 | normal 1029 条全等、test 1030 条全等，0 changed/missing/extra |
| 签名验证 | 两版均通过 APK Signature Scheme v3 |

> ⚠️ 本地 `dist/` 里的 APK 是**本机 JDK 21 构建**，哈希为 `d5660e...` / `06e451...`；GitHub Release 附件是**云端 JDK 17 构建**，哈希为上表两个值。二者功能一致但 DEX 字节不同，**不要把两套哈希混用**。

关键提交链：

| 提交 | 内容 |
|---|---|
| `8950f7e` | Add tablet layout and landscape support（平板模式） |
| `d0c7e35` | Fix responsive widget rendering（小组件等比缩放） |
| `af658f6` | Release version 1.5.0（版本号 + README） |
| `6030573` | Align CI DEX payload references（云端 DEX 基准） |

---

## 3. 仓库结构地图

```text
miui-weather-rust-universal/
├── build.py                     唯一构建入口（Python 标准库，无第三方依赖）
├── inputs.lock.json             锁定 build-inputs.zip 的 URL + SHA-256
├── BUILD_INPUTS.md              构建输入契约（比 README 更准，改构建前必读）
├── README.md                    面向用户的说明 + 版本更新记录
├── HANDOFF.md                   ← 本文档
├── HANDOFF-PAD-LANDSCAPE.md     平板适配历史调研（仅存档）
├── manifests/{normal,test}.xml  最终合并后的 AndroidManifest（aapt2 直接吃）
├── port/                        15 MB 自有兼容层
│   ├── *.java                   111 个文件 / 13,559 行 ← 唯一「可自由改」的应用逻辑
│   ├── host.c                   1,027 行 JNI：加载上游 .so、建 engine、转发 viewport/insets/touch
│   ├── patch_*.py               ★ 二进制补丁脚本（改 AOT / DEX / .so）
│   ├── res/                     95 文件；只有 2 个 layout，无 land/ 与 sw600dp/
│   └── assets/                  180 文件；小组件背景、pad_* 素材、TTS 资源等
├── native/{shim.c,shim.S}       上游加载器 shim（编成 libweathercompat.so）
├── third_party/xdl/             固定 commit 的 .so 加载器（vendored）
├── weather-pages-module/        24 个 Java：原版降水/空气/台风页桥接 + miui.os.Build 等桩
│   ├── src/                     实际参与编译的适配源码
│   └── compile-stubs/           仅用于编译期占位的上游 API 桩
├── splash/src/                  AndroidX splash 最后生成源码
├── references/{normal,test}.json 验收版 APK 逐条目 SHA-256（1029 / 1030 条）
├── tests/                       44 个测试脚本（多数是「假 Android 类 + javac 跑逻辑」）
├── .github/workflows/build.yml  CI：Windows + Py3.12 + JDK17 + SDK36/BT35/NDK29
├── .build/                      本地缓存（已 gitignore）：inputs、probe、shots、日志、签名
└── dist/                        本地构建产物（已 gitignore）
```

**上游二进制不在仓库里**，全在 `inputs.zip`（约 128 MB）。仓库只负责「重新编译我们这一层」，上游 ROM 的 `.so` / AOT / 字体 / 地图资源原样塞进 APK。

---

## 4. 架构：四个组成部分

### 4.1 自有宿主层（可改）

- `port/*.java`：Activity、Service、Provider、兼容桥、设置页、小组件渲染器。
- `port/host.c`：JNI 入口。用 xdl 建 namespace → `dlopen` 4 个上游 `.so` → 取 `HYPER_FLUTTER_INTERFACE` → 建 runtime/shell → `--aot-shared-library-name=...libapp.so` → `Launch("main")`。
- `port/*.py`：二进制补丁，带指纹校验，改的是上游 AOT/DEX/`.so` 的字节。
- `port/res`、`port/assets`：资源与资产覆盖层。
- `manifests/*.xml`：最终 manifest，直接交给 aapt2。

### 4.2 上游冻结二进制（不可直接改）

| 输入 | 作用 |
|---|---|
| `variants/normal/libapp.so`、`variants/test/libapp.so` | Flutter/Dart AOT 快照，所有主界面逻辑 |
| `pages/original-weather-pages.dex` | 原版降水/空气/台风等页面（7,038 类） |
| `runtime/lib/arm64-v8a/*.so` | 167 个上游原生库（两个自有库会被替换） |
| `runtime/assets/**` | 最终 APK 资产，逐字节保留 |
| `splash/splash-runtime-isolated.jar` | 隔离的 AndroidX/Kotlin 依赖 |
| `mipush/MiPush_SDK_Client_6_0_1-C.jar` | 小米推送 SDK |

### 4.3 构建输入与校验

- `inputs.lock.json` 锁定归档 URL 与 SHA-256。
- 归档内 `manifest.json` 逐文件校验；多文件/少文件都会失败。
- `references/*.json` 记录验收版 APK 每个 ZIP 条目的 SHA-256，用于发布前严格比对。

### 4.4 二进制补丁层（最后手段）

| 脚本 | 作用 |
|---|---|
| `port/patch_dpi_settings.py` | 改 Dart AOT：DPI 设置相关叶路由（~600 行，唯一成熟的 AOT 补丁范例） |
| `port/patch_widget_settings.py` | 改 Dart AOT：小组件设置入口 |
| `port/patch_weather_locale.py` | DEX 等长指令替换（Locale.equals → invoke-static） |
| `port/patch_mgl_rendering.py` | 改 `libhyper_opengl.so`：强制原生分辨率、去掉额外软件帧门控 |
| `port/isolate_neural_kotlin.py` | 隔离神经语音的 Kotlin 运行时依赖 |

> 这些脚本都依赖 `clang` / `llvm-objcopy`（NDK 提供），并且要求**逐字节指纹匹配**。不要在没有反汇编确认的情况下新增补丁。

---

## 5. 构建系统：`build.py` 到底做了什么

### 5.1 流水线

```text
inputs.lock.json
   ↓ fetch_inputs()        校验→复用/下载→解压到 .build/inputs
   ↓ verify_inputs()       逐文件 SHA-256
   ↓ toolchain()           定位 javac/d8/aapt2/zipalign/apksigner/clang
   ↓ compile_native()      NDK clang 编 host.c → libweatherhost.so
   │                       NDK clang 编 shim.c → libweathercompat.so
   ↓ compile_java()        javac 三趟：splash → module-stubs → module → host
   ↓ build_edition()       D8 合并 host+splash+module+pages.dex
   │                       → patch_weather_locale(classes.dex)
   │                       → aapt2 link(resources.apk)
   │                       → 塞 runtime lib / assets / AOT / renderer 补丁
   │                       → 字体清单重写 + 静态字重裁剪
   │                       → ZIP 组装 → zipalign → apksigner
   ↓ compare_payload()     与 references/*.json 比对；--strict-payload 有差异即失败
   ↓ dist/*.apk + dist/*.json + SHA256SUMS.txt
```

### 5.2 关键函数位置（`build.py`）

| 行 | 函数 | 作用 |
|---|---|---|
| 154 | `toolchain()` | 定位 JDK/SDK/BT/NDK 工具，缺件抛 `FileNotFoundError` |
| 177 | `fetch_inputs()` | 校验 lock → 复用缓存/下载 → 解压 |
| 213 | `verify_inputs()` | 逐文件校验 `manifest.json` |
| 234 | `compile_native()` | 两个自有 `.so` |
| 259 | `compile_java()` | 四趟 javac（含 stub） |
| 343 | `compare_payload()` | 载荷比对 |
| 366 | `keystore()` | 支持 `WEATHER_KEYSTORE_PATH` 或 `WEATHER_KEYSTORE_B64` |
| 381 | `build_edition()` | 单版本打包全流程 |
| 502 | `main()` | CLI 参数 |

### 5.3 命令速查

```bash
# 开发构建（无签名、无严格比对）
export ANDROID_HOME="C:/Code/Android/Sdk"
export JAVA_HOME="C:/Users/Fang/scoop/apps/oraclejdk-lts/current"
export WEATHER_NDK_VERSION=27.1.12297006   # 复用本机 NDK，不额外下载
python -X utf8 build.py --inputs .build/inputs --variant normal

# 发布前严格比对（改过代码必然失败，用于核对未改动部分）
python -X utf8 build.py --inputs .build/inputs --variant all --strict-payload

# 带签名构建
export WEATHER_KEYSTORE_PATH=".build/signing/pad.p12"
export WEATHER_KEYSTORE_PASSWORD=<从本机安全读取，勿打印>
export WEATHER_KEY_ALIAS=weather
python -X utf8 build.py --inputs .build/inputs --variant all --require-signing

# 只跑能力探测回归（无需 APK）
python -X utf8 tests/test_device_capabilities.py --java-home "$JAVA_HOME"
```

---

## 6. 为什么 GitHub Actions 会关心 `.class` / DEX

这是最容易误解的一点，单独说明。

### 6.1 编译链

```text
port/*.java + weather-pages-module/src/*.java
        ↓ javac（JDK 17）
      *.class
        ↓ D8（R8 的 DEX 编译器）
   classes.dex / classes2.dex（multidex）
        ↓ 与 pages/original-weather-pages.dex 及依赖合并
        最终 DEX
```

### 6.2 CI 不是「编译成功就发版」

`build.py --strict-payload` 会把最终 APK 解压，对**每个条目**计算 SHA-256，再和 `references/normal.json` / `test.json` 比较。比较范围包括：

- `AndroidManifest.xml`
- `classes.dex`、`classes2.dex`
- `resources.arsc`、`res/**`
- `lib/arm64-v8a/**`（167 个 `.so`）
- `assets/**`

**签名文件不参与比对**（所以换签名不会让比对失败，但换 JDK 会）。

### 6.3 为什么本地和云端 DEX 哈希不同

`classes.dex` 的字节由 **javac 输出 + D8 版本 + 合并顺序**共同决定：

- 本机 JDK 21 与云端 JDK 17 生成的 `.class` 常量池/调试信息可能不同。
- 本机 NDK 27 与云端 NDK 29 生成的 native 字节也可能不同。

因此：

- **native 基准保留云端 NDK 29 的结果**；
- **DEX 基准最终采用云端 JDK 17 的实际哈希**：
  - normal：`8cdaed01dc819cdc1a68e10a1c468f942d24f7dc435716c618ea9cf6139a621d`
  - test：`e811d511e55b02fc535d37270cea195e960364933153957ab62436ec52676c40`
- 第一次 1.5.0 Actions 失败就是「normal/test 各只有 `classes.dex` 一项差异」，提交 `6030573` 采用云端产物后通过。

### 6.4 实践含义

- 改了任何 Java/资源/manifest → `references` 必然失效 → 发布前必须更新。
- 更新 `references` 时，**DEX 条目要取 CI 实际构建的 Artifact 报告**，不要用本机结果硬替。
- `--strict-payload` 是发布门禁，不是本地开发命令。

---

## 7. 已实现的关键能力

### 7.1 平板布局（v1.5.0 核心）

**根因**：上游 Dart 的布局档位由 `com.android.os.system.properties#getAll` 返回的 `ro.build.characteristics` 决定：

- `padPortraitMode` / `padLandMode` / `smallTabletMode` / `phoneMode`

旧代码在 `port/AndroidDeviceProfile.java` 把它**硬编码为 `"nosdcard"`**，导致真实平板永远走 `phoneMode`。

**修复**：

```java
static String characteristics(Context context) {
    boolean tablet = context != null
        && context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    return tablet ? "nosdcard,tablet" : "nosdcard";
}
```

**为什么用 `smallestScreenWidthDp` 而不是当前窗口宽度**：

- 它与方向无关，手机横屏不会误判为平板。
- 600dp 是 Android 自己的平板阈值。

**配套改动**：

- `manifests/{normal,test}.xml`：`<application android:resizeableActivity="true">`；主 Activity 解除方向锁。
- 5 个原生设置页解除方向锁：背景、组件管理、外观、语音、WebView。
- 仍锁竖屏的地方：小组件配置弹窗 + 台风页。降水/空气质量两页已解除方向锁并改成平板卡片，见 7.1.1。
- `weather-pages-module/src/miui/os/Build.java`：`IS_TABLET` / `IS_MIPAD` 按 `smallestScreenWidthDp >= 600` 求值。
- `port/WeatherWidgetSettingsActivity.java`：大屏下内容限宽约 720dp 并居中。

**重要区分**：

- `isPadLayoutMode` 只是温度 OpenGL 组件自己的宽度判断，**不是**整页布局档位。
- 真正的布局档位看 `DynamicLayoutMode` 日志（`padPortraitMode` / `padLandMode` / `phoneMode`）。

#### 7.1.1 降水 / 空气质量的原版页改成平板卡片

原版 `ActivityMinuteRain` / `ActivityAqiDetail` 在平板上是整屏不透明页面。现在改成和 Dart 15 天弹窗同款的居中卡片：546×636dp、36dp 圆角、`#F2F2F2` 底面、20% 黑色遮罩。

**关键：主题必须写在 manifest 里，不能靠运行时 `setTheme()`。**
`ActivityManager` 在 activity 启动前就用该 theme 求出 `ActivityInfo.isTranslucentOrFloating()`，据此决定下层的 `WeatherActivity` 是否要被 stop。读成不透明时下层被 stop、SurfaceView 被销毁，卡片后面就是纯黑。`port/res/values-sw600dp/weather_original_page_dialog.xml` 用 `windowIsTranslucent` + `windowIsFloating` 解决，同时让下层保持 paused-but-alive。

**卡片几何**（`weather-pages-module/src/dev/weather/oneplus/PadPageCard.java`）全部对着 15 天弹窗实测标定，真机像素验证：三者的窗口框都是 1365×1440px（2880×1800 @ 2.5x），完全一致。

**关闭控件按原版各自保留**，不注入统一按钮：

- 降水页自带右侧 X，保留。
- 空气质量页自带左上角返回箭头，保留。
- 试过统一注入右侧 X：给降水叠加成两个图标，且我早期用 decor 全树扫描去找返回箭头时误伤降雨页的地图（未布局时在 (0,0)），导致地图空白。现已全部移除。

**已知未解**：偶现「关闭卡片后主界面瞬间上移一下再恢复」。已排除的假设：MIUI 的 `periodicallyMoveUpFirst` / `Resume onConfigurationChanged` 日志在普通全屏设置页上同样出现，与该位移没有因果关系。把窗口改成非 floating 也没能消除它，反而让 AQI 的 MIUIX 标题栏跑到卡片外（标题栏挂在 decor 上，不在 `android.R.id.content` 里），该实验已回退。

### 7.2 小组件响应式（v1.5.0 核心）

**确认的 bug**：

| 渲染器 | 问题 | 影响组件 |
|---|---|---|
| `WeatherCardWidgetRenderer` | `canvas.scale(width/w, height/h)` 分别拉伸 X/Y | 逐小时、自定义天气、多日、综合预报、日出日落 |
| `WeatherWidgetRenderer` | 云层/光照素材被绘制到非等比目标矩形 | 基础天气 2×2 |
| `WeatherOxygenBackground` | 「今日」2×2 背景 fitXY | 一加天气·今日 |
| `weather_widget.xml` | 外层 `ImageView` 用 `fitXY` | 宿主尺寸与位图不匹配时二次拉伸 |

**修复策略**：

- 原版卡片：**背景铺满宿主、内容等比居中**，统一 `float scale = Math.min(width/w, height/h)`。
- 大型天空素材：`assetCrop()` 等比 center-crop。
- 横条组件：保留原设计比例，不为了填满格子而拉伸。
- 外层 `ImageView`：`fitCenter`。
- 尺寸回调：`onAppWidgetOptionsChanged` 直接使用系统传入的 `Bundle`，不再只回查 `AppWidgetManager`。

**没有做**：没有全局开启 `resizeMode`，因为「手动拖拽格子数」是另一项产品行为变更，与旋转变形是独立问题。

**组件清单**：8 款标准 + 17 款一加/氧风格 = 25 款，全部在 `port/WeatherWidgetCatalog.java` 注册。

### 7.3 其他已实现能力

- 标准 Android 定位、后台定位、通知权限对接。
- 字体与动画设置（MiSans / 系统字体，字重 50%–150%）。
- 天气动画原生分辨率 + 最高 120Hz 请求。
- 标准 Android TTS 播报；可选 Kokoro 本地模型 / 缇宝预设（模型不随 Release 分发）。
- 小组件自动联网刷新（10/20/30/45 分钟、1/2 小时，默认 20 分钟）。
- 夜间免打扰（23:00–07:00）。
- 小米推送 SDK 已嵌入，但**服务器推送尚未验证送达**。

---

## 8. 测试体系

测试分两类：

### 8.1 纯逻辑回归（假 Android 类 + javac）

不需要 APK，不需要真机。CI 会跑其中 8 个：

| 测试 | 覆盖 |
|---|---|
| `test_background_permissions.py` | 后台权限 |
| `test_permission_settings_intents.py` | 权限设置跳转 |
| `test_android_location.py` | 定位 |
| `test_weather_speech.py` | 播报路由 |
| `test_device_capabilities.py` | 设备能力（947 项） |
| `test_widget_catalog.py` | 25 款组件注册 + 资源一致性 |
| `test_widget_responsive.py` | 25 种样式 × 151 项尺寸 + 防变形契约 |
| `test_weather_diagnostics.py` | 诊断 |

### 8.2 本机限制

- 部分旧测试没有 `--release 8` 回退，在本机 JDK 21 上会因 `Cannot close compiler resources` 失败。
- 这是**本机工具链差异**，不是业务代码错误；云端 JDK 17 全部通过。
- 新增测试请沿用 `build.py` 的回退写法：优先 `--release 8`，失败回退 `-source 8 -target 8`。

---

## 9. CI / Release 流程

### 9.1 工作流

`.github/workflows/build.yml`：

- 触发：推送 `v*` 标签，或手动 `workflow_dispatch`（可选 `release_tag`）。
- 环境：`windows-latest` + Python 3.12 + Temurin JDK 17.0.12 + Android SDK 36 + Build Tools 35.0.0 + NDK 29.0.14206865。
- 步骤：安装工具链 → 跑 8 个测试 → `build.py --fetch-inputs --variant all --strict-payload` → 上传 Artifact → 标签构建时发布 Release。

### 9.2 Secrets

| Secret | 必填 | 说明 |
|---|---|---|
| `RELEASE_KEYSTORE_B64` | 是 | PKCS#12 的 Base64 |
| `RELEASE_KEYSTORE_PASSWORD` | 是 | 签名文件密码 |
| `RELEASE_KEY_ALIAS` | 可选 | 默认取文件内别名；本项目为 `weather` |
| `RELEASE_KEY_PASSWORD` | 可选 | 与文件密码相同时无需设置 |

> Fork 不继承 Secrets。**任何回复中都不能输出 Secret 值或私钥 Base64。**

### 9.3 发版步骤

1. 改 `manifests/{normal,test}.xml` 的 `versionCode` / `versionName`。
2. 更新 `README.md` 的版本记录。
3. 本地构建，拿到新的载荷报告。
4. 更新 `references/*.json`（DEX 用云端 Artifact 的实际值）。
5. 提交、推送。
6. 打 `v*` 标签并推送，或对标签执行 `gh workflow run build.yml`。
7. 核对 Release 正文、两个 APK、两份报告、`SHA256SUMS.txt`。

---

## 10. 本机环境

| 项 | 值 |
|---|---|
| 工作目录 | `C:\Code\WorkSpace\miui-weather-rust-universal` |
| Python | 3.15（CI 用 3.12） |
| JDK | `C:\Users\Fang\scoop\apps\oraclejdk-lts\current`（21；CI 用 17.0.12） |
| Android SDK | `C:\Code\Android\Sdk` |
| platform | `android-36` |
| build-tools | `35.0.0` |
| NDK | 本机 `27.1.12297006`；CI `29.0.14206865` |
| 签名 | `.build/signing/pad.p12`（别名 `weather`） |
| 已缓存输入 | `.build/inputs/`（763 条目，无需重新下载） |
| 截图 | `.build/shots/`（平板/手机模拟验收图） |

---

## 11. 已知限制与风险

1. **没有真实 12.7 寸平板**：平板结论来自「手机 + 改分辨率/密度」模拟。真机上 `smallestScreenWidthDp` 必然 ≥ 600，逻辑等价，但不同 HyperOS 桌面的格子分配仍需逐机验收。
2. **原版页面仍锁竖屏**：6 个 `Original*Activity` 的布局在 `pages.dex` 里，未验证横屏安全性。
3. **签名不可混用**：本 fork 使用自己的 `pad.p12`，与上游官方签名不能覆盖安装；换签名必须卸载重装（会清空城市、设置、缓存）。
4. **`debuggable=true` 仍在**：normal/test 都是功能变体，不是严格 release 构建；GitHub Release 只是发布渠道，不会自动关闭调试属性。
5. **推送未验证**：MiPush 已嵌入，但首版实测停在底层账号注册阶段。
6. **双城壁纸玻璃未接通**：原版玻璃效果尚未完整复现。
7. **二进制补丁脆弱**：任何上游 AOT/DEX 更新都可能导致指纹失配，必须重新反汇编确认。

---

## 12. 接手 Agent 工作约定（重要）

### 12.1 提交纪律

- **禁止 `git add .`**。只显式列出目标文件。
- **禁止提交**：`.build/`、`dist/`、`*.apk`、`*.p12`、`*.jks`、`*.log`、`__pycache__/`、签名私钥、个人数据。
- 提交前先 `git status`；注意 `port/WeatherActivity.java` 可能显示 ` M`，但 `git diff --numstat` 为空，**只是 CRLF 警告，不要误提交**。
- 未经明确要求，不主动 commit / push / 打标签 / 发 Release。

### 12.2 修改流程

1. 先确认要改的是「自有层」还是「上游二进制」。
2. 自有层：直接改 `port/`、`manifests/`、`weather-pages-module/`。
3. 上游二进制：先看 `port/patch_*.py` 是否有先例，没有就先反汇编确认。
4. 改完跑对应测试；UI 改动还要跑 `test_widget_responsive.py` 和 `test_device_capabilities.py`。
5. 发布前更新 `references`，DEX 基准用云端产物。

### 12.3 常见坑

| 坑 | 处理 |
|---|---|
| `--strict-payload` 失败 | 正常。先看 `dist/*.json` 的 changed 列表，确认改动符合预期再更新 references。 |
| 本机 javac 报 `Cannot close compiler resources` | 用 `-source 8 -target 8` 回退，不要升级/降级 JDK。 |
| 本机 NDK 版本不对 | 设 `WEATHER_NDK_VERSION`，不要额外下载。 |
| `git` 报 dubious ownership / 全局配置无权限 | 用 `git -c safe.directory='*'`，不要改全局配置。 |
| `gh` 在沙箱中读不到配置 | 需要时显式提权，不要绕过。 |
| 下载 release 资源报 `CRYPT_E_REVOCATION_OFFLINE` | 用 `curl -sS -L --ssl-no-revoke`。 |

---

## 13. 关键文件索引

| 想改什么 | 先看哪里 |
|---|---|
| 构建流程 | `build.py`、`BUILD_INPUTS.md` |
| 平板布局 | `port/AndroidDeviceProfile.java`、`weather-pages-module/src/miui/os/Build.java`、`manifests/*.xml` |
| 小组件渲染 | `port/WeatherCardWidgetRenderer.java`、`port/WeatherWidgetRenderer.java`、`port/WeatherOxygenBackground.java`、`port/WeatherWidgetProvider.java` |
| 小组件尺寸 | `port/WeatherWidgetSizing.java`、`port/WeatherWidgetCatalog.java` |
| 启动与引擎 | `port/WeatherActivity.java`、`port/host.c`、`port/MajesticSurface.java` |
| 原版页面桥接 | `weather-pages-module/src/dev/weather/oneplus/Original*.java`、`port/WeatherPagesRuntime.java` |
| 二进制补丁 | `port/patch_*.py` |
| CI | `.github/workflows/build.yml` |
| 发布基准 | `references/{normal,test}.json` |
| 用户文档 | `README.md`、`WIDGET_COMPATIBILITY.md`、`SPEECH_COMPATIBILITY.md` |

---

## 14. 历史文档索引

| 文档 | 内容 | 状态 |
|---|---|---|
| `HANDOFF-PAD-LANDSCAPE.md` | 平板横屏改造前置调研 + 实施记录 | 历史存档，结论已并入本文档第 7.1 节 |
| `BUILD_INPUTS.md` | 构建输入契约 | 有效，改构建必读 |
| `WIDGET_COMPATIBILITY.md` | 各系统桌面小组件与材质兼容 | 有效 |
| `SPEECH_COMPATIBILITY.md` | 播报兼容实现 | 有效 |
| `ANIMATION_PERFORMANCE_AUDIT.md` | 动画性能审计 | 有效 |
| `CHANNEL_BRIDGE_AUDIT.md` | 平台桥接审计 | 有效 |
| `WIDGET_ICON_TIMING_AUDIT.md` | 基础天气 2×2 图标切换核对 | 有效 |
| `THIRD_PARTY_NOTICES.md` | 第三方组件说明 | 有效 |

---

**维护约定**：每次发布新版本，同步更新第 2 节的版本快照、第 7 节的能力清单和第 11 节的限制。本文档与 `README.md` 的分工是：README 面向用户，本文档面向接手开发者。
