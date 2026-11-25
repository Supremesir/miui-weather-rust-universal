# HANDOFF：小米天气（RUST 版通用化）— 代码盘点与「平板横屏」改造前置调研

> 用途：给接手 Agent 省 token 的交接文档。**阅读本文后不必再通读仓库**，只按需打开「关键文件索引」里点名的位置。
> 状态：仅调研，**未改任何代码**。本文档本身是新增文件，未提交 git。
> 生成时间：2026-11-25

---

## 0. 一句话结论

| 问题 | 结论 |
|---|---|
| 代码齐不齐？ | **构建所需的代码齐全**，`build.py` 能完整重编译 host Java + JNI + 资源 + DEX 并打包。缺的只有**上游 Dart 侧源码**（本来就不在仓库里，是二进制 AOT）。 |
| 能不能动手改？ | **能**。Java / C / 资源 / manifest 全在仓库内，改完可重新构建 APK。 |
| 平板横屏能不能做？ | **分层可做**：manifest+窗口层几乎免费；布局层取决于上游 Dart 是否已有 pad 分支 —— **证据表明它有**（AOT 里有 `isPadLayoutMode`、`Pad mode:`、`isPadLayout`、`DeviceInfo.isTabletDialogMode` 和整套 `pad_*` 资源），所以优先尝试「喂对信号」而不是二进制改 Dart。 |

---

## 1. 环境现状（已就绪 / 缺什么）

工作目录：`C:\Code\WorkSpace\miui-weather-rust-universal`（`git clone` 自 `https://github.com/Maga-King/miui-weather-rust-universal.git`，分支 `main`，HEAD `c7fcd25` = v1.4.1，工作区干净）

**已准备好的东西（勿重复下载，共 128 MB）**
- `.build/downloads/inputs.zip` — 官方构建输入归档，**已校验** SHA-256 = `a4548cfd6980c456ec78ba17747f96bcceb59cdb1b5c4e33eb0925ed12d820c5`（与 `inputs.lock.json` 一致）
  - 归档结构：`runtime/`(756) + `variants/`(3) + `pages/` + `splash/` + `mipush/` + `manifest.json`，共 763 条目
  - 因为文件已放在 `build.py` 期望的位置，`--fetch-inputs` 会跳过下载，直接校验并解压到 `.build/inputs/`
- `.build/inputs/` — 已单独抽出 3 个用于分析的文件：`pages/original-weather-pages.dex`(7,056,444B)、`splash/splash-runtime-isolated.jar`、`mipush/MiPush_SDK_Client_6_0_1-C.jar`
- `.build/probe/libapp.so` — 从归档抽出的 `variants/normal/libapp.so`(10,290,096B)，**上游 Dart AOT 快照**，字符串取证都基于它

**本机工具链**
| 项 | 状态 |
|---|---|
| Python | 3.15.2（CI 用 3.12） |
| JDK | `JAVA_HOME` = scoop `oraclejdk-lts` → **21**（CI 固定 17.0.12；`javac --release 8` 在 21 上仍可用，但建议装 17 对齐） |
| Android SDK | `ANDROID_HOME=C:\Code\Android\Sdk` |
| platform | `android-36` ✅ |
| build-tools | `35.0.0` ✅（build.py 硬编码这一个版本） |
| NDK | **缺 `29.0.14206865`**（现有 21.3 / 21.4 / 27.1）→ 编译 `host.c`/`shim.c` 与 AOT patch 都需要它 |
| 平台限制 | `build.py` 硬性要求 `os.name == "nt"`，即只能在 Windows 构建（本机满足） |

> 装 NDK：`sdkmanager --install "ndk;29.0.14206865"`。注意本机 `sdkmanager.bat` 已被 Google 换成「Android CLI」外壳（会提示 deprecation 并尝试自装 CLI），上次调用被用户中断。也可用新的 `android sdk` 子命令。

**下载踩坑（记录，避免重试）**
- 直接 `curl -L` 或在 Python 里 `urlopen` 拉 release 资源会失败：`schannel: CRYPT_E_REVOCATION_OFFLINE`（吊销服务器脱机）。
- 可用解法：`curl -sS -L --ssl-no-revoke -o ... <url>`（200，128,738,284B，约 149s）。
- 归档已就位，无需再下载。

---

## 2. 仓库结构地图（524 个文件，29 MB）

```
miui-weather-rust-universal/
├── build.py                    510 行  唯一构建入口（Python 标准库，无第三方依赖）
├── inputs.lock.json            锁死 inputs.zip 的 URL + SHA-256
├── BUILD_INPUTS.md             构建输入契约（必读，比 README 准）
├── README.md / *_AUDIT.md / THIRD_PARTY_NOTICES.md / LICENSE
├── manifests/{normal,test}.xml 合并后的最终 AndroidManifest（aapt2 直接吃这两个）
├── port/                       15 MB  宿主兼容层（Java + C + 资源 + 资产 + patch 脚本）
│   ├── *.java                  111 个文件 / 13,513 行  ← 唯一「可自由改」的应用逻辑
│   ├── host.c                  1,027 行  JNI：加载上游 .so、建 engine runtime、viewport/insets/touch 转发
│   ├── patch_*.py              ★ 二进制补丁脚本（改 AOT / DEX / .so）
│   ├── res/                    95 文件  aapt2 编译；只有 2 个 layout，**无 land/ 与 sw600dp/**
│   └── assets/                 180 文件  小组件背景、天气卡片、缇宝 TTS、pad_* 素材覆盖等
├── native/{shim.c,shim.S}      上游加载器 shim（编译成 libweathercompat.so）
├── third_party/xdl/            固定 commit 的 .so 加载器（vendored）
├── weather-pages-module/       24 个 Java：原版降水/空气/台风页桥接 + miui.os.Build 等桩
├── splash/src/                 AndroidX splash 的最后生成源码
├── references/{normal,test}.json  验收版 APK 的逐条目 SHA-256（各 1,029 条）
├── tests/                      41 个测试脚本（多数是「假 Android 类 + javac 跑逻辑」的回归）
└── .github/workflows/build.yml CI：Windows + Py3.12 + JDK17.0.12 + SDK36/BT35/NDK29
```

**上游二进制去哪了**：不在仓库里，全在 `inputs.zip`。仓库只做「重新编译我们这层」，上游 Rom 的 `.so`/AOT/字体/地图原样塞进 APK。

---

## 3. 关键文件索引（改哪里看哪里）

### 3.1 构建流水线 `build.py`
| 行 | 函数 | 作用 |
|---|---|---|
| 153 | `toolchain()` | 定位 javac/d8/aapt2/zipalign/apksigner/clang，缺件会抛 FileNotFoundError |
| 176 | `fetch_inputs()` | 校验 lock→复用/下载→解压到 `.build/inputs`（已缓存的 zip 会跳过网络） |
| 212 | `verify_inputs()` | 逐文件校验 `manifest.json` 里的 SHA-256；多/少文件即失败 |
| 233 | `compile_native()` | 用 NDK clang 编 `port/host.c`→`libweatherhost.so`、`native/shim.c`→`libweathercompat.so`，带 `-ffile-prefix-map` 复现历史 `__FILE__` |
| 258 | `compile_java()` | javac 三趟：splash → module-stubs → module → host（`--release 8`） |
| 355 | `build_edition()` | D8 合并 host+splash+module+pages.dex → 打 DEX 补丁 → aapt2 link → 塞 runtime lib/assets → **AOT/renderer 补丁** → 签名 |
| 398-399 | ↑ | `entries["libapp.so"] = patch_widget_settings(patch_dpi_settings(inputs/variants/<edition>/libapp.so))` ← **AOT 改造入口** |
| 397 | ↑ | `patch_mgl_rendering(libhyper_opengl.so)` 强制原生分辨率 |
| 372 | ↑ | `patch_weather_locale(classes.dex)` DEX 等长替换（Locale.equals→invoke-static） |
| 317 | `compare_payload()` | 与 `references/*.json` 比对；`--strict-payload` 有差异即失败 |
| 476 | `main()` | CLI：`--variant all|normal|test`、`--inputs`、`--fetch-inputs`、`--strict-payload`、签名参数 |

> ⚠️ **任何 UI/布局改动都会让 `--strict-payload` 失败**。开发阶段用 `python build.py --inputs .build/inputs --variant normal`（不加 `--strict-payload`），只需看 `dist/normal.json` 的 changed 列表。

### 3.2 启动与主界面（横屏改造的主战场）
| 文件:行 | 说明 |
|---|---|
| `port/WeatherActivity.java:96` | `FrameLayout root` 全屏容器 |
| `:125` | `surface = new SurfaceView(this){...}` 接 touch / IME / key |
| `:134-135` | `setContentView(root)` + insets 监听 |
| `:163` | `startEngine(nativeLibraryDir, filesDir/runtime, ..., assets, render_backend)` → 进 `host.c` |
| `:196-205` | `onConfigurationChanged()`：density 变化则 recreate，否则 `configureSystemBars()` + `sendSystemSettings()` + `nativeSurface(w,h)` + `applyInsets()` —— **尺寸变化已经会重新上报给引擎** |
| `:522-533` | `configuration()`：上报 `orientation`/`screen_width_dp`/`screen_height_dp`/`smallest_screen_width_dp`/`window_bounds`/`dm_width_pixels`… 到 Dart（`com.xiaomi.hyperos/configuration`） |
| `:563-564` | `surfaceChanged`/`surfaceDestroyed` → `nativeSurface()` |
| `:551-562` | `applyInsets()` → `nativeInsets()`（物理像素 padding/gesture/slop） |
| `:536-550` | `forwardTouch()` 打 312B/point 的二进制包给引擎 |
| `port/host.c:982-1007` | `nativeSurface()`：设置 `viewport[0..2]={density,w,h}`、`api[0xc8](holder,120,w,h,density)`、`api[0xa0](holder,viewport)`、`api[0xb0](holder,w,h)` |
| `port/host.c:1013-1027` | `nativeInsets()`：`viewport[3..15]` 写入 insets 后 `api[0xa0]` 重推 |
| `port/host.c:866-945` | `startEngine()`：xdl 建 namespace → dlopen 4 个 .so → 取 `HYPER_FLUTTER_INTERFACE` → 建 runtime/shell → `--aot-shared-library-name=...libapp.so` → Launch("main") |
| `port/MajesticSurface.java` | 把 SurfaceView 交给上游 Majestic 渲染器 + `PixelCopy` 取玻璃背景；`:89-122` `requestSurface(w,h,x,y)` 由 **Dart 反向驱动**（说明 Dart 侧自己算渲染区域尺寸） |
| `port/WeatherDisplayDensity.java:57-64` | `wrap(base)` 只覆盖 `densityDpi`，**不改 orientation** → 方向能透传 |
| `port/WeatherSystemBars.java` | edge-to-edge / 图标明暗（无方向假设） |
| `port/AndroidDeviceProfile.java:35` | `CHARACTERISTICS = "nosdcard"` ← **平板判定信号源之一** |
| `port/AndroidDeviceProfile.java:53-60` | `com.xiaomi.hyperos/device_info#getDeviceInfo` 返回 5 个字符串键 |
| `port/WeatherActivity.java:385` | `device_info` 兜底分支（`getDeviceInfo`→emptyMap，其它→false）。因 `AndroidDeviceProfile` 在前（`:328`）先命中，此分支对 `getDeviceInfo` **实际不可达**；但 `isTabletDialogMode` 之类未知方法会落到这里返回 `false` |

### 3.3 上游原版页（降水/空气/台风）
- `weather-pages-module/src/dev/weather/oneplus/Original*Activity.java`（6 个）继承 `com.miui.weather2map.*`，原版实现来自 `pages/original-weather-pages.dex`。
- `port/WeatherPagesRuntime.java`：用 `ResourcesLoader` 装载原版 0x7f 资源包（`assets/weather_pages/resources.apk`），并把 `cityData` 走 Gson+Bundle 传参。
- `weather-pages-module/src/miui/os/Build.java:7-8`：`IS_TABLET=false` / `IS_MIPAD=false` ← **这两个常量会被编译进 APK**（在 `compile_java` 的 module 趟里），可直接改。
- `weather-pages-module/src/miuix/autodensity/AutoDensityConfig.java`：全是 no-op 桩（全局密度改写被禁用，保持标准 Android 行为）。

### 3.4 二进制补丁脚本（改 Dart/原生前必读的三个）
- `port/patch_dpi_settings.py`（~600 行）：**唯一的 Dart AOT 补丁范例**。在验证过的 ELF padding 里放「无 CALL 的叶路由」，复用原 BL 与其 stackmap，靠 `FINGERPRINTS` 强校验地址/字节。文件头注释明确写了 GC/根集/stackmap 契约。
- `port/patch_widget_settings.py`：同风格，克隆一个设置行 + action。
- `port/patch_weather_locale.py`：DEX 等长指令替换（不改布局）。
- 三者都要求 `clang`/`llvm-objcopy`（NDK 29）。

---

## 4. 「平板横屏」受阻点：精确证据

### 4.1 三层锁定，逐层说明

**A. Manifest 层（最硬、最好改）**
`manifests/normal.xml:51`（test.xml 同）：
```xml
<activity android:name="dev.weather.oneplus.WeatherActivity"
    android:screenOrientation="portrait"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|density" .../>
```
- 全 manifest 共 **13 处 `screenOrientation="portrait"`**（16 个 `port/` Activity + 6 个 Original* + 配置页），全部锁死竖屏。
- `<application>` **没有** `resizeableActivity`，也**没有** `supports-screens`（默认值，平板可装）。
- Android 12L+ 的「平板强制可调整大小」在 `targetSdk=36` 下会被系统忽略 app 的固定方向 —— 但这里还叠了 B 层。

**B. 窗口/引擎层（几乎已通，只需放开）**
- `onConfigurationChanged` 已会把新 `w/h` 通过 `nativeSurface()` 重推到引擎（`host.c:999-1004`）。
- `configuration()` 已按当前 `Configuration` + `WindowMetrics` 实时取值，不含写死竖屏数值。
- `WeatherDisplayDensity.wrap()` 只覆盖 densityDpi。
- 结论：**只要 A 层放开，尺寸就会真的传到 Dart**。真正的未知在 C。

**C. 上游 Dart 布局（无源码，只能靠信号或二进制补丁）**
仓库里 **0 个 `.dart` 文件**，`libapp.so` 是 AOT 快照。但从快照字符串可见上游**自带 pad 分支**：

| 证据（`.build/probe/libapp.so` 中偏移） | 字符串 |
|---|---|
| @283776 | `[_updateTemperaturePosition] isPadLayoutMode=` |
| @283776 附近 | `PadLayoutMode`、`physicalSize=`、`dense displaySmall` |
| @363047 | `columns` / `, isPadLayout` |
| @698150 | `[_updateTemperaturePosition] Pad mode: cityManagement on top, keeping old X=` |
| @303825 / @742080 | `DeviceInfo.isTabletDialogMode` / `DeviceInfo.isTabletDialogMode failed:` |
| @216399 | `DeviceInfo.getDeviceInfo: channel returned null.` |
| @330648 | `DeviceInfo.getDeviceInfo: unexpected type` |
| @335648 | `screen_layout` |
| @265863 / @528559 | `smallest_screen_width_dp` / `screen_width_dp` |
| 资源 | `flutter_assets/packages/flutter_opengl/assets/pad/*.webp`(14)、`cloud_lite_vertex_shader_pad.glsl`、`pad_wind_icon.svg` 等 25 个 pad 资源**已在 APK 内** |

也就是说：**上游支持平板，只是当前被喂了「手机」信号**，加上 manifest 锁竖屏。

**D. 资源层**
`port/res/` 下只有 `layout/`（2 个：splash、widget）与 `xml/`（widget 声明），**没有 `layout-land/`、`layout-sw600dp/`**。因为主界面是 Flutter 式自绘 + SurfaceView，本来就不吃 Android layout 资源；设置页是原生 View，横屏时会被拉伸但不致崩。

### 4.2 可用的「平板信号」候选（按性价比）

| # | 信号 | 位置 | 说明 |
|---|---|---|---|
| 1 | `screenOrientation` → `fullUser`/`unspecified`（或直接删）+ 加 `android:resizeableActivity="true"` | `manifests/normal.xml:33-51`、`test.xml` | 必做第一步，纯配置 |
| 2 | `miui.os.Build.IS_TABLET` / `IS_MIPAD` → `true` | `weather-pages-module/src/miui/os/Build.java:7-8` | 会编进 APK；原版 dex 里有 `get IS_TABLET from miui.os.Build exception:`，说明是反射按名读取 |
| 3 | 系统属性 `CHARACTERISTICS`：`"nosdcard"` → `"tablet"`（或加 `tablet`） | `port/AndroidDeviceProfile.java:35` | 现测试 `tests/test_device_capabilities.py:56` 断言必须是 `nosdcard`，改要同步改测试 |
| 4 | `com.xiaomi.hyperos/device_info#isTabletDialogMode` 返回 `true` | 目前落到 `WeatherActivity.java:385` 返回 `false`；建议在 `AndroidDeviceProfile.handleCall()` 里显式实现 | Dart 日志名就是这个，最像「平板对话框模式」开关 |
| 5 | 让 `configuration()` 的 `smallest_screen_width_dp` 真实反映大屏 | `WeatherActivity.java:529` | 现在用 `c.smallestScreenWidthDp × systemDpi/densityDpi`，逻辑已在，需实机确认数值合理 |
| 6 | 二进制补丁改 Dart 判断 | `port/patch_*.py` 风格 | **最后手段**，代价极高（GC 安全契约 + 逐字节指纹） |

---

## 5. 建议的改造路线（未执行，供接手 Agent 取舍）

**Phase 0（0 风险，必做）**
1. 装 NDK 29；`python build.py --inputs .build/inputs --variant normal` 跑通基线，确认能出 unsigned APK。
2. 记录基线 `dist/normal.json`（有哪些 changed），后续每次比对。
3. 跑 `tests/` 里与本机改动相关的回归（`test_device_capabilities.py` 只需 `--java-home`，不需完整构建）。

**Phase 1（纯配置，验证「放开方向后会发生什么」）**
- 只改 `WeatherActivity` 一条：`screenOrientation` 去掉 + `resizeableActivity="true"`（先别动其它 12 处）。
- 装机观察：横屏时是否只是「拉伸」/是否重排/是否白屏。这一步的截图决定后面走 Phase 2 还是 Phase 3。

**Phase 2（喂信号，优先于改二进制）**
- 依上表 #2→#4→#3 逐个单独开关，**一次只改一个**，每次装机对比 `isPadLayoutMode` / `Pad mode:` 日志（`adb logcat | grep -E "PadLayout|Pad mode|isTabletDialogMode"`）。
- 注意 #3 会打破 `test_device_capabilities.py` 的 `nosdcard` 断言，需要同步更新测试或改为按 Build.DEVICE 分支。

**Phase 3（只在上游确实没有横屏分支时）**
- 照 `patch_dpi_settings.py` 的模板写新 patch：验证指纹 → 在 padding 里放 leaf router → 复用原 BL 与 stackmap → 不允许新增 CALL。
- 必须先做 ELF 反汇编确认目标调用点（本机有 NDK 后可用 `llvm-objdump -d`）。

---

## 6. 需要打断点确认的未知（交给接手 Agent）

1. **Dart 里 pad 模式的触发条件到底是什么**：目前只确认存在字符串，未定位判定函数。建议对 `libapp.so` 做 `llvm-objdump` 反汇编，交叉引用 `isPadLayoutMode` 附近的字符串字面量。
2. `DeviceInfo.isTabletDialogMode` 走哪个 channel/method（现有 Java 侧无对应实现，说明当前返回 false/异常）。
3. `WeatherActivity.java:385` 的 `device_info` 兜底是否真的不可达（`AndroidDeviceProfile` 先命中 `getDeviceInfo`，但其它 method 仍会落到这里）。
4. 横屏后 `MajesticSurface.requestSurface(w,h,x,y)` 的坐标语义（Dart 主动给值），以及玻璃背景 `PixelCopy` 是否仍正确。
5. 原版 6 个 `Original*Activity` 是否也支持横屏（它们继承 `com.miui.weather2map.*`，代码在 DEX 里，只能实机试）。

---

## 7. 常用命令速查

```powershell
# 构建（无签名、无严格比对；inputs 已缓存会自动复用）
cd C:\Code\WorkSpace\miui-weather-rust-universal
$env:ANDROID_HOME="C:\Code\Android\Sdk"
python build.py --inputs .build/inputs --variant normal

# 严格比对（改过代码必然失败，仅用于发布前核对未改动部分）
python -X utf8 build.py --inputs .build/inputs --variant all --strict-payload

# 单独校验/抽 AOT 补丁报告
python port\patch_dpi_settings.py .build\probe\libapp.so --report dpi.json

# 只跑能力探测回归（无需 APK）
python -X utf8 tests\test_device_capabilities.py --java-home $env:JAVA_HOME
```

---

## 8. 硬事实清单（省去重复取证）

- 仓库规模：524 文件 / 29 MB；`port/*.java` 111 个 / 13,513 行；`port/res` 95 文件；`port/assets` 180 文件。
- `references/normal.json` / `test.json`：各 **1,029** 条 payload SHA-256，其中 **170** 条在 `lib/arm64-v8a/`。
- inputs.zip：**128,738,284 B**，条目 **763**；`runtime/` 756、`variants/` 3、其余 4。
- `variants/{normal,test}/libapp.so` 各 **10,290,096 B**（两份同尺寸，test 另加 `assets/test-city/schema.sql`）。
- `pages/original-weather-pages.dex`：**7,056,444 B**，7,038 个类，41,660 字符串。
- MiPush JAR 指纹：`bce8d29a...578644`；pages dex 指纹：`e84cd226...1d3bb`（见 BUILD_INPUTS.md）。
- APK 内 native 库总数（上游）：**167**。
- `miui.os.Build` 的字段在 pages.dex 中被引用：`IS_ALPHA_BUILD`、`IS_DEVELOPMENT_VERSION`、`IS_INTERNATIONAL_BUILD`、`IS_MIPAD`、`IS_STABLE_VERSION`；方法：`checkRegion`。`IS_TABLET` 未在常量池字段引用里出现，但存在 `get IS_TABLET from miui.os.Build exception:` 字符串 → 反射读取。
- 引擎 ABI 关键数值（`host.c`）：`api[0] == 0x268`；callbacks[0]=0x68、[13]=0x18；`api[0x40]` 建 shell、`api[0x58]` Launch、`api[0xa0]` viewport、`api[0xa8]` surface、`api[0xb0]` size、`api[0xc8]` refresh/size/density。
- 要求：ARM64 + Android 11+（`minSdk 28`，但原版页 `WeatherPagesRuntime` 强制 API≥30）；UI 语言默认 zh-CN。

---

## 9. 实施记录：Pad 模式已落地（2026-11-25 第二轮）

> 本文档第 1～8 节的「未知」已全部实测结论化，下面是最终实现与验证。

### 9.1 真正的根因（实测，不是推测）

用 `adb shell wm size / wm density` 在有线手机上模拟大屏，抓 `files/debug_log/dart_main_*.txt` 得到三条硬证据：

| 观测 | 结果 |
|---|---|
| 800×1280dp 竖屏 + 原始 profile | `isPadLayoutMode=true`，但 `DynamicMultiChildLayoutDelegate layoutMode:DynamicLayoutMode.phoneMode` |
| 同上，只把 `CHARACTERISTICS` 改成 `nosdcard,tablet` | `layoutMode:DynamicLayoutMode.padPortraitMode` |
| 再切横屏 | `layoutMode:DynamicLayoutMode.padLandMode`，首页变成多栏卡片瀑布流 |

**结论**：上游 Dart 的布局档位由 `com.android.os.system.properties#getAll` 里的
`ro.build.characteristics` 决定（`padPortraitMode` / `padLandMode` / `smallTabletMode` /
`phoneMode`），而 `port/AndroidDeviceProfile.java` 过去把它**硬编码成 `"nosdcard"`**，
于是真实平板也永远走 `phoneMode`。`isPadLayoutMode` 只是温度 OpenGL 组件自己的尺寸判断，
不能代表布局档位（这也是第一轮误判的原因）。

### 9.2 改动清单

| 文件 | 改动 |
|---|---|
| `port/AndroidDeviceProfile.java` | 新增 `characteristics(Context)`：`smallestScreenWidthDp >= 600` 时返回 `"nosdcard,tablet"`，否则 `"nosdcard"`。只在 600dp 及以上窗口生效，手机行为零变化 |
| `manifests/{normal,test}.xml` | `<application android:resizeableActivity="true">`；`WeatherActivity` 去掉 `screenOrientation="portrait"` 并加 `resizeableActivity="true"`；5 个原生设置页（外观/语音/后台/组件管理/WebView）解除竖屏锁 |
| `port/WeatherWidgetSettingsActivity.java` | `page()` 在 `screenWidthDp > 720` 时按 720dp 限宽并居中，横屏/大屏下设置页不再通栏拉伸 |
| `weather-pages-module/src/miui/os/Build.java` | `IS_TABLET` / `IS_MIPAD` 改为按 `smallestScreenWidthDp >= 600` 求值（原版降水/空气/台风页反射读取） |
| `build.py` | ① `WEATHER_NDK_VERSION` 环境变量可复用本机已装 NDK（CI 仍固定 29）；② `--release 8` 在本机会因 `Cannot close compiler resources`（JDK 21 + ct.sym）失败，自动回退 `-source 8 -target 8` |
| `tests/test_device_capabilities.py` | 跟随 `systemProperties(context)` 签名与新断言（`nosdcard` → `nosdcard,tablet`），并加 `--release` 回退 |

### 9.3 验证结果（Xiaomi 12 / HyperOS 3，`wm size 1600x2560 @320dpi` 模拟平板）

- 竖屏 800dp：`padPortraitMode`，卡片居中限宽，侧边城市管理层正常
- 横屏 1280×800dp：`padLandMode`，多栏瀑布流（逐小时 / 15 日 / 生活指数并排），截图见 `.build/shots/final_land.png`
- 旋转时热切换 `padPortraitMode ⇄ padLandMode`，不重启引擎
- 复位 `wm size reset / wm density reset` 后手机竖屏仍是原手机布局（`phoneMode`），无回归
- `python -X utf8 tests/test_device_capabilities.py --java-home $env:JAVA_HOME` → `PASS: 947`

### 9.4 本机构建命令

```bash
export ANDROID_HOME="C:/Code/Android/Sdk"
export JAVA_HOME="C:/Users/Fang/scoop/apps/oraclejdk-lts/current"
export WEATHER_NDK_VERSION=27.1.12297006          # 复用已装的 NDK 27.1，不额外下载
export WEATHER_KEYSTORE_PATH=".build/signing/pad.p12"
export WEATHER_KEYSTORE_PASSWORD=weatherport
export WEATHER_KEY_ALIAS=weather
python -X utf8 build.py --inputs .build/inputs --variant normal
```

### 9.5 仍未验证 / 风险

1. **没有真机平板**：所有结论来自「手机 + 改分辨率/密度」模拟。真机 12.7" 上 `smallestScreenWidthDp` 必然 ≥ 600，
   逻辑等价，但 HyperOS 是否还会给未适配应用套兼容缩放需真机确认。
2. 签名为本机新建调试证书（`CN=Local Weather Pad Build`），与之前安装的包不同签名，重装会清数据。
3. 原版 6 个 `Original*Activity` 仍锁竖屏（其布局在 pages.dex 里，无法验证横屏安全性）。
4. `WeatherWidgetConfigureActivity`（小组件配置弹窗）保持竖屏。
5. 小组件、推送、TTS 均未受影响（未改动相关代码路径）。
