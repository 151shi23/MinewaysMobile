/*
  M2-5 · 导出诊断模块（移植适配层新增，不属于核心算法）。

  目的：把"导出完成但一个文件都没有（files=0 / err=512）"从沉默的假成功，
  变成一段人能读懂、能直接粘贴反馈的报告：
    - 世界目录里到底有什么（level.dat 是否可解析、region/db 目录、每个 region 文件的区块数）
    - 区块是哪种压缩（核心只认 zlib=2）、能否解压
    - 用户填的选区覆盖了多少区块、其中磁盘上真实存在多少个
    - 用核心自己的读档函数试读**选区内的**区块，并统计"落在选区内的非空气方块数"
    - 结合上面的证据给出"最可能原因 + 该怎么改"的提示

  约束：本模块只读扫描，不修改任何核心状态、不调用核心算法（探针只调用其公开读档函数），
        与 jni_export.cpp 共用。
*/
#pragma once

#include <string>
#include <vector>

namespace ExportDiag {

// 单个 region 文件的结构体检结果
struct RegionFileInfo {
    std::string path;                       // 相对世界目录，如 region/r.0.0.mca
    long long size = 0;                     // 字节数
    int chunks = 0;                         // 偏移表中非空槽位数
    int compression[8] = {0, 0, 0, 0, 0, 0, 0, 0};  // 索引即压缩类型：1=gzip 2=zlib 3=未压缩 4=LZ4
    int tested = 0;                         // 实际抽样解压的区块数
    int inflateOk = 0;
    int inflateFail = 0;
    int badEntry = 0;                       // 长度/扇区越界等结构性错误
    std::string note;                       // 首个具体问题
};

// 整世界体检汇总
struct WorldScan {
    std::string worldDir;                   // UTF-8 路径
    bool levelDatOk = false;
    std::string levelDatNote;               // level.dat 异常说明
    int dataVersion = 0;
    int releaseNumber = 0;                  // MC 主版本号（21 表示 1.21.x）
    int spawnX = 0, spawnY = 0, spawnZ = 0;

    bool hasRegionDir = false;              // <world>/region 是否存在
    bool hasDbDir = false;                  // <world>/db 是否存在（基岩版存档特征）
    bool hasDimensionsDir = false;          // <world>/dimensions 是否存在
    int extraRegionDirs = 0;                // DIM1/DIM-1/dimensions 下的 region 目录数

    int regionFiles = 0;
    int totalChunks = 0;
    int testedChunks = 0, inflateOk = 0, inflateFail = 0, badEntries = 0;
    int compression[8] = {0, 0, 0, 0, 0, 0, 0, 0};

    bool haveChunkBounds = false;
    int minChunkX = 0, maxChunkX = 0, minChunkZ = 0, maxChunkZ = 0;

    std::string fileLines;                  // 每个 region 文件一行（含省略提示）
    std::vector<std::string> problems;      // 具体问题清单（最多 8 条）

    // 选区统计（由 scanSelection 填充）
    int coveredChunks = 0;                  // 选区覆盖的区块坐标数（超上限时截断）
    int presentChunks = 0;                  // 其中磁盘上真实存在（非空槽位）的区块数
    bool selectionCapped = false;
};

// 扫描世界目录：region/ 以及 DIM1/DIM-1/dimensions/*/*/region
// dataVersion / releaseNumber / 出生点由调用方（JNI，已解析 level.dat）传入，避免重复解析。
void scanWorld(const wchar_t* worldDir, int dataVersion, int releaseNumber,
               int spawnX, int spawnY, int spawnZ, WorldScan& out);

// 统计选区（方块坐标，含端点）覆盖与命中的区块数；须在 scanWorld 之后调用。
void scanSelection(const wchar_t* worldDir, int minx, int minz, int maxx, int maxz,
                   WorldScan& out);

// 错误码（MW_* 位掩码）→ 首个名称 / 多行中文说明
std::string codeName(int code);
std::string describeErrorCode(int code);

// 常见错误的"短提示"：把失败原因压成 2~4 行能直接照做的中文（不再需要翻超长报告）。
//   scan      : scanWorld 的结果（level.dat / region / 压缩类型 / 选区命中）
//   errCode   : SaveVolume 返回码；files: 写出的文件数（0 表示没产出）
//   sel*      : 本次选区方块坐标（用于给出"把 X/Z/Y 改成 …"的具体建议）
//   probeText : probeCoreRead 的结果（从中取"选区内非空气方块合计"与"方块 Y 实际范围"）
// 返回每行以 "  • " 开头、以 \n 结尾的 UTF-8 文本；无法判断时返回空串。
std::string buildHints(const WorldScan& scan, int errCode, int files,
                       int selMinX, int selMinY, int selMinZ,
                       int selMaxX, int selMaxY, int selMaxZ,
                       const std::string& probeText);

// 用核心自己的读档函数（regionGetBlocks）试读**选区内的**区块，回报：
//   返回码、全高非空气方块数、**落在选区内的非空气方块数**、方块实际所在 Y 范围、
//   首个非空气方块坐标与内部编号。
// 用来把"导出 0 方块"拆成三层：解析失败 / 选区内真的没有方块（多半是 Y 填错）/ 渲染筛选层问题。
std::string probeCoreRead(const std::wstring& worldDir, int mcVersion, int minHeight, int maxHeight,
                          int selMinX, int selMinY, int selMinZ,
                          int selMaxX, int selMaxY, int selMaxZ);

// UTF-32(wchar_t) → UTF-8
std::string toUtf8(const std::wstring& s);

// 读取核心写出的 OBJ 注释"回执"（文件开头连续的 # 行）。
// 核心写 OBJ 时会把自己的生效状态写成注释，例如：
//   # Export no materials / # Export individual textures
//   # Culling scheme: Standard
//   # Set 3D print type: Wavefront OBJ absolute indices
//   # block_scale: 0.002 / # Tree leaves solid: YES / # Hollow out bottom ...
// 把这些回显到报告里，就能直接核对界面开关是否真的到达核心（无需依赖肉眼比对模型）。
// maxLines<=0 表示不限；返回每行已缩进的 UTF-8 文本，读不到时返回空串。
std::string readObjReceipt(const std::string& objPathUtf8, int maxLines);

// 只读扫描导出的 OBJ，回报：顶点/面数、usemtl 材质列表（按使用次数排序）、o/g 对象行数，
// 以及"名字里含 glass 的材质"是否出现。用来直接回答"某个方块到底有没有写进模型"：
//   名字在 → 已导出（看不到就是显示/材质/透明度一侧）；名字不在 → 被过滤/剔除/不在选区。
std::string summarizeObjContent(const std::string& objPathUtf8, int maxNames);

// OBJ 对等性自检：扫描 OBJ 里所有 v 行的包围盒，与 OBJ 注释头中**核心自己声明**的
// `# block dimensions: X=.. by Y=.. by Z=.. blocks` / 顶点面数 / `# block_scale:` 对照，
// 从而验证：① 1 单位 = 1 方块（没有被 3D 打印缩放污染）；② 模型没有超出选区；
// ③ 坐标轴方向是否符合 Mineways 约定（X 镜像），或已启用居中/旋转/Z-up 时说明跳过方向判定。
// 全部为经验测量，不依赖对核心代码的假设。
std::string verifyObjGeometry(const std::string& objPathUtf8,
                              int selMinX, int selMinY, int selMinZ,
                              int selMaxX, int selMaxY, int selMaxZ,
                              int rotateDeg, bool zUp, bool centered);

}  // namespace ExportDiag
