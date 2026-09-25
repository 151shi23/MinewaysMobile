/*
  android_port.cpp —— 安卓平台的"port glue"，补齐桌面 windows.h 没有但核心用到的能力。
  M2 阶段内容：
    - android_wfopen_s : 桌面 _wfopen_s（宽字符路径打开文件），安卓 wchar_t=32bit，转 UTF-8 后 fopen
    - androidCreateDirectoryW : 桌面 CreateDirectoryW，转 UTF-8 后 mkdir（EEXIST 视为成功）
    - isBlockCulled : M2 剔除能力 stub（后续 M5 实现真正 scheme）
*/
#include "stdafx.h"
#include <string>
#include <errno.h>
#include <sys/stat.h>
#include <stdarg.h>

// wchar_t(安卓为 UTF-32) 转 UTF-8 字节串
static std::string wcharToUtf8(const wchar_t* ws) {
    std::string out;
    if (!ws) return out;
    for (const wchar_t* p = ws; *p; ++p) {
        unsigned int cp = (unsigned int)*p;
        if (cp < 0x80) {
            out += (char)cp;
        } else if (cp < 0x800) {
            out += (char)(0xC0 | (cp >> 6));
            out += (char)(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            out += (char)(0xE0 | (cp >> 12));
            out += (char)(0x80 | ((cp >> 6) & 0x3F));
            out += (char)(0x80 | (cp & 0x3F));
        } else {
            out += (char)(0xF0 | (cp >> 18));
            out += (char)(0x80 | ((cp >> 12) & 0x3F));
            out += (char)(0x80 | ((cp >> 6) & 0x3F));
            out += (char)(0x80 | (cp & 0x3F));
        }
    }
    return out;
}

int android_wfopen_s(FILE** fp, const wchar_t* filename, const wchar_t* mode) {
    if (!fp) return EINVAL;
    std::string path = wcharToUtf8(filename);
    std::string m    = wcharToUtf8(mode);
    *fp = fopen(path.c_str(), m.empty() ? "rb" : m.c_str());
    return (*fp != NULL) ? 0 : errno;
}

// MSVC 的 swprintf_s 里 %s/%c 指 "wide string/char"；glibc/NDK 的 swprintf 里 %s/%c 指 narrow。
// 本项目核心代码的 swprintf_s(L"...%s...", wchar_t*) 全部按 MSVC 语义传宽字符串。
// 此 shim 把 %s->%ls、%c->%lc 后调用 vswprintf，忠实复刻 MSVC 调用约定（纯编译层适配，不改算法）。
int android_swprintf_s(wchar_t* dst, size_t dstSize, const wchar_t* fmt, ...) {
    if (!dst || dstSize == 0) return -1;
    const size_t fmtsz = 4 + (fmt ? wcslen(fmt) : 0) * 2;
    wchar_t* newfmt = (wchar_t*)malloc((fmtsz + 1) * sizeof(wchar_t));
    if (!newfmt) return -1;
    int ni = 0;
    const wchar_t* p = fmt;
    if (!p) p = L"";
    while (*p) {
        if (*p != L'%') { newfmt[ni++] = *p++; continue; }
        // copy the whole conversion up to (and including) the type char
        newfmt[ni++] = *p++;       // '%'
        if (*p == L'%') { newfmt[ni++] = *p++; continue; }   // literal %
        // flags / width / precision / length, but stop right before type char
        bool hasL = false;
        while (*p && !wcschr(L"diouxXeEfFgGaAcspn", *p)) {
            if (*p == L'l' || *p == L'h' || *p == L'j' || *p == L'z' || *p == L't') hasL = (*p == L'l');
            newfmt[ni++] = *p++;
        }
        // now *p is the type char (or NUL); if bad format, bail
        if (!*p) { newfmt[ni++] = 0; break; }
        wchar_t t = *p;
        if (t == L's' && !hasL) { newfmt[ni++] = L'l'; }
        else if (t == L'c' && !hasL) { newfmt[ni++] = L'l'; }
        newfmt[ni++] = *p++;
    }
    newfmt[ni] = 0;
    va_list args;
    va_start(args, fmt);
    int ret = vswprintf(dst, dstSize, newfmt, args);
    va_end(args);
    free(newfmt);
    return ret;
}

bool androidCreateDirectoryW(const wchar_t* path, void* /*securityAttr*/) {
    std::string p = wcharToUtf8(path);
    if (p.empty()) return false;
    int r = mkdir(p.c_str(), 0755);
    return (r == 0) || (errno == EEXIST);
}

// PORTAFILE 宽的读写入口：宽路径 → UTF-8 → fopen
FILE* android_open_read_w(const wchar_t* fn) {
    std::string p = wcharToUtf8(fn);
    return p.empty() ? NULL : fopen(p.c_str(), "rb");
}
FILE* android_open_write_w(const wchar_t* fn) {
    std::string p = wcharToUtf8(fn);
    return p.empty() ? NULL : fopen(p.c_str(), "w");
}
FILE* android_open_append_w(const wchar_t* fn) {
    std::string p = wcharToUtf8(fn);
    return p.empty() ? NULL : fopen(p.c_str(), "a");
}

// 桌面 _wcsicmp：宽字符（UTF-32）不区分大小写比较
int android_wcsicmp(const wchar_t* a, const wchar_t* b) {
    if (!a || !b) return (a == b) ? 0 : (a ? 1 : -1);
    while (*a && *b) {
        wchar_t ca = (*a >= L'A' && *a <= L'Z') ? (*a + 32) : *a;
        wchar_t cb = (*b >= L'A' && *b <= L'Z') ? (*b + 32) : *b;
        if (ca != cb) return (ca < cb) ? -1 : 1;
        ++a; ++b;
    }
    if (*a == *b) return 0;
    return (*a) ? 1 : -1;
}

// 桌面 qsort_s：compare 为 (ctx,a,b) 形式且导出为单线程调用，用全局 ctx + 标准 qsort 适配。
typedef int(*AndroidQSortCmp)(void*, const void*, const void*);
static AndroidQSortCmp g_qsort_cmp;
static void* g_qsort_ctx;
static int android_qsort_adapter(const void* a, const void* b) {
    return g_qsort_cmp(g_qsort_ctx, a, b);
}
void android_qsort_s(void* base, size_t nmemb, size_t size, int (*cmp)(void*, const void*, const void*), void* ctx) {
    g_qsort_cmp = (AndroidQSortCmp)cmp;
    g_qsort_ctx = ctx;
    qsort(base, nmemb, size, android_qsort_adapter);
}

// 桌面 MultiByteToWideChar 的 UTF-8→UTF-32 最小实现（OBJ 导出里 charToWchar 需要）
int androidMultiByteToWideChar(int codepage, unsigned long /*flags*/, const char* mb, int mbLen, wchar_t* wc, int wcMax) {
    if (codepage != CP_UTF8 || !mb || !wc) return 0;
    if (mbLen < 0) mbLen = (int)strlen(mb);
    int out = 0;
    int i = 0;
    while (i < mbLen && out < wcMax - 1) {
        unsigned char c = (unsigned char)mb[i];
        unsigned cp;
        int len;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; len = 4; }
        else { return out; }   // invalid
        if (i + len > mbLen) return out;
        for (int k = 1; k < len; ++k) {
            unsigned char cc = (unsigned char)mb[i + k];
            if ((cc & 0xC0) != 0x80) return out;
            cp = (cp << 6) | (cc & 0x3F);
        }
        wc[out++] = (wchar_t)cp;
        i += len;
    }
    wc[out] = L'\0';
    return out;
}