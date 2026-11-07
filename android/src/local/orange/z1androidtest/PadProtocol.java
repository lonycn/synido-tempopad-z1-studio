package local.orange.z1androidtest;

import java.util.Arrays;

/** Pure MIDI/layout model, shared by Android and host-side tests. No device output. */
public final class PadProtocol {
    public static int sample(int cell) { return (7-cell/8)*8+cell%8; }
    public static int drum(int cell) { int r=7-cell/8,c=cell%8; return 36+4*r+c%4+32*(c/4); }
    public static int cell(int note) {
        if (note<36 || note>99) return -1;
        int i=note-36,r=(i%32)/4,c=i%4+4*(i/32);
        return (7-r)*8+c;
    }
    public interface Listener { void message(int status,int[] data); }
    public static final class Parser {
        private int status,count,needed,sysexLength;
        private boolean sysex,overflow;
        private final int[] data=new int[512];
        public void reset() { status=count=needed=sysexLength=0;sysex=overflow=false; }
        public void feed(byte[] bytes,int start,int length,Listener listener) {
            for (int i=start;i<start+length;i++) {
                int b=bytes[i]&255;
                if (b>=0xF8) continue;
                if (b==0xF0) { reset();sysex=true;continue; }
                if (b==0xF7) {
                    if (sysex && !overflow && sysexLength>0) listener.message(0xF0,Arrays.copyOf(data,sysexLength));
                    reset();continue;
                }
                if (b>=0x80) {
                    reset();
                    if (b<0xF0) {status=b;needed=((b&0xF0)==0xC0 || (b&0xF0)==0xD0)?1:2;}
                    continue;
                }
                if (sysex) {if(sysexLength<data.length)data[sysexLength++]=b;else overflow=true;continue;}
                if(status==0)continue;
                data[count++]=b;
                if(count==needed) {listener.message(status,Arrays.copyOf(data,count));count=0;}
            }
        }
    }
}
