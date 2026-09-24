#include <jni.h>
#include <string>
#include <cstring>
#include <memory>
extern "C" {
void* gs_gamecenter_create();
void gs_gamecenter_close(void*);
void gs_gamecenter_request(void*, const char*, const char*, void*, void (*)(void*, int, const char*));
}
struct Request { JavaVM* vm; jclass bindings; jmethodID complete; jlong id; };
static void complete(void* context, int ok, const char* data) {
    std::unique_ptr<Request> request(static_cast<Request*>(context));
    JNIEnv* env = nullptr;
    bool attached = request->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED;
    if (attached && request->vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return;
    auto size = static_cast<jsize>(std::strlen(data));
    jbyteArray bytes = env->NewByteArray(size);
    if (bytes) {
        env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte*>(data));
        env->CallStaticVoidMethod(request->bindings, request->complete, request->id, static_cast<jboolean>(ok), bytes);
        env->DeleteLocalRef(bytes);
    }
    env->DeleteGlobalRef(request->bindings);
    if (attached) request->vm->DetachCurrentThread();
}
static std::string utf8(JNIEnv* env, jbyteArray bytes) {
    std::string result(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()), reinterpret_cast<jbyte*>(result.data()));
    return result;
}
extern "C" JNIEXPORT jlong JNICALL Java_com_mikepenz_gameservices_gamecenter_NativeBindings_create(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(gs_gamecenter_create());
}
extern "C" JNIEXPORT void JNICALL Java_com_mikepenz_gameservices_gamecenter_NativeBindings_close(JNIEnv*, jclass, jlong handle) {
    gs_gamecenter_close(reinterpret_cast<void*>(handle));
}
extern "C" JNIEXPORT void JNICALL Java_com_mikepenz_gameservices_gamecenter_NativeBindings_request(JNIEnv* env, jclass bindings, jlong handle, jlong id, jbyteArray operation, jbyteArray arguments) {
    auto op = utf8(env, operation);
    auto args = utf8(env, arguments);
    if (env->ExceptionCheck()) return;
    auto request = std::make_unique<Request>();
    env->GetJavaVM(&request->vm);
    request->bindings = static_cast<jclass>(env->NewGlobalRef(bindings));
    request->complete = env->GetStaticMethodID(bindings, "complete", "(JZ[B)V");
    request->id = id;
    if (!request->bindings || !request->complete) {
        if (request->bindings) env->DeleteGlobalRef(request->bindings);
        return;
    }
    gs_gamecenter_request(reinterpret_cast<void*>(handle), op.c_str(), args.c_str(), request.release(), complete);
}
