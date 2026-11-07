#include "Synth.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <thread>
#include <atomic>
#include <algorithm>
static float output[512*2];
static int checks;
static double peak;
static double render(Synth& s,int frames){double sum=0;int count=frames*2;
    while(frames){int n=std::min(512,frames);s.render(output,n);for(int i=0;i<n*2;i++){assert(std::isfinite(output[i]) && std::abs(output[i])<=.980001f);sum+=output[i]*output[i];peak=std::max(peak,(double)std::abs(output[i]));}frames-=n;checks++;assert(s.active()<=128);}
    return std::sqrt(sum/count);
}
static void silent(Synth& s){s.panic();assert(render(s,512)==0);}
int main(int argc,char** argv){assert(argc==2 || argc==3);
    if(argc==3){Synth single(argv[1]);assert(single.ready());single.command(Synth::On,0,60,100);assert(render(single,512)>0);printf("Single real-font engine, 128 layered voices, loaded/rendered.\n");return 0;}
    Synth invalid("/no-such-font");assert(!invalid.ready());assert(render(invalid,512)==0);
    Synth synth(argv[1]);assert(synth.ready());
    // First note traverses 128 inactive slots; panic/retrigger traverses previously used and unused slots.
    assert(synth.active()==0);assert(synth.command(Synth::On,0,60,100));assert(render(synth,512)>0);
    int layers=synth.active();assert(layers>0 && layers<=4);
    for(int i=0;i<16;i++){assert(synth.command(Synth::On,0,60,100));assert(render(synth,512)>0);assert(synth.active()==layers);}
    silent(synth);assert(synth.active()==0);assert(synth.command(Synth::On,1,38,100));assert(render(synth,512)>0);silent(synth);
    printf("Inactive-voice regression passed: first-note, 16 same-pitch retriggers, post-panic percussion\n");
    synth.command(Synth::On,0,60,20);double low=render(synth,24000);silent(synth);
    synth.command(Synth::On,0,60,120);double high=render(synth,24000);assert(high>low*1.5 && low>0);
    for(int note=36;note<=84;note++){silent(synth);synth.command(Synth::On,0,note,100);assert(render(synth,2048)>1e-7);}
    printf("Piano RMS velocity20=%.8f velocity120=%.8f\n",low,high);silent(synth);
    synth.command(Synth::Sustain,0,0,127);synth.command(Synth::On,0,60,100);render(synth,4800);
    synth.command(Synth::Off,0,60,0);double held=render(synth,48000);assert(held>0);
    synth.command(Synth::Sustain,0,0,0);render(synth,480000);assert(render(synth,1024)<1e-8);silent(synth);
    synth.command(Synth::On,0,64,100);render(synth,4800);synth.command(Synth::Off,0,64,0);render(synth,480000);assert(render(synth,1024)<1e-8);
    silent(synth);synth.command(Synth::On,1,38,20);double drumLow=render(synth,12000);silent(synth);
    synth.command(Synth::On,1,38,120);double drumHigh=render(synth,12000);assert(drumHigh>drumLow*1.5 && drumLow>0);
    printf("Snare RMS velocity20=%.8f velocity120=%.8f\n",drumLow,drumHigh);
    int extended[]={36,38,42,46,49,51,41,43,45,47,48,50,39,54,56,75,80,81};
    for(int r=0;r<8;r++)for(int c=0;c<8;c++){int input=36+4*r+c%4+32*(c/4),note=input<=81?input:extended[input-82];
        silent(synth);synth.command(Synth::On,1,note,100);double rms=render(synth,12000);assert(rms>1e-7);printf("Drum input=%d output=%d RMS=%.8f\n",input,note,rms);
    }
    silent(synth);for(int i=0;i<1024;i++)synth.command(Synth::On,0,36+i%49,100);assert(render(synth,512)==0); // overflow panic dominates queued old attacks
    for(int i=0;i<256;i++)synth.command(Synth::On,0,i%128,127);render(synth,512);assert(synth.active()<=128);silent(synth);
    // Sustained same-pitch retriggers replace layers rather than growing indefinitely.
    synth.command(Synth::Sustain,0,0,127);for(int i=0;i<1000;i++){synth.command(Synth::On,0,60,100);synth.command(Synth::Off,0,60,0);render(synth,64);assert(synth.active()<=4);}silent(synth);
    // Actual SPSC queue and panic crossing render; core lifetime ends only after callback join.
    std::atomic<bool> done{false};std::thread producer([&]{for(int i=0;i<20000;i++){synth.command(Synth::On,0,36+i%49,i%127+1);synth.command(Synth::Off,0,36+i%49,0);if(i%100==0)synth.panic();}done=true;});
    while(!done)render(synth,64);producer.join();silent(synth);
    synth.gain(100);synth.command(Synth::On,0,60,127);assert(render(synth,4800)>0);silent(synth);
    synth.gain(NAN);synth.command(Synth::On,0,60,127);assert(render(synth,512)==0);silent(synth);synth.gain(.45f);
    for(int i=0;i<3;i++){Synth lifetime(argv[1]);assert(lifetime.ready());lifetime.command(Synth::On,0,60,100);assert(render(lifetime,512)>0);silent(lifetime);}
    synth.command(Synth::On,0,60,127);synth.invalidate();assert(!synth.ready());assert(!synth.command(Synth::On,0,61,127));assert(render(synth,512)==0);synth.panic();assert(render(synth,512)==0);
    printf("SynthTest passed: %d render blocks, 64/64 drums, peak=%.8f, 128 layered voice ceiling\n",checks,peak);
}
