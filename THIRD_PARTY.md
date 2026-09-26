# 第三方组件与许可 / Third-Party Components and Licenses

本仓库把若干开源项目作为**源码或静态资源**内置（便于离线使用）。它们各自的权利归原作者，
许可以下方表格与各自源码头部为准。**如果你要再分发（尤其是商用），请逐项核对上游许可。**

This repository bundles several open-source projects as source code or static assets so the app
works fully offline. Each remains the property of its authors; the authoritative license text is
the one shipped in the upstream project and/or in the file headers listed below.

| 组件 / Component | 位置 / Location | 上游 / Upstream | 许可 / License |
|---|---|---|---|
| Mineways 核心（C++：读档、网格生成、OBJ/MTL 导出） | `app/src/main/cpp/core/**` | erich666/Mineways | 见上游仓库（本仓库按其条款使用）/ see upstream |
| Chunker（基岩版 → Java 版世界转换） | `chunker-core/**` | hivemc/chunker | GPL-3.0 |
| 网易存档解密适配 | `app/src/main/java/com/mineways/NeteaseDecryptor.java` | 本项目 / 源码内声明 | GPL-3.0（源码头部已声明） |
| three.js r134（OBJ 预览渲染）<br>+ OBJLoader / MTLLoader / OrbitControls | `app/src/main/assets/objviewer/lib/**` | mrdoob/three.js | MIT |
| Blockbench（内置网页版） | `app/src/main/assets/blockbench/**` | JannisX11/blockbench | MIT |
| Snowstorm（内置网页版，镜像自上游 master） | `app/src/main/assets/snowstorm/**` | JannisX11/snowstorm | GPL-3.0-or-later |
| Snowstorm 汉化字典与适配脚本（本项目自研，非上游）：`zh-dict.js`（英→中对照表）、`hans.js`（注入式安全翻译 + 导出接管），另 `index.html` 相对上游增加了两个脚本标签与中文标题 | `app/src/main/assets/snowstorm/zh-dict.js`、`hans.js`、`index.html` | MinewaysMobile | GPL-3.0（与本仓库一致）|
| lodepng（PNG 读写） | `app/src/main/cpp/core/lodepng.*` | lvandeve/lodepng | zlib |
| region.cpp（Minecraft region 文件读取） | `app/src/main/cpp/core/region.cpp` | Ryan Hitchman（文件头保留原版权声明） | BSD-2-Clause（见文件头） |
| 内置小游戏《寂零快跑》 | `app/src/main/assets/minigame/**` | 本项目原创 | 随本仓库 GPL-3.0 |
| AndroidX / Material Components | Gradle 依赖 | Google / AOSP | Apache-2.0 |
| Shizuku API | Gradle 依赖（`dev.rikka.shizuku`） | RikkaApps/Shizuku | 见上游仓库 / see upstream |

## three.js 的 MIT 声明（必须保留）

```
three.js
Copyright © 2010-2021 three.js authors
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## 与 Mojang / Microsoft / 网易的关系

本项目**不是**官方产品，与 Mojang Studios、Microsoft、网易雷火均无隶属或背书关系。
Minecraft 相关商标与素材归各自权利人所有；请仅对你**合法拥有**的存档使用本工具，
并遵守对应版本的最终用户许可协议（EULA）。
