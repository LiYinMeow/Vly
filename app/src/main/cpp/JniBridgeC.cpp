/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include <jni.h>
#include "JniBridgeC.hpp"
#include "LAppDelegate.hpp"
#include "LAppDefine.hpp"
#include "LAppPal.hpp"

using namespace Csm;

static JavaVM *g_JVM; // JavaVM is valid for all threads, so just save it globally
static jclass g_JniBridgeJavaClass;
static jmethodID g_LoadFileMethodId;
static jmethodID g_MoveTaskToBackMethodId;
static jmethodID g_PushLastData;

JNIEnv *GetEnv() {
    JNIEnv *env = NULL;
    g_JVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    return env;
}

// The VM calls JNI_OnLoad when the native library is loaded
jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_JVM = vm;

    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("date/liyin/vly/live2d/JniBridgeJava");
    g_JniBridgeJavaClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    g_LoadFileMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "LoadFile",
                                                "(Ljava/lang/String;)[B");
    g_MoveTaskToBackMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "MoveTaskToBack",
                                                      "()V");
    g_PushLastData = env->GetStaticMethodID(g_JniBridgeJavaClass, "PushLastData", "([B)V");
    return JNI_VERSION_1_6;
}

void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = GetEnv();
    env->DeleteGlobalRef(g_JniBridgeJavaClass);
}

char *JniBridgeC::LoadFileAsBytesFromJava(const char *filePath, unsigned int *outSize) {
    JNIEnv *env = GetEnv();

    // ファイルロード
    jbyteArray obj = (jbyteArray) env->CallStaticObjectMethod(g_JniBridgeJavaClass,
                                                              g_LoadFileMethodId,
                                                              env->NewStringUTF(filePath));
    *outSize = static_cast<unsigned int>(env->GetArrayLength(obj));

    char *buffer = new char[*outSize];
    env->GetByteArrayRegion(obj, 0, *outSize, reinterpret_cast<jbyte *>(buffer));

    return buffer;
}

void JniBridgeC::MoveTaskToBack() {
    JNIEnv *env = GetEnv();

    // アプリ終了
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_MoveTaskToBackMethodId, NULL);
}

void JniBridgeC::PushLastDataToJava(unsigned char *data, unsigned int size) {
    JNIEnv *env = GetEnv();
    jbyteArray array = env->NewByteArray((int) size);
    env->SetByteArrayRegion(array, 0, (int) size, reinterpret_cast<const jbyte *>(data));
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_PushLastData, array);
}

extern "C"
{
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnStart(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnStart();
}

JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnPause(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnPause();
}

JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnStop(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnStop();
}

JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnDestroy(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnDestroy();
}

JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnSurfaceCreated(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnSurfaceCreate();
}

JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnSurfaceChanged(JNIEnv *env, jclass type,
                                                                jint width, jint height) {
    LAppDelegate::GetInstance()->OnSurfaceChanged(width, height);
}

JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeOnDrawFrame(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->Run();
}
JNIEXPORT jstring JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeGetL2DName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(LAppDefine::ModelName.c_str());
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeSetFace(JNIEnv *env, jclass clazz, jstring name) {
    jboolean isCopy;
    const char *cname = env->GetStringUTFChars(name, &isCopy);
    LAppDelegate::GetInstance()->SetExpression(std::string(cname));
}

JNIEXPORT jobjectArray JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeGetFaceList(JNIEnv *env, jclass clazz) {
    std::vector<std::string> fname = LAppDelegate::GetInstance()->GetExpressionList();
    jobjectArray jresult = (jobjectArray) env->NewObjectArray(fname.size(),
                                                              env->FindClass("java/lang/String"),
                                                              env->NewStringUTF(""));
    for (int i = 0; i < fname.size(); i++) {
        env->SetObjectArrayElement(jresult, i, env->NewStringUTF(fname[i].c_str()));
    }
    return jresult;
}
JNIEXPORT jobjectArray JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeGetMotionList(JNIEnv *env,
                                                             jclass clazz) {
    std::vector<std::string> fname = LAppDelegate::GetInstance()->GetMotionList();
    jobjectArray jresult = (jobjectArray) env->NewObjectArray(fname.size(),
                                                              env->FindClass("java/lang/String"),
                                                              env->NewStringUTF(""));
    for (int i = 0; i < fname.size(); i++) {
        env->SetObjectArrayElement(jresult, i, env->NewStringUTF(fname[i].c_str()));
    }
    return jresult;
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativePlayAnimation(JNIEnv *env, jclass clazz,
                                                             jstring animation) {
    jboolean isCopy;
    const char *ani = env->GetStringUTFChars(animation, &isCopy);
    LAppDelegate::GetInstance()->PlayAnimation(std::string(ani));
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeStopAnimation(JNIEnv *env, jclass clazz) {
    LAppDelegate::GetInstance()->StopLastMotion();
}
JNIEXPORT jboolean JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_isLoaded(JNIEnv *env, jclass clazz) {
    return static_cast<jboolean>(LAppDelegate::GetInstance()->isActive());
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeSetModelJSONName(JNIEnv *env, jclass clazz,
                                                                jstring name) {
    jboolean isCopy;
    const char *cname = env->GetStringUTFChars(name, &isCopy);
    LAppDefine::ModelName = cname;
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeSetConfigMode(JNIEnv *env, jclass clazz,
                                                             jboolean is_config) {
    LAppDefine::configMode = static_cast<bool>(is_config);
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeSetModelX(JNIEnv *env, jclass clazz, jfloat x) {
    LAppDefine::centerModelX = x;
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeSetModelY(JNIEnv *env, jclass clazz, jfloat y) {
    LAppDefine::centerModelY = y;
}
JNIEXPORT void JNICALL
Java_date_liyin_vly_live2d_JniBridgeJava_nativeSetModelScale(JNIEnv *env, jclass clazz,
                                                             jfloat scale) {
    LAppDefine::scaleSize = scale;
    LAppPal::PrintLog("Scale: %lf", LAppDefine::scaleSize);
}
}
