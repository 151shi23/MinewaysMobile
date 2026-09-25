/*
  culling.cpp —— M2 Android headless 移植：真实方块剔除（Culling Scheme）。

  源：CullingSchemes.cpp（Eric Haines, 2026）。函数体逐字保留，仅做编译/存储层适配：
  - isBlockCulled / applyCullingScheme / seedDefaultCulled 原样移植；
  - Windows 注册表持久化 (CullingManager) 改为文件持久化（androidSetCullingFilePath）；
  - 对话框/菜单 (doCullingSchemes / CullingSchemeEdit) 属纯 UI，不移植。
*/
#include "stdafx.h"
#include "CullingSchemes.h"
#include "blockInfo.h"	// BLOCK_BARRIER, BLOCK_STRUCTURE_VOID

#include <stdio.h>

// 快速剔除查找：gIsCulledByIndex[i]=BlockTranslations[i] 当前是否被剔除。由 applyCullingScheme() 填充。
static unsigned char gIsCulledByIndex[NUM_CULL_ENTRIES] = { 0 };
static bool gAnyCulled = false;

// 当前激活 scheme 的快照（去掉注册表，保留内存态；M5 再接入界面临时方案存储）
static CullingScheme gActiveScheme;
static bool gHasActiveScheme = false;

// 存储路径 shim（NULL = 仅在内存，不落盘）
static char gCullingFilePath[1024] = { 0 };

void androidSetCullingFilePath(const char* utf8Path)
{
    if (utf8Path)
        strncpy_s(gCullingFilePath, sizeof(gCullingFilePath), utf8Path, sizeof(gCullingFilePath) - 1);
    else
        gCullingFilePath[0] = '\0';
}

// 预置"默认总隐藏"集合：技术/功能性方块（barrier、structure_void），用户基本不想在地图/导出里看到。
// Standard scheme 也强制这些，即使没有激活任何用户 scheme 也不出现。
static void seedDefaultCulled(unsigned char* culled)
{
    int barrierIdx = blockTransIndexFor(BLOCK_BARRIER, 0);
    if (barrierIdx >= 0 && barrierIdx < NUM_CULL_ENTRIES) culled[barrierIdx] = 1;
    int voidIdx = blockTransIndexFor(BLOCK_STRUCTURE_VOID, 0);
    if (voidIdx >= 0 && voidIdx < NUM_CULL_ENTRIES) culled[voidIdx] = 1;
}

void applyCullingScheme(const unsigned char* culled)
{
    if (culled == NULL) {
        // "Standard"：无用户方案激活，但仍隐藏 always-cull 默认项
        memset(gIsCulledByIndex, 0, sizeof(gIsCulledByIndex));
        seedDefaultCulled(gIsCulledByIndex);
    } else {
        memcpy(gIsCulledByIndex, culled, NUM_CULL_ENTRIES);
    }
    gAnyCulled = false;
    for (int i = 0; i < NUM_CULL_ENTRIES; i++) {
        if (gIsCulledByIndex[i]) { gAnyCulled = true; break; }
    }
}

bool isBlockCulled(int type, int dataVal)
{
    if (!gAnyCulled) return false;
    int idx = blockTransIndexFor(type, dataVal);
    // 越界不剔除
    if (idx < 0 || idx >= NUM_CULL_ENTRIES)
        return false;
    return gIsCulledByIndex[idx] != 0;
}

// 在"标准"（applyCullingScheme(NULL) 已 seed barrier/void）基础上追加"强剔除"的额外隐藏项。
// 仅隐藏仅供结构生成的工程方块（structure_block），不影响任何玩家可见方块。
void seedExtraCulled()
{
    // structure_block (255)：仅用于结构文件定位，模型里通常不该出现。
    int sbIdx = blockTransIndexFor(BLOCK_STRUCTURE_BLOCK, 0);
    if (sbIdx >= 0 && sbIdx < NUM_CULL_ENTRIES) gIsCulledByIndex[sbIdx] = 1;
    gAnyCulled = true;
}

// =============================================================================================
// 持久化 (CullingManager 核心) —— 注册表 -> 文件
// =============================================================================================

// 与桌面版一致：所有 culled=0，并预置 barrier/structure_void 为隐藏
void cullingManagerInit(CullingScheme* cs)
{
    memset(cs->culled, 0, sizeof(cs->culled));
    seedDefaultCulled(cs->culled);
}

void cullingManagerSave(CullingScheme* cs)
{
    if (gCullingFilePath[0])
    {
        FILE* f = fopen(gCullingFilePath, "wb");
        if (f)
        {
            fwrite(cs, sizeof(CullingScheme), 1, f);
            fclose(f);
        }
    }
    else
    {
        gActiveScheme = *cs;       // 内存态兜底
        gHasActiveScheme = true;
    }
}

void cullingManagerLoad(CullingScheme* cs)
{
    memset(cs, 0, sizeof(CullingScheme));
    cullingManagerInit(cs);        // 先给正确的默认（含 barrier/void）
    if (gCullingFilePath[0])
    {
        FILE* f = fopen(gCullingFilePath, "rb");
        if (f)
        {
            size_t r = fread(cs, sizeof(CullingScheme), 1, f);
            fclose(f);
            if (r == 1)
                return;            // 读到了有效方案
            memset(cs, 0, sizeof(CullingScheme));
            cullingManagerInit(cs);
        }
    }
    else if (gHasActiveScheme)
    {
        *cs = gActiveScheme;
    }
}