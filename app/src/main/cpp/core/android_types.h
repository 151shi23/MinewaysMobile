/*
  M2 Android 端口类型头。
  桌面端这些类型在 MinewaysMap.h / Mineways.h 中，而那两个头含 Windows/GUI 依赖。
  这里只复刻核心（ObjFileManip / SaveVolume）实际用到的类型，保持定义与桌面一致。
*/
#pragma once

typedef void (*ProgressCallback)(float progress, wchar_t* buf);

#define WORLD_UNLOADED_TYPE   0
#define WORLD_LEVEL_TYPE      1
#define WORLD_TEST_BLOCK_TYPE 2
#define WORLD_SCHEMATIC_TYPE  3
#define EMPTY_HEIGHT          (-999)

typedef struct Schematic {
    unsigned char* blocks;
    unsigned short* data;
    int width;   // X
    int height;  // Y
    int length;  // Z
    int numBlocks;
    bool repeat;
    bool isSponge;
} Schematic;

typedef struct WorldGuide {
    unsigned int type;
    wchar_t world[520];
    wchar_t directory[520];
    Schematic sch;
    int nbtVersion;
    bool isServerWorld;
    bool newFormat;
    int minHeight;
    int maxHeight;
} WorldGuide;

/* 原版该函数声明在 MinewaysMap.h，因我们不引入该 GUI 头，故在共享头声明；实现逐字在 mw_loadblock.cpp */
const char* RetrieveBlockSubname(int type, int dataVal);

/* 同样来自 MinewaysMap.h；ObjFileManip.cpp(SaveVolume) 需要，实现逐字在 mw_loadblock.cpp */
void SetDimensionDirectory(WorldGuide* pWorldGuide, unsigned int worldType);
/* 设置维度子目录前的路径分隔符（桌面 GUI 设 '\\'；安卓固定 '/'）；实现逐字在 mw_loadblock.cpp */
void SetSeparatorMap(const wchar_t* separator);
WorldBlock* LoadBlock(WorldGuide* pWorldGuide, int bx, int bz, int mcVersion, int versionID, int& retCode);

/* 导出配色与未知方块回退；实现逐字抽取在 mw_loadblock.cpp */
void SetUnknownBlockID(int val);
int  GetUnknownBlockID();
unsigned int GetBlockDataColor(int type, int dataVal);