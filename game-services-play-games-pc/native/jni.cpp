#include "bridge.h"
#include <jni.h>
#include <cstring>
#include <memory>
struct Request { JavaVM* vm; jclass bindings; jmethodID complete; jlong id; };
static void complete(void* context, int stage, int code, const char* data) {
    std::unique_ptr<Request> request(static_cast<Request*>(context));
    JNIEnv* env = nullptr;
    bool attached = request->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED;
    if (attached && request->vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return;
    auto size = static_cast<jsize>(std::strlen(data));
    jbyteArray bytes = env->NewByteArray(size);
    if (bytes) {
        env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte*>(data));
        env->CallStaticVoidMethod(request->bindings, request->complete, request->id, stage, code, bytes);
        env->DeleteLocalRef(bytes);
    }
    env->DeleteGlobalRef(request->bindings);
    if (attached) request->vm->DetachCurrentThread();
}
extern "C" JNIEXPORT jlong JNICALL Java_com_mikepenz_gameservices_playgames_pc_PcBindings_create(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(gs_pc_create());
}
extern "C" JNIEXPORT void JNICALL Java_com_mikepenz_gameservices_playgames_pc_PcBindings_close(JNIEnv*, jclass, jlong handle) {
    gs_pc_close(reinterpret_cast<void*>(handle));
}
extern "C" JNIEXPORT void JNICALL Java_com_mikepenz_gameservices_playgames_pc_PcBindings_request(JNIEnv* env, jclass bindings, jlong handle, jlong id, jint operation) {
    auto request = std::make_unique<Request>();
    env->GetJavaVM(&request->vm);
    request->bindings = static_cast<jclass>(env->NewGlobalRef(bindings));
    request->complete = env->GetStaticMethodID(bindings, "complete", "(JII[B)V");
    request->id = id;
    if (!request->bindings || !request->complete) {
        if (request->bindings) env->DeleteGlobalRef(request->bindings);
        return;
    }
    gs_pc_request(reinterpret_cast<void*>(handle), operation, request.release(), complete);
}
