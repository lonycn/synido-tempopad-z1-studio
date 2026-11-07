#ifdef SYNTH_TEST_ALLOC
#include <cstdlib>
#include <cassert>
static thread_local bool realtime=false;
static void* checkedMalloc(size_t n){assert(!realtime);return std::malloc(n);}
static void* checkedRealloc(void* p,size_t n){assert(!realtime);return std::realloc(p,n);}
static void checkedFree(void* p){assert(!realtime);std::free(p);}
#define TSF_MALLOC checkedMalloc
#define TSF_REALLOC checkedRealloc
#define TSF_FREE checkedFree
struct RealtimeScope { RealtimeScope(){realtime=true;} ~RealtimeScope(){realtime=false;} };
#endif
#ifdef SYNTH_TEST_POISON_INACTIVE
#include <sanitizer/asan_interface.h>
#endif
#define TSF_IMPLEMENTATION
#include "../vendor/tinysoundfont/tsf.h"
#include "Synth.h"
#include <cmath>
#include <algorithm>
Synth::Synth(const char* path) {
    font=tsf_load_filename(path);
    if(!font)return;
    tsf_set_output(font,TSF_STEREO_INTERLEAVED,48000,-10);
    if(!tsf_set_max_voices(font,128) || !tsf_channel_set_bank_preset(font,0,0,0) ||
       !tsf_channel_set_bank_preset(font,1,128,0)) { tsf_close(font);font=nullptr; }
}
Synth::~Synth(){if(font)tsf_close(font);}
bool Synth::command(Type type,int channel,int key,int velocity) {
    if(!ready() || channel<0 || channel>1 || key<0 || key>127 || velocity<0 || velocity>127)return false;
    auto w=write.load(std::memory_order_relaxed);
    if(w-read.load(std::memory_order_acquire)>=Capacity){panic();return false;}
    queue[w%Capacity]={type,channel,key,velocity,epoch.load(std::memory_order_acquire)};
    write.store(w+1,std::memory_order_release);return ready();
}
void Synth::silence(){
    // Pinned TSF's sounds_off_all has a short release. Panic requires exact silence.
    // All synth state access is callback-owned after initialization.
    for(int i=0;i<font->voiceNum;i++)font->voices[i].playingPreset=-1;
    tsf_channel_set_sustain(font,0,0);tsf_channel_set_sustain(font,1,0);
}
void Synth::render(float* out,int frames){
#ifdef SYNTH_TEST_ALLOC
    RealtimeScope realtimeScope;
#endif
    if(!ready()){std::fill(out,out+frames*2,0.f);return;}
    auto end=write.load(std::memory_order_acquire),r=read.load(std::memory_order_relaxed);
    auto current=epoch.load(std::memory_order_acquire);
    if(applied!=current){silence();applied=current;}
    while(r!=end){const Event e=queue[r%Capacity];++r;
        current=epoch.load(std::memory_order_acquire);
        if(applied!=current){silence();applied=current;}
        if(e.generation!=current)continue;
        if(e.type==On) {
            // One attack per pitch: replace sustained/retriggered layers, never accumulate them.
#ifdef SYNTH_TEST_POISON_INACTIVE
            // ASan sentinel: TSF initializes only playingPreset in never-started slots.
            for(int i=0;i<font->voiceNum;i++)if(font->voices[i].playingPreset==-1)
                __asan_poison_memory_region(&font->voices[i].playingKey,3*sizeof(int));
#endif
            for(int i=0;i<font->voiceNum;i++)if(font->voices[i].playingPreset!=-1 && font->voices[i].playingChannel==e.channel && font->voices[i].playingKey==e.key)font->voices[i].playingPreset=-1;
#ifdef SYNTH_TEST_POISON_INACTIVE
            for(int i=0;i<font->voiceNum;i++)__asan_unpoison_memory_region(&font->voices[i].playingKey,3*sizeof(int));
#endif
            tsf_channel_note_on(font,e.channel,e.key,e.velocity/127.f);
        } else if(e.type==Off)tsf_channel_note_off(font,e.channel,e.key);
        else tsf_channel_set_sustain(font,0,e.velocity>=64);
    }
    read.store(r,std::memory_order_release);
    // Fixed render chunks bound work and TSF stack use independently of device burst size.
    for(int offset=0;offset<frames;offset+=64)tsf_render_float(font,out+offset*2,std::min(64,frames-offset),0);
    float g=level.load(std::memory_order_relaxed);
    for(int i=0;i<frames*2;i++){float v=out[i]*g;out[i]=std::isfinite(v)?std::max(-.98f,std::min(.98f,v)):0;}
    current=epoch.load(std::memory_order_acquire);
    if(applied!=current || !ready()){silence();applied=current;std::fill(out,out+frames*2,0.f);}
}
int Synth::active() const {return font?tsf_active_voice_count(font):0;}
