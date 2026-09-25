# Mineways Mobile · 我的世界存档 → 3D 模型（Android）

> 把 Minecraft **Java / 基岩 / 网易**存档里的建筑导出成 **OBJ**（带贴图与材质），
> 全程在手机上离线完成：框选坐标 → 导出 → 内置 3D 预览 → 打包 ZIP 放进「下载」。
>
> Export Minecraft builds to **OBJ** (textures & materials included), entirely on-device:
> pick a region, export, preview in 3D, get a ZIP in your Downloads folder.

**本仓库不包含任何编译好的 APK / AAB。** 请按下文自行构建。
**This repository ships no prebuilt APK/AAB.** Build it yourself — see [构建 / Build](#构建--build).

> **特别鸣谢 / Special thanks**
> **蚩尤** —— 测试：找出多个重大 bug（导出选项失效、3D 预览、导出 0 方块、玻璃等方块漏导、维度错误、选择器三态…），并给出可复现的现象与可靠日志
> **ZERO寂灵** —— 策划与移植主导（功能取舍、与桌面版行为对齐、安卓端交互），**并参与测试，提供了同样可靠的日志**
>
> 两位的测试与日志，决定了这些问题是"无法复现"还是"已修复"。
>
> **蚩尤 (Chiyou)** — testing: surfaced several major bugs (broken export options, 3D preview, zero-block exports, missing block types such as glass, wrong dimension, the three-state selector…) with reproducible symptoms and reliable logs
> **ZERO寂灵 (ZERO Jiling)** — planning & porting lead (feature scoping, desktop-behaviour alignment, Android UX), **who also tested and supplied equally reliable logs**
>
> Their testing and logs are what turned these issues from "cannot reproduce" into "fixed".

---

## 目录 / Table of Contents

- [中文说明](#中文说明)
  - [功能](#功能)
  - [导出选项的三态：多选 / 单选 / 不选](#导出选项的三态多选--单选--不选)
  - [环境要求](#环境要求)
  - [构建](#构建--build)
  - [目录结构](#目录结构)
  - [常见问题](#常见问题)
  - [鸣谢](#鸣谢)
- [English](#english)
  - [Features](#features)
  - [Export options: multi-select / single-select / none](#export-options-multi-select--single-select--none)
  - [Requirements](#requirements)
  - [Build](#build-1)
  - [Project layout](#project-layout)
  - [Troubleshooting](#troubleshooting)
  - [Acknowledgements](#acknowledgements)
- [许可 / License](#许可--license)
- [第三方组件 / Third-party](#第三方组件--third-party)
- [免责声明 / Disclaimer](#免责声明--disclaimer)

---

## 中文说明

### 功能

| 模块 | 说明 |
|---|---|
| **存档导入** | 选择存档文件夹（SAF）；可用 Shizuku 直接读取 `Android/data`；自动识别并就地解密**网易**存档；**基岩版**存档可用内置 Chunker 转成 Java 版后再导出 |
| **框选导出** | 手填 / 粘贴 `X Y Z`（支持 `X: -7 Y: -53 Z: -7`、`/tp`、全角标点、小数，自动逐轴排序）|
| **维度** | 主世界 / 下界 / 末地 —— 建筑在哪个维度就选哪个（**选错维度会导出"另一张地图"**）|
| **OBJ 导出** | 材质模式 5 种（不导出材质 / 实体材质颜色 / 颜色噪点纹理 / 整幅大图 / 每方块一张 PNG）、纹理 RGB·A·RGBA、单独纹理目录名、Z 轴向上、旋转 0/90/180/270、围绕原点居中 |
| **网格与分块** | 简化网格（合并共面）、边界块面、实心树叶、按类型分割、创建分组对象、导出单个块、肥大块、花草/告示牌翻倍、合并平面块 |
| **3D 打印结构** | 掏空 / 超中空 / 壁厚、封闭入口、填侧隧道、填气泡、连接零件、连接转角、**焊接所有共享边**、删除浮动对象、融雪、复合叠加 |
| **3D 打印尺寸** | 按高度(cm) / 按壁厚最小化 / 每区块(mm) / 目标成本、模型单位、物理材料 |
| **面剔除** | 全显示 / 标准（隐藏 barrier、structure_void）/ 强剔除（再加 structure_block）|
| **3D 预览（独立界面）** | 开源 **three.js** 离线渲染 OBJ，自动读同目录 MTL 与贴图；单指旋转、双指缩放，可切线框/贴图/双面/自动旋转；也能直接挑任意 `.obj` 或先看 ZIP |
| **诊断报告** | 每次导出都生成可粘贴的报告：世界体检、选区命中、`[选区实测]`（读到的方块类型 / Y 分层 / 俯视高度图）、`[核心回执]`（核心自己记录的生效选项）、`[导出内容]`（实际写进 OBJ 的材质清单，含 glass 探测）、`[对等性自检]`（顶点包围盒 vs 核心声明尺寸：缩放比 / 是否越界 / 坐标轴方向）|
| **内置工具** | 网页版 Blockbench（离线）、小游戏《寂零快跑》、世界信息与工具页 |

### 导出选项的三态：多选 / 单选 / 不选

「导出选项」面板（导出页的 **导出选项** 按钮，或长按「开始导出」）里的选择器具备三态语义：

| 状态 | 怎么操作 |
|---|---|
| **多选** | 开关（勾选框）随便勾，互不影响 |
| **单选** | 单选组内互斥，点谁是谁 |
| **不选** | 每个单选组**第一项就是「不选（用默认）」**；**再点一次已选中的那一项**也会回到「不选」 |

- **不选**以 `-1` 存盘，导出时按该键**默认值**发送 —— 行为等同于"从未设置过"，不会产生奇怪结果。
- 面板顶部另有 **全不选 / 全选 / 恢复默认** 三个快捷动作，改完立即生效（下次导出使用）。
- 所有选项**改动即保存**，无需额外确认。

### 环境要求

| 项 | 值 |
|---|---|
| JDK | **17**（`sourceCompatibility = 17`）|
| Android SDK | 平台 **34**、Build-Tools **34.x** |
| NDK | **26.3.11579264**（`ndkVersion`）|
| CMake | 随 SDK 安装即可（`app/src/main/cpp/CMakeLists.txt`）|
| Gradle | 使用仓库自带 wrapper（8.14.5）|
| 设备 | Android **8.0+（API 26）**；ABI：`arm64-v8a`、`armeabi-v7a`、`x86_64` |

> **Windows 提示**：Android Gradle Plugin 拒绝**含非 ASCII 字符的工程路径**（中文目录会直接构建失败）。
> 克隆到纯英文路径（如 `C:\dev\MinewaysMobile`）再构建；仓库自带 `tools/build_release.ps1` 也会先把源码复制到 `C:\mmbuild` 这类纯 ASCII 目录再编译。

> 原生库已按 **16 KB 页**对齐（`arm64-v8a` / `x86_64`），可在 Android 15+ 的 16 KB 页设备上正常加载。

### 构建 / Build

```bash
# 1) 环境变量（换成你自己的路径）
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=/path/to/jdk-17
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"

# 2) 写本机 SDK 路径（该文件已被 .gitignore 忽略）
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 3) 调试包
./gradlew :app:assembleDebug
#    产物：app/build/outputs/apk/debug/app-debug.apk

# 4) 正式包（未签名，自行用 apksigner 签名）
./gradlew :app:assembleRelease
#    产物：app/build/outputs/apk/release/app-release-unsigned.apk

# 5) 只编译原生核心（可选，排查 C++ 问题）
./gradlew :app:externalNativeBuildRelease

# 6) 单元测试（验证「导出选项」选择器的多选 / 单选 / 不选三态）
./gradlew :app:testDebugUnitTest
#    报告：app/build/reports/tests/testDebugUnitTest/index.html
```

签名示例（**不要把 keystore 提交进仓库**）：

```bash
zipalign -f -P 16 4 app-release-unsigned.apk app-aligned.apk
apksigner sign --ks my-release.jks --ks-key-alias myalias \
  --out MinewaysMobile-release.apk app-aligned.apk
```

Windows PowerShell 一键脚本见 `tools/build_release.ps1`（Gradle 构建 → zipalign → apksigner → 复制成品）。

### 目录结构

```
app/
  src/main/java/com/mineways/     # 界面与安卓侧逻辑（导出、预览、转换、解密…）
  src/main/cpp/                   # JNI 入口 + 导出诊断
      core/                       # Mineways 核心（读档、网格生成、OBJ/MTL 写出）
  src/main/assets/
      objviewer/                  # 离线 3D 预览器（three.js + OBJLoader/MTLLoader/OrbitControls）
      blockbench/                 # 内置网页版 Blockbench
      minigame/                   # 内置小游戏《寂零快跑》
      convert/                    # Chunker 转换所需映射资源
chunker-core/                     # 基岩版 → Java 版世界转换（Chunker）
tools/                            # 构建 / 审计 / 离线化脚本
```

### 常见问题

**导出 0 个方块？** 看报告里的 `[选区命中]` 与 `[选区实测]`：多半是 **Y 范围没盖住地形**，或**维度选错**（建筑在下界/末地却按主世界导）。报告会直接给出方块实际所在的 Y 范围。

**某个方块没导出（例如玻璃）？** 看 `[导出内容]`：它列出**实际写进 OBJ 的材质清单**，并单独探测名字含 `glass` 的材质。

- 清单里**有** → 已经导出了，看不到多半是查看器的**材质透明度/显示**问题（把该材质设为 Alpha Blend，或直接用 App 内置 3D 预览）。
- 清单里**没有** → 结合 `[选区实测]`（每种方块标注 `[会导出]` / `[被过滤]`）判断是被过滤，还是压根不在选区内。

**导出结果在哪里？** 勾选「导出后打包 ZIP 到「下载/MinewaysMobile」」后，ZIP 出现在公共「下载」目录（Android 10+ 走 MediaStore，无需权限）；Android 8/9 首次会申请存储权限，未授权则退回应用私有目录。

**怎么确认导出的 OBJ 与存档"完全对等"（无任何不对等）？** 看报告里的 `[对等性自检]`，它用**实测**给出三个判定：
① **缩放比** 应为 `1.000`（即 1 单位 = 1 方块，未被 3D 打印尺寸污染）；
② 模型**是否超出选区**（应为"未超出"）；
③ **坐标轴方向**（Mineways 绝对坐标 OBJ 的 X 轴是镜像的、Y/Z 与世界坐标一致 —— 这是与桌面版相同的既定约定，不是错误）。
再结合 `[导出内容]`（实际写进模型的材质清单）与 `[选区实测]`（读到的方块类型 / Y 分层），即可确认"读进来 → 写出去"全链路一致。

**为什么有的选项勾了看起来没变化？** 选项效果多发生在 OBJ 内部结构或几何细节上（分组、焊接、掏空…）。报告里的 `[核心回执]` 是**核心自己写的生效状态**，一眼可核对是否真的生效。

### 鸣谢

本项目能走到今天，靠的是下面两位的持续投入 —— **同等重要，只是分工不同**：

| 贡献者 | 角色 | 具体贡献 |
|---|---|---|
| **蚩尤** | 测试 | 测出并推动修复了**多个重大 bug**：导出选项点了不生效、3D 预览打不开、导出 0 方块（`MW_NO_BLOCKS_FOUND`）、玻璃等方块漏导、维度不对导致导出"货不对版"、单选组选上了取消不了、导出选项选择器三态等。每次都给出可复现的现象与**可靠日志/导出报告**，这是问题能被定位的关键。|
| **ZERO寂灵** | 策划 / 移植 **+ 测试** | 主导项目的策划与移植：功能取舍与优先级、与桌面版 Mineways 行为对齐（选项语义、默认值、导出结果一致性）、安卓端交互与整体推进节奏；**同时参与测试，提供了同样可靠的日志**，与蚩尤的反馈一起构成完整的复现链路。|

**两位的测试同等重要**：一人负责的问题面、另一人负责的复现细节，缺一不可 ——
这些 bug 大多在手机上才会出现，没有可靠的日志与现象描述，就无法定位，更无法验证修复。

同时感谢上游开源项目：**Mineways**（Eric Haines）、**Chunker**（hivemc）、**three.js**、**Blockbench**、**lodepng** 等（许可见 [`THIRD_PARTY.md`](THIRD_PARTY.md)）；以及每一位提交反馈、帮忙测试的朋友。

---

## English

### Features

| Module | What it does |
|---|---|
| **World import** | Pick a save folder (SAF); optional Shizuku access to `Android/data`; automatic in-place decryption of **NetEase** saves; **Bedrock** saves can be converted to Java with the bundled Chunker |
| **Region export** | Type or paste `X Y Z` (`X: -7 Y: -53 Z: -7`, `/tp`, full-width punctuation, decimals — auto-sorted per axis) |
| **Dimension** | Overworld / Nether / The End — pick the one your build lives in (**a wrong dimension exports a different map**) |
| **OBJ export** | 5 material modes (none / solid colors / swatch texture / full-sheet mosaic / one PNG per block), RGB·A·RGBA flags, tile folder name, Z-up, rotation 0/90/180/270, centering |
| **Mesh & grouping** | Decimate (merge coplanar faces), border faces, solid leaves, split by block type, make groups/objects, individual blocks, fatten blocks, double billboards, merge flat-top blocks |
| **3D-print structure** | Hollow / super-hollow / wall thickness, seal entrances, seal side tunnels, fill bubbles, connect parts, connect corner tips, **connect all shared edges**, delete floaters, melt snow, composite overlay |
| **3D-print sizing** | By height (cm) / minimize by wall thickness / mm per block / target cost, units, physical material |
| **Culling schemes** | Show all / Standard (hide barrier & structure_void) / Aggressive (also structure_block) |
| **3D preview (dedicated screen)** | Open-source **three.js** renders the OBJ offline and auto-loads the sibling MTL + textures; one-finger orbit, pinch zoom, wireframe / texture / double-side / autorotate toggles; you can also pick any `.obj` directly or preview a ZIP |
| **Diagnostics report** | Every export yields a copy-pasteable report: world health check, selection hit stats, `[选区实测]` (block types read / Y bands / top-down height map), `[核心回执]` (the core's own record of effective options), `[导出内容]` (materials actually written to the OBJ, incl. a glass probe), `[对等性自检]` (vertex bounding box vs the core's declared size: scale ratio / out-of-selection / axis orientation) |
| **Built-ins** | Offline web Blockbench, the mini-game 《寂零快跑》, world-info/tools page |

### Export options: multi-select / single-select / none

The **Export options** panel (button on the export page, or long-press *Start export*) uses three-state semantics:

| State | How |
|---|---|
| **Multi-select** | Checkboxes — any combination, including none |
| **Single-select** | Radio groups are mutually exclusive |
| **None** | Every radio group starts with **“不选（用默认） / none (use default)”**, and **tapping the already-selected item again clears it** |

- “None” is stored as `-1` and sent as **that key's default value** at export time — identical to “never set”.
- The panel header also offers **clear all / select all / restore defaults**, applied immediately.
- Changes are saved on the spot; the next export uses them.

### Requirements

| Item | Value |
|---|---|
| JDK | **17** |
| Android SDK | Platform **34**, Build-Tools **34.x** |
| NDK | **26.3.11579264** |
| CMake | installed with the SDK (`app/src/main/cpp/CMakeLists.txt`) |
| Device | Android **8.0+ (API 26)**; ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64` |

> **Windows note:** the Android Gradle Plugin refuses project paths containing **non-ASCII characters**
> (a CJK directory name fails the build outright). Clone into a plain-ASCII path such as
> `C:\dev\MinewaysMobile`. The bundled `tools/build_release.ps1` also copies sources into an ASCII
> sandbox (`C:\mmbuild`) before compiling.

> Native libraries are **16 KB-page aligned** for `arm64-v8a` / `x86_64`, so they load on Android 15+ 16 KB-page devices.

### Build

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=/path/to/jdk-17
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
echo "sdk.dir=$ANDROID_HOME" > local.properties      # gitignored

./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # unsigned; sign it yourself with apksigner
./gradlew :app:testDebugUnitTest    # unit tests for the export-option selector
```

```bash
zipalign -f -P 16 4 app-release-unsigned.apk app-aligned.apk
apksigner sign --ks my-release.jks --ks-key-alias myalias \
  --out MinewaysMobile-release.apk app-aligned.apk
```

On Windows, a PowerShell pipeline is provided in `tools/build_release.ps1`.

### Project layout

```
app/src/main/java/com/mineways/     # UI + Android-side logic (export, preview, convert, decrypt…)
app/src/main/cpp/                   # JNI entry points + export diagnostics
app/src/main/cpp/core/              # Mineways core (world reading, meshing, OBJ/MTL writing)
app/src/main/assets/objviewer/      # Offline 3D viewer (three.js + OBJLoader/MTLLoader/OrbitControls)
app/src/main/assets/blockbench/     # Bundled web Blockbench
app/src/main/assets/minigame/       # Bundled mini-game
chunker-core/                       # Bedrock → Java world conversion (Chunker)
tools/                              # Build / audit / offline-asset scripts
```

### Troubleshooting

**Export produced 0 blocks?** Check `[选区命中]` and `[选区实测]`. Usually the **Y range misses the terrain** or the **dimension is wrong** (a Nether build exported as Overworld). The report prints the actual Y range of the blocks.

**A block is missing from the OBJ (e.g. glass)?** Look at `[导出内容]`: it lists the **materials actually written**, plus a dedicated probe for names containing `glass`.

- Present → it *was* exported; you probably can't see it because of the viewer's material transparency/display settings (switch that material to Alpha Blend, or just use the in-app 3D preview).
- Absent → combine with `[选区实测]` (each block type is tagged `[会导出]` / `[被过滤]`) to tell filtering apart from “not in the selection at all”.

**Where do exports go?** With “package a ZIP into Downloads/MinewaysMobile” enabled, the ZIP lands in the public Downloads folder (Android 10+ uses MediaStore, no permission needed). On Android 8/9 the app asks for storage permission on first use; if denied, results fall back to the app-private folder.

**How do I confirm the exported OBJ matches the save exactly (no discrepancy)?** Read `[对等性自检]` in the report — it *measures* three things:
① the **scale ratio**, which must be `1.000` (1 unit = 1 block, unaffected by 3D-print sizing);
② whether the model **stays inside the selection** (it should);
③ the **axis orientation** (an absolute-coordinate Mineways OBJ mirrors the X axis while Y/Z match world coordinates — that is the established desktop-version convention, not a bug).
Together with `[导出内容]` (what was actually written) and `[选区实测]` (what was read), this verifies the whole "read → write" chain.

**Why do some options look like they do nothing?** Most options affect OBJ internals or geometry details (grouping, welding, hollowing…). The `[核心回执]` section of the report is the **core's own record** of what actually took effect — check it there.

### Acknowledgements

This project got where it is thanks to two people who kept at it — **equal credit, different roles**:

| Contributor | Role | Contribution |
|---|---|---|
| **蚩尤 (Chiyou)** | Testing | Found and drove fixes for **several major bugs**: export options that had no effect, the 3D preview not opening, exports producing 0 blocks (`MW_NO_BLOCKS_FOUND`), missing block types (e.g. glass), "wrong map" exports caused by the missing dimension switch, radio options that could not be cleared, and the three-state option selector. Every report came with reproducible symptoms and **reliable logs/export reports** — that is what made them fixable. |
| **ZERO寂灵 (ZERO Jiling)** | Planning / Porting **+ testing** | Led planning and the port: feature scoping and priorities, aligning behaviour with desktop Mineways (option semantics, defaults, output consistency), plus the Android UX and overall pace. **Also tested, supplying equally reliable logs** that completed the reproduction chain together with Chiyou's reports. |

**Both contributions to testing carry equal weight:** one covered a different surface of the app, the other
documented the reproduction details. Most of these bugs only appear on a real device — without reliable logs
and symptom descriptions they could neither be located nor verified as fixed.

Thanks as well to the upstream open-source projects — **Mineways** (Eric Haines), **Chunker** (hivemc), **three.js**, **Blockbench**, **lodepng** and others (see [`THIRD_PARTY.md`](THIRD_PARTY.md)) — and to everyone who reports issues or helps test.

---

## 许可 / License

本仓库以 **GNU General Public License v3.0（GPL-3.0）** 发布，全文见 [`LICENSE`](LICENSE)。

This repository is released under the **GNU General Public License v3.0 (GPL-3.0)** — see [`LICENSE`](LICENSE).

之所以选 GPL-3.0：仓库同时包含 **GPL-3.0** 的组件（Chunker 世界转换、网易存档解密适配），
而 GPL 具有传染性。**再分发（含修改后分发）请同样以 GPL-3.0 开源。**

GPL-3.0 was chosen because the repository also contains GPL-3.0 components (Chunker world
conversion and the NetEase save decryption adapter), and the GPL is copyleft. **Redistribution,
including modified versions, must remain GPL-3.0.**

## 第三方组件 / Third-party

详见 [`THIRD_PARTY.md`](THIRD_PARTY.md)：Mineways 核心、Chunker、three.js（MIT，含必须保留的声明）、
Blockbench（MIT）、lodepng（zlib）、region.cpp（BSD-2-Clause）、AndroidX/Material（Apache-2.0）等。

See [`THIRD_PARTY.md`](THIRD_PARTY.md) for the full list — Mineways core, Chunker, three.js (MIT,
notice required), Blockbench (MIT), lodepng (zlib), region.cpp (BSD-2-Clause), AndroidX/Material
(Apache-2.0), and others.

## 免责声明 / Disclaimer

本项目**不是**官方产品，与 Mojang Studios、Microsoft、网易均无隶属或背书关系。
Minecraft 相关商标与素材归各自权利人所有。请仅对你**合法拥有**的存档使用本工具，
并遵守对应版本的最终用户许可协议（EULA）。导出模型的使用请同样遵守相关协议。

This project is **not** affiliated with, endorsed by, or sponsored by Mojang Studios, Microsoft,
or NetEase. Minecraft-related trademarks and assets belong to their respective owners. Use this
tool only with saves you legally own, and comply with the applicable End User License Agreement.
