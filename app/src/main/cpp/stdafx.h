/*
  Android 端口版 stdafx.h —— 替代桌面版 stdafx.h（其无条件 #include <windows.h>）。
  这里只提供纯平台上需要的宏与 POSIX 文件 I/O 映射，
  并只引入本次编译必要的头部（nbt/region/blockInfo），
  避免拖入 MinewaysMap/ObjFileManip/terrainExtData 等大依赖。
*/
#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <limits.h>
#include <wchar.h>

/* min/max 只在 Android(NDK libc++)下用宏（libc++ 的 <string>/<vector> 不含 <bits/stl_algobase.h>，宏安全）。
   宿主机(libstdc++)下若#define min/max，会展开 std::min(a,b) 等系统模板导致编译失败 → 改用 std::min/std::max。
   clamp/swapint 非 STL 名，两平台统一用宏。 */
#if defined(__ANDROID__)
#ifndef max
#define max(a,b)            (((a) > (b)) ? (a) : (b))
#endif
#ifndef min
#define min(a,b)            (((a) < (b)) ? (a) : (b))
#endif
#else
#include <algorithm>
using std::min;
using std::max;
#endif
#ifndef clamp
#define clamp(a,lo,hi)      ((a) < (lo) ? (lo) : ((a) > (hi) ? (hi) : (a)))
#endif
#ifndef swapint
#define swapint(a,b)        { int tempint = (a); (a) = (b); (b) = tempint; }
#endif

/* fopen 失败返回 NULL */
#define INVALID_HANDLE_VALUE NULL

#define strncpy_s(d,dSize,s,n) strncpy((d),(s),(n))
#define sprintf_s snprintf
/* strcpy_s 同时支持 2 参(dst,src) 与 3 参(dst,size,src) 两种调用 */
#define strcpy_s_pick(_1,_2,_3,NAME,...) NAME
#define strcpy_s_2(dst,src)    strcpy((dst),(src))
#define strcpy_s_3(dst,sz,src) strcpy((dst),(src))
#define strcpy_s(...) strcpy_s_pick(__VA_ARGS__, strcpy_s_3, strcpy_s_2)(__VA_ARGS__)
#define strcat_s(d,sz,s)       strcat((d),(s))
#define _strdup strdup
#define _fileno fileno

/* 桌面 qsort_s(base,n,size,cmp,ctx)；定义在 android_port.cpp（全局 ctx + 标准 qsort 适配，导出单线程调用） */
#include <stdlib.h>
void android_qsort_s(void* base, size_t nmemb, size_t size, int (*cmp)(void*, const void*, const void*), void* ctx);
#define qsort_s(b,n,s,c,cx) android_qsort_s((b),(n),(s),(c),(cx))

/* 来自桌面 windows.h 的常量/宏与类型 */
#define MAX_PATH 260
#define MAX_PATH_AND_FILE (2*MAX_PATH)
typedef FILE* HANDLE;              /* 本移植几乎只把 HANDLE 当文件句柄用 */
typedef unsigned long DWORD;
typedef wchar_t WCHAR;
typedef bool boolean;
typedef int errno_t;
#define wcscpy_s(d,sz,s)    wcscpy((d),(s))
#define wcscat_s(d,sz,s)    wcscat((d),(s))
#define wcsncpy_s(d,dSz,s,n) wcsncpy((d),(s),(n))
#define _wfopen_s(fp,fn,m)  (android_wfopen_s((fp),(fn),(m)))
int android_swprintf_s(wchar_t* dst, size_t dstSize, const wchar_t* fmt, ...);
/* MSVC 的 swprintf_s 里 %s=%宽字符串；glibc/NDK 的 swprintf 里 %s=narrow。android_swprintf_s 忠实复刻 MSVC。 */
#define swprintf_s(d,sz,fmt,...) android_swprintf_s((d),(sz),(fmt),##__VA_ARGS__)

/* PORTAFILE 统一为 FILE*；宽路径经 android_port 转 UTF-8 后 fopen（不改算法，只做编译层适配） */
#define PORTAFILE FILE*
FILE* android_open_read_w(const wchar_t* fn);
FILE* android_open_write_w(const wchar_t* fn);
FILE* android_open_append_w(const wchar_t* fn);
#define PortaOpen(fn)   android_open_read_w((fn))
#define PortaCreate(fn) android_open_write_w((fn))
#define PortaAppend(fn) android_open_append_w((fn))
#define PortaSeek(h,ofs) fseek((h),(ofs),SEEK_SET)
#define PortaRead(h,buf,len)  (fread((buf),(len),1,(h))!=1)
#define PortaWrite(h,buf,len) (fwrite((buf),(len),1,(h))!=1)
#define PortaClose(h) fclose((h))

/* 安卓端口状态机（实现见 android_port.cpp） */
bool androidCreateDirectoryW(const wchar_t* path, void* securityAttr);
int  android_wfopen_s(FILE** fp, const wchar_t* filename, const wchar_t* mode);

/* ---- 桌面 windows.h 残留符号 -> 安卓 shim（仅编译层，不改算法） ---- */
int  android_wcsicmp(const wchar_t* a, const wchar_t* b);
#define _wcsicmp(a,b) android_wcsicmp((a),(b))
#define ERROR_ALREADY_EXISTS 183L
static inline unsigned long android_getlasterror(void) { return 0; }   /* androidCreateDirectoryW 对 EEXIST 已视为成功 */
#define GetLastError() android_getlasterror()
#define CreateDirectoryW(p,a) androidCreateDirectoryW((p),(a))

/* 桌面 MinewaysMap.h 的 worldType 选项位（ObjFileManip 等用到），逐字保持 */
#define CAVEMODE            0x0001
#define HIDEOBSCURED        0x0002
#define DEPTHSHADING        0x0004
#define LIGHTING            0x0008
#define HELL                0x0010
#define ENDER               0x0020
#define SHOWALL             0x0080
#define BIOMES              0x0100
#define TRANSPARENT_WATER   0x0200
#define MAP_GRID            0x0400

/* ---- 桌面 MSVC 安全串/时间函数 -> 安卓 shim ---- */
#include <ctype.h>
#include <time.h>
#include <strings.h>   /* strcasecmp */
static inline void android_strlwr_s(char* s, size_t /*sz*/) { for (; *s; ++s) if (*s >= 'A' && *s <= 'Z') *s += ('a' - 'A'); }
#define _strlwr_s(d,sz)      android_strlwr_s((d),(sz))
#define _stricmp(a,b)        strcasecmp((a),(b))
typedef time_t __time32_t;
#define _time32(t)           time((t))
#define _localtime32_s(tm,t) (localtime_r((t),(tm)) ? 0 : -1)
#define asctime_s(buf,sz,tm) (asctime_r((tm),(buf)) ? 0 : -1)

/* ---- MultiByteToWideChar（仅 charToWchar 用 UTF-8→UTF-32），见 android_port.cpp ---- */
#define CP_UTF8 65001
#define MB_ERR_INVALID_CHARS 0x8
static inline void android_wcslwr_s(wchar_t* s, size_t /*sz*/) { for (; *s; ++s) if (*s >= L'A' && *s <= L'Z') *s += 32; }
#define _wcslwr_s(d,sz)        android_wcslwr_s((d),(sz))
int androidMultiByteToWideChar(int codepage, unsigned long flags, const char* mb, int mbLen, wchar_t* wc, int wcMax);
#define MultiByteToWideChar(cp,fl,mb,mbl,wc,wcm) androidMultiByteToWideChar((cp),(fl),(mb),(mbl),(wc),(wcm))

#include <unistd.h>

/* 与原 tiles.h 保持一致的保护性占位；若 tiles.h 已先包含则跳过 */
#ifndef TOTAL_TILES
#define VERTICAL_TILES 80
#define TOTAL_TILES (VERTICAL_TILES*16)
#endif

/* 仅保留核心需要的头 */
#include "nbt.h"
#include "region.h"
#include "blockInfo.h"
#include "biomes.h"
#include "cache.h"
#include "android_types.h"
#include "ObjFileManip.h"