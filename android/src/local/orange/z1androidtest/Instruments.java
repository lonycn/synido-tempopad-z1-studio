package local.orange.z1androidtest;

import java.util.*;

/** Pure layout/routing model. MIDI pitches never identify the hardware mode. */
final class Instruments {
    enum Mode { EASY, DRUM, KEY }
    static final int[] EXTENDED={36,38,42,46,49,51,41,43,45,47,48,50,39,54,56,75,80,81};
    static final String[] GM={"Bass Drum 1","Side Stick","Acoustic Snare","Hand Clap","Electric Snare","Low Floor Tom","Closed Hi-Hat","High Floor Tom","Pedal Hi-Hat","Low Tom","Open Hi-Hat","Low-Mid Tom","Hi-Mid Tom","Crash Cymbal 1","High Tom","Ride Cymbal 1","Chinese Cymbal","Ride Bell","Tambourine","Splash Cymbal","Cowbell","Crash Cymbal 2","Vibraslap","Ride Cymbal 2","Hi Bongo","Low Bongo","Mute Hi Conga","Open Hi Conga","Low Conga","High Timbale","Low Timbale","High Agogo","Low Agogo","Cabasa","Maracas","Short Whistle","Long Whistle","Short Guiro","Long Guiro","Claves","Hi Wood Block","Low Wood Block","Mute Cuica","Open Cuica","Mute Triangle","Open Triangle"};
    static int key(int cell) {
        if(cell<0 || cell>=64)return -1;
        int r=7-cell/8,c=cell%8;
        int[] offsets=(r%2==0)?new int[]{0,2,4,5,7,9,11,12}:new int[]{-1,1,3,-1,6,8,10,-1};
        return offsets[c]<0?-1:36+12*(r/2)+offsets[c];
    }
    static String modeLabel(Mode applied,Mode button){return (applied==button?"● ":"")+button.name();}
    static int inputCell(Mode mode,int note){
        if(mode!=Mode.KEY)return PadProtocol.cell(note);
        for(int i=0;i<64;i++)if(key(i)>=0 && key(i)==note)return i;
        return -1;
    }
    static int drum(int input){return input>=36 && input<=81?input:EXTENDED[Math.floorMod(input-82,EXTENDED.length)];}
    static String name(int note){String[] n={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};return n[Math.floorMod(note,12)]+(note/12-1);}
    static String dynamics(boolean fixed,int velocity){return fixed?"无力度数据 / 固定音量":"力度 "+velocity+"/127";}
    static int pitch(Mode mode,int cell){return mode==Mode.KEY?key(cell):PadProtocol.drum(cell);}
    interface Sink { void on(int pitch,int velocity); void off(int pitch); void sustain(boolean down); void panic(); }
    /** Coalesce shared pitches: first owner attacks, last owner releases; no duplicate voice leak. */
    static final class Owners {
        final Sink sink; final Set<String> pedals=new HashSet<>(); final Map<String,Integer> owners=new HashMap<>(); final int[] counts=new int[128];
        Owners(Sink sink){this.sink=sink;}
        void press(String owner,int pitch,int velocity){Integer old=owners.get(owner);if(old!=null && old==pitch)return;release(owner);owners.put(owner,pitch);if(counts[pitch]++==0)sink.on(pitch,velocity);}
        void release(String owner){Integer pitch=owners.remove(owner);if(pitch!=null && --counts[pitch]==0)sink.off(pitch);}
        void pedal(String owner,boolean down){boolean before=!pedals.isEmpty();if(down)pedals.add(owner);else pedals.remove(owner);boolean after=!pedals.isEmpty();if(before!=after)sink.sustain(after);}
        void clear(){owners.clear();pedals.clear();Arrays.fill(counts,0);sink.sustain(false);sink.panic();}
    }
    static final class ModeIntent {
        private long generation; private Mode requested=Mode.EASY;
        synchronized long manual(Mode mode){requested=mode;return ++generation;}
        synchronized long generation(){return generation;}
        synchronized Mode requested(){return requested;}
        synchronized boolean current(long token){return generation==token;}
    }
}
