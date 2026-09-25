#include "export_setup.h"
#include "CullingSchemes.h"   // applyCullingScheme

// ---------------- 本地 FileList 初始化（桌面 initializeOutputFileList 复刻） ----------------
void initOutputFileList(FileList& list) {
    list.count = 0;
    for (int i = 0; i < MAX_OUTPUT_FILES; i++) list.name[i] = NULL;
}
void freeOutputFileList(FileList& list) {
    for (int i = 0; i < list.count; i++) { if (list.name[i]) { free(list.name[i]); list.name[i] = NULL; } }
    list.count = 0;
}

void androidProgressCallback(float /*progress*/, wchar_t* /*buf*/) {
}

// ================= 桌面 setHeightsFromVersionID 复刻（版本→世界高度） =================
void setHeightsFromVersionId(int versionId, int mcVersion, int& minH, int& maxH) {
    maxH = MAX_WORLD_HEIGHT(versionId, mcVersion);
    minH = ZERO_WORLD_HEIGHT(versionId, mcVersion);
}

// ================= 桌面 initializeViewExportData 复刻（OBJ 全纹理默认导出数据） =================
// 为"一个都不能改"原则下的妥协：ExportFileData 是 UI 附属数据，非核心算法，
// 这里复刻桌面 view 默认值（OBJ=0 绝对坐标、全纹理、tile 输出），保证导出行为与桌面一致。
#define ANDROID_EFD_INIT_ALL(exp, list)  \
    { for (int fi = 0; fi < FILE_TYPE_TOTAL; fi++) (exp)[fi] = (list)[fi]; }

void initViewExportData(ExportFileData& efd) {
    memset(&efd, 0, sizeof(ExportFileData));
    efd.fileType = FILE_TYPE_WAVEFRONT_ABS_OBJ;

    const int createZip[FILE_TYPE_TOTAL]           = {0,0,0,0,0,0,0,0,0};
    const int createModelFiles[FILE_TYPE_TOTAL]    = {1,1,1,1,1,1,1,1,1};
    const int radioNoMtl[FILE_TYPE_TOTAL]          = {0,0,0,0,0,1,0,1,1};
    const int radioMtlCol[FILE_TYPE_TOTAL]         = {0,0,0,1,1,0,0,0,0};
    const int radioSolidTex[FILE_TYPE_TOTAL]       = {0,0,0,0,0,0,0,0,0};
    const int radioFullTex[FILE_TYPE_TOTAL]        = {0,0,0,0,0,0,1,0,0};
    const int radioTileTex[FILE_TYPE_TOTAL]        = {1,1,1,0,0,0,0,0,0};
    const int makeZUp[FILE_TYPE_TOTAL]             = {0,0,0,0,0,0,0,0,0};
    const int blockSize[FILE_TYPE_TOTAL]           = {1000,1000,1000,1000,1000,1000,1000,1000,1000};
    const int hollow[FILE_TYPE_TOTAL]              = {0,0,0,0,0,0,0,0,0};
    const int superHollow[FILE_TYPE_TOTAL]         = {0,0,0,0,0,0,0,0,0};
    const int indivBlocks[FILE_TYPE_TOTAL]         = {0,0,0,0,0,0,0,0,0};
    const int hollowThick[FILE_TYPE_TOTAL]         = {1000,1000,1000,1000,1000,1000,1000,1000,1000};
    const int customMtl[FILE_TYPE_TOTAL]           = {1,1,0,0,0,0,0,0,0};
    const int physMtl[FILE_TYPE_TOTAL]             = {PRINT_MATERIAL_FULL_COLOR_SANDSTONE,
                                                      PRINT_MATERIAL_FULL_COLOR_SANDSTONE,
                                                      PRINT_MATERIAL_FULL_COLOR_SANDSTONE,
                                                      PRINT_MATERIAL_CUSTOM_MATERIAL,
                                                      PRINT_MATERIAL_CUSTOM_MATERIAL,
                                                      PRINT_MATERIAL_CUSTOM_MATERIAL,
                                                      PRINT_MATERIAL_FULL_COLOR_SANDSTONE,
                                                      PRINT_MATERIAL_FULL_COLOR_SANDSTONE,
                                                      PRINT_MATERIAL_FULL_COLOR_SANDSTONE};
    const int modelUnits[FILE_TYPE_TOTAL]          = {UNITS_METER, UNITS_METER, UNITS_METER,
                                                      UNITS_MILLIMETER, UNITS_MILLIMETER, UNITS_MILLIMETER,
                                                      UNITS_METER, UNITS_METER, UNITS_METER};

    ANDROID_EFD_INIT_ALL(efd.chkCreateZip, createZip);
    ANDROID_EFD_INIT_ALL(efd.chkCreateModelFiles, createModelFiles);
    ANDROID_EFD_INIT_ALL(efd.radioExportNoMaterials, radioNoMtl);
    ANDROID_EFD_INIT_ALL(efd.radioExportMtlColors, radioMtlCol);
    ANDROID_EFD_INIT_ALL(efd.radioExportSolidTexture, radioSolidTex);
    ANDROID_EFD_INIT_ALL(efd.radioExportFullTexture, radioFullTex);
    ANDROID_EFD_INIT_ALL(efd.radioExportTileTextures, radioTileTex);
    ANDROID_EFD_INIT_ALL(efd.chkMakeZUp, makeZUp);
    ANDROID_EFD_INIT_ALL(efd.blockSizeVal, blockSize);
    ANDROID_EFD_INIT_ALL(efd.chkHollow, hollow);
    ANDROID_EFD_INIT_ALL(efd.chkSuperHollow, superHollow);
    ANDROID_EFD_INIT_ALL(efd.chkIndividualBlocks, indivBlocks);
    ANDROID_EFD_INIT_ALL(efd.hollowThicknessVal, hollowThick);
    ANDROID_EFD_INIT_ALL(efd.chkCustomMaterial, customMtl);
    ANDROID_EFD_INIT_ALL(efd.comboPhysicalMaterial, physMtl);
    ANDROID_EFD_INIT_ALL(efd.comboModelUnits, modelUnits);

    strcpy(efd.tileDirString, "tex");

    efd.chkTextureRGB = 1;
    efd.chkTextureA = 1;
    efd.chkTextureRGBA = 1;

    efd.chkExportAll = 1;
    efd.modelHeightVal = 100.0f;
    efd.costVal = 25.00f;

    efd.chkSealEntrances = 0;
    efd.chkSealSideTunnels = 0;
    efd.chkFillBubbles = 0;
    efd.chkConnectParts = 0;
    efd.chkConnectCornerTips = 0;
    efd.chkConnectAllEdges = 0;
    efd.chkDeleteFloaters = 0;
    efd.chkMeltSnow = 0;

    efd.chkSeparateTypes = 1;
    efd.chkMaterialPerFamily = 1;
    efd.chkSplitByBlockType = 1;
    efd.chkMakeGroupsObjects = 1;
    efd.chkCompositeOverlay = 0;
    efd.chkBlockFacesAtBorders = 1;
    efd.chkDecimate = 0;
    efd.chkLeavesSolid = 0;
    efd.chkExportMDL = 0;

    efd.scaleLightsVal = 15.0f;
    efd.scaleEmittersVal = 20.0f;
    efd.floaterCountVal = 16;
    efd.radioRotate0 = 1;
    efd.radioScaleByBlock = 1;
    efd.flags = 0x0;
}

void assembleOptions(Options& opt, ExportFileData& efd, int fileType) {
    memset(&opt, 0, sizeof(Options));
    opt.worldType = SHOWALL;   // 叠加世界（含全部方块），与桌面 view 默认一致
    opt.pEFD = &efd;
    opt.saveFilterFlags = BLF_WHOLE | BLF_ALMOST_WHOLE | BLF_STAIRS | BLF_HALF | BLF_MIDDLER |
                          BLF_BILLBOARD | BLF_PANE | BLF_FLATTEN | BLF_FLATTEN_SMALL |
                          BLF_SMALL_MIDDLER | BLF_SMALL_BILLBOARD;   // chkExportAll=1 的渲染默认

    // exportFlags 装配（复刻 exportVolume 的 OBJ 路径）
    // 材质/纹理模式按导出选项装配；默认分支（radioExportTileTextures）与旧行为逐位一致：
    //   MATERIALS | TEXTURE_IMAGES | SEPARATE_TEXTURE_TILES | MTL_PER_TYPE
    opt.exportFlags = EXPT_OUTPUT_OBJ_MTL_PER_TYPE;
    if (efd.radioExportNoMaterials[fileType]) {
        // 不导出材质：不写 .mtl，也不写贴图
    } else if (efd.radioExportMtlColors[fileType]) {
        opt.exportFlags |= EXPT_OUTPUT_MATERIALS;                                   // 仅实体材质颜色
    } else if (efd.radioExportSolidTexture[fileType]) {
        opt.exportFlags |= EXPT_OUTPUT_MATERIALS | EXPT_OUTPUT_TEXTURE_SWATCHES;    // 带颜色噪点纹理
    } else if (efd.radioExportFullTexture[fileType]) {
        opt.exportFlags |= EXPT_OUTPUT_MATERIALS | EXPT_OUTPUT_TEXTURE_IMAGES;      // 三幅整幅大图
    } else {
        opt.exportFlags |= EXPT_OUTPUT_MATERIALS | EXPT_OUTPUT_TEXTURE_IMAGES | EXPT_OUTPUT_SEPARATE_TEXTURE_TILES;
    }
    if (fileType == FILE_TYPE_WAVEFRONT_ABS_OBJ || fileType == FILE_TYPE_WAVEFRONT_REL_OBJ)
    {
        if (efd.chkSeparateTypes) {
            opt.exportFlags |= EXPT_OUTPUT_OBJ_SEPARATE_TYPES | EXPT_OUTPUT_OBJ_MATERIAL_PER_BLOCK;
        }
        if (efd.chkSplitByBlockType) opt.exportFlags |= EXPT_OUTPUT_OBJ_SPLIT_BY_BLOCK_TYPE;
        if (efd.chkMakeGroupsObjects) opt.exportFlags |= EXPT_OUTPUT_OBJ_MAKE_GROUPS_OBJECTS;
        if (efd.chkCustomMaterial[fileType]) opt.exportFlags |= EXPT_OUTPUT_CUSTOM_MATERIAL;
        if (fileType == FILE_TYPE_WAVEFRONT_REL_OBJ) opt.exportFlags |= EXPT_OUTPUT_OBJ_REL_COORDINATES;
        if (efd.radioExportTileTextures[fileType]) opt.exportFlags |= EXPT_OUTPUT_SEPARATE_TEXTURE_TILES;
    }

    // ---- 3D 打印 / 结构 / 分块选项 → EXPT 标志（复刻桌面 exportVolume 的装配段）----
    // 注意：这些以前只填进了 efd，没转成 EXPT 标志，于是"焊接所有共享边""掏空""封闭入口""连接零件"
    // 等选项在界面上勾了也不生效。此处补齐，全部默认 0/未命中时逐位与旧行为一致。
    if (efd.chkHollow[fileType])           opt.exportFlags |= EXPT_HOLLOW_BOTTOM;
    if (efd.chkSuperHollow[fileType])      opt.exportFlags |= EXPT_SUPER_HOLLOW_BOTTOM;
    if (efd.chkSealEntrances)              opt.exportFlags |= EXPT_SEAL_ENTRANCES;
    if (efd.chkSealSideTunnels)            opt.exportFlags |= EXPT_SEAL_SIDE_TUNNELS;
    if (efd.chkFillBubbles)                opt.exportFlags |= EXPT_FILL_BUBBLES;
    if (efd.chkConnectParts)               opt.exportFlags |= EXPT_CONNECT_PARTS;
    if (efd.chkConnectCornerTips)          opt.exportFlags |= EXPT_CONNECT_CORNER_TIPS;
    if (efd.chkConnectAllEdges)            opt.exportFlags |= EXPT_CONNECT_ALL_EDGES;
    if (efd.chkDeleteFloaters)             opt.exportFlags |= EXPT_DELETE_FLOATING_OBJECTS;
    if (efd.chkIndividualBlocks[fileType]) opt.exportFlags |= EXPT_INDIVIDUAL_BLOCKS | EXPT_OUTPUT_EACH_BLOCK_A_GROUP;
    if (efd.chkBiome)                      opt.exportFlags |= EXPT_BIOME;
    if (efd.chkExportMDL)                  opt.exportFlags |= EXPT_EXPORT_MDL;
    // "每个系列的材料"关掉时，撤掉按块材质（这是该选项的唯一作用）
    if (!efd.chkMaterialPerFamily)         opt.exportFlags &= ~EXPT_OUTPUT_OBJ_MATERIAL_PER_BLOCK;

    // 用了任一"3D 打印结构"选项即按水密模型处理（等价桌面的 3D printing 模式）：
    // 内核多处会据此调整边界块面与切面处理（见 ObjFileManip.cpp 的 gModel.print3D 分支）。
    if (efd.chkHollow[fileType] || efd.chkSuperHollow[fileType] || efd.chkSealEntrances ||
        efd.chkSealSideTunnels || efd.chkFillBubbles || efd.chkConnectParts ||
        efd.chkConnectCornerTips || efd.chkConnectAllEdges || efd.chkDeleteFloaters ||
        efd.chkMeltSnow) {
        opt.exportFlags |= EXPT_3DPRINT;
    }
}