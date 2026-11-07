package android.media.midi;
public final class MidiDevice implements java.io.Closeable {
    public final MidiInputPort input=new MidiInputPort();public final MidiOutputPort output=new MidiOutputPort();
    public boolean closed,noInput,noReceive,throwReceive,throwConnect;public int inputOpens,outputOpens;
    public MidiInputPort openInputPort(int index){if(index!=0)throw new AssertionError("output port index");inputOpens++;return noInput?null:input;}
    public MidiOutputPort openOutputPort(int index){if(index!=0)throw new AssertionError("receive port index");outputOpens++;if(throwReceive)throw new IllegalStateException("test receive setup failure");output.throwConnect=throwConnect;return noReceive?null:output;}
    public void close() throws java.io.IOException{closed=true;}
}
