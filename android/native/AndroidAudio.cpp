#include "Synth.h"
#include <aaudio/AAudio.h>
#include <jni.h>
#include <atomic>
struct Engine {
    Synth synth;
    AAudioStream* stream=nullptr;
    explicit Engine(const char* path):synth(path){}
    static aaudio_data_callback_result_t data(AAudioStream*,void* user,void* output,int32_t frames){
        auto& synth=static_cast<Engine*>(user)->synth;synth.render(static_cast<float*>(output),frames);return synth.ready()?AAUDIO_CALLBACK_RESULT_CONTINUE:AAUDIO_CALLBACK_RESULT_STOP;
    }
    static void error(AAudioStream*,void* user,aaudio_result_t){static_cast<Engine*>(user)->synth.invalidate();}
    bool start(){
        if(!synth.ready())return false;
        AAudioStreamBuilder* b=nullptr;
        if(AAudio_createStreamBuilder(&b)!=AAUDIO_OK)return false;
        AAudioStreamBuilder_setDirection(b,AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setFormat(b,AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(b,2);AAudioStreamBuilder_setSampleRate(b,48000);
        AAudioStreamBuilder_setPerformanceMode(b,AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(b,AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setDataCallback(b,data,this);AAudioStreamBuilder_setErrorCallback(b,error,this);
        auto result=AAudioStreamBuilder_openStream(b,&stream);AAudioStreamBuilder_delete(b);
        if(result!=AAUDIO_OK)return false;
        if(AAudioStream_getSampleRate(stream)!=48000 || AAudioStream_getChannelCount(stream)!=2 || AAudioStream_getFormat(stream)!=AAUDIO_FORMAT_PCM_FLOAT)return false;
        AAudioStream_setBufferSizeInFrames(stream,AAudioStream_getFramesPerBurst(stream)*2);
        return AAudioStream_requestStart(stream)==AAUDIO_OK;
    }
    ~Engine(){if(stream){AAudioStream_requestStop(stream);AAudioStream_close(stream);}}
};
#define JNI(name) Java_local_orange_z1androidtest_NativeInstrument_##name
extern "C" JNIEXPORT jlong JNICALL JNI(create)(JNIEnv* env,jclass,jstring path){
    const char* p=env->GetStringUTFChars(path,nullptr);if(!p)return 0;
    Engine* e=new Engine(p);env->ReleaseStringUTFChars(path,p);
    if(!e->start()){delete e;return 0;}return reinterpret_cast<jlong>(e);
}
extern "C" JNIEXPORT jboolean JNICALL JNI(send)(JNIEnv*,jclass,jlong h,jint type,jint ch,jint key,jint vel){auto e=reinterpret_cast<Engine*>(h);return e->synth.command(static_cast<Synth::Type>(type),ch,key,vel);}
extern "C" JNIEXPORT void JNICALL JNI(panic)(JNIEnv*,jclass,jlong h){reinterpret_cast<Engine*>(h)->synth.panic();}
extern "C" JNIEXPORT void JNICALL JNI(gain)(JNIEnv*,jclass,jlong h,jfloat g){reinterpret_cast<Engine*>(h)->synth.gain(g);}
extern "C" JNIEXPORT jboolean JNICALL JNI(healthy)(JNIEnv*,jclass,jlong h){return reinterpret_cast<Engine*>(h)->synth.ready();}
extern "C" JNIEXPORT void JNICALL JNI(dispose)(JNIEnv*,jclass,jlong h){delete reinterpret_cast<Engine*>(h);}
