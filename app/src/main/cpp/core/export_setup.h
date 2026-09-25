/*
  M2-4 共享导出装配单元。
  把 JNI 入口(exportWorld)与宿主测试(Host main)共用的、纯"调用前铺状态"的 UI/适配逻辑集中于此，
  避免两份实现漂移。核心算法仍在 ObjFileManip/SaveVolume 原样，这里只是复刻桌面
  initializeViewExportData / setHeightsFromVersionID / exportVolume 的默认赋值，属于"不改算法"的适配。
*/
#pragma once
#include "stdafx.h"

// 世界版本 → 高度区间（复刻桌面 setHeightsFromVersionID）
void setHeightsFromVersionId(int versionId, int mcVersion, int& minH, int& maxH);

// 复刻桌面 initializeViewExportData 的 OBJ 全纹理默认 view（ExportFileData）
void initViewExportData(ExportFileData& efd);

// 装配 Options：worldType、saveFilterFlags、exportFlags（复刻 exportVolume 的 OBJ 全纹理 flag 段）
// fileType 用于判定是否走 OBJ 分离纹理路径
void assembleOptions(Options& opt, ExportFileData& efd, int fileType);

// 本地 FileList 初始化/释放（复刻桌面 initializeOutputFileList / free）
void initOutputFileList(FileList& list);
void freeOutputFileList(FileList& list);

// 进度回调（可空）：SaveVolume 内部做 *gpCallback 判空；此处提供"总是可调用"的空回调。
void androidProgressCallback(float progress, wchar_t* buf);