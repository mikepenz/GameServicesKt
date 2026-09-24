#include <jni.h>
#include <dlfcn.h>
#include <cstdint>

namespace {
using Callback = void (*)(void*, int, const char*);
using Open = void* (*)(JavaVM*, jobject);
using Close = void (*)(void*);
using Request = void (*)(void*, int, const char*, int, void*, Callback);

struct Api {
    void* library = dlopen("libgs_play_validation.so", RTLD_NOW);
    Open open = library ? reinterpret_cast<Open>(dlsym(library, "gs_validation_open")) : nullptr;
    Close close = library ? reinterpret_cast<Close>(dlsym(library, "gs_validation_close")) : nullptr;
    Request request = library ? reinterpret_cast<Request>(dlsym(library, "gs_validation_request")) : nullptr;
};

Api& api() { static Api value; return value; }
struct Session { JavaVM* vm; jobject activity; void* backend; };
struct Pending { JavaVM* vm; jobject activity; };

void complete(void* context, int success, const char* message) {
    auto* pending = static_cast<Pending*>(context);
    JavaVM* vm = pending->vm;
    JNIEnv* env = nullptr;
    const bool attached = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK;
    if (attached && vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) { delete pending; return; }
    jclass type = env->GetObjectClass(pending->activity);
    jmethodID method = env->GetMethodID(type, "onNativeResult", "(ZLjava/lang/String;)V");
    jstring text = env->NewStringUTF(message);
    env->CallVoidMethod(pending->activity, method, success == 1, text);
    env->DeleteLocalRef(text);
    env->DeleteLocalRef(type);
    env->DeleteGlobalRef(pending->activity);
    delete pending;
    if (attached) vm->DetachCurrentThread();
}

void fail(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(type, message);
    env->DeleteLocalRef(type);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mikepenz_gameservices_sample_host_NativeValidationActivity_nativeOpen(JNIEnv* env, jobject activity) {
    auto& native = api();
    if (!native.open || !native.close || !native.request) {
        fail(env, "Native validation library is unavailable");
        return 0;
    }
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK) {
        fail(env, "Could not access JavaVM");
        return 0;
    }
    jobject global = env->NewGlobalRef(activity);
    void* backend = native.open(vm, global);
    if (!backend) {
        env->DeleteGlobalRef(global);
        fail(env, "Could not initialize Play Games native backend");
        return 0;
    }
    return reinterpret_cast<jlong>(new Session{vm, global, backend});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikepenz_gameservices_sample_host_NativeValidationActivity_nativeClose(JNIEnv* env, jobject, jlong value) {
    auto* session = reinterpret_cast<Session*>(value);
    if (!session) return;
    api().close(session->backend);
    env->DeleteGlobalRef(session->activity);
    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikepenz_gameservices_sample_host_NativeValidationActivity_nativeRequest(
    JNIEnv* env, jobject, jlong value, jint operation, jstring achievement, jint progress) {
    auto* session = reinterpret_cast<Session*>(value);
    if (!session) return;
    const char* id = env->GetStringUTFChars(achievement, nullptr);
    auto* pending = new Pending{session->vm, env->NewGlobalRef(session->activity)};
    api().request(session->backend, operation, id, progress, pending, complete);
    env->ReleaseStringUTFChars(achievement, id);
}
