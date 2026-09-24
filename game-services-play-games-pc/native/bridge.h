#pragma once
#ifdef _WIN32
#define GS_EXPORT __declspec(dllexport)
#else
#define GS_EXPORT
#endif
#ifdef __cplusplus
extern "C" {
#endif
typedef void (*gs_pc_callback)(void* context, int stage, int code, const char* session);
GS_EXPORT void* gs_pc_create(void);
GS_EXPORT void gs_pc_close(void* handle);
GS_EXPORT void gs_pc_request(void* handle, int operation, void* context, gs_pc_callback callback);
#ifdef __cplusplus
}
#endif
