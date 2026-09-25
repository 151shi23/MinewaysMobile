/*
  M2 Android headless 移植：替代桌面版 CullingSchemes.h。
  保留面剔除/方块剔除的真实核心行为（isBlockCulled + seedDefaultCulled + scheme 存储），
  仅 shim 掉纯 UI 附属（Windows 注册表持久化、CommDlg 对话框、菜单枚举）。

  与桌面版差异（全部为编译/存储层适配，不改算法）：
  - registry(注册表) 持久化  ->  culling_schemes.bin 文件（android_port 提供目录）
  - doCullingSchemes/对话框   ->  不移植（Android 端 UI 在后续里程碑做）
  - 其余 isBlockCulled 剔除判定逐字保留。
*/
#pragma once
#include "nbt.h"

#ifdef __cplusplus
extern "C" {
#endif

#define NUM_CULL_ENTRIES    NUM_TRANS

typedef struct
{
    int id;
    wchar_t name[255];
    unsigned char culled[NUM_CULL_ENTRIES];	// per-BlockTranslations bool; 0=show, 1=hide
} CullingScheme;

// 与桌面版同语义：以 scheme 的 culled[] 重建快速查找（NULL=Standard，仍强制隐藏 barrier/structure_void）
void applyCullingScheme(const unsigned char* culled);

// 返回 (type, dataVal) 是否应按当前激活 scheme 剔除（0 开销分支）
bool isBlockCulled(int type, int dataVal);

// 在"标准"方案基础上追加"强剔除"的额外隐藏项（structure_block 等仅供结构生成的工程方块）
void seedExtraCulled();

// M2 最小持久化：指定 scheme 存储文件路径（Android app 外部目录）；未设置时按内存空方案
void androidSetCullingFilePath(const char* utf8Path);

// 桌面版 CullingManager 的读/写核心，改用文件而非注册表（算法不变）
void cullingManagerInit(CullingScheme* cs);           // 全部 culled=0，预置 barrier/void
void cullingManagerSave(CullingScheme* cs);
void cullingManagerLoad(CullingScheme* cs);

#ifdef __cplusplus
}
#endif