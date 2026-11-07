package android.media.midi;
public final class MidiOutputPort implements java.io.Closeable {
    private MidiReceiver receiver;public boolean closed,throwConnect;
    public void connect(MidiReceiver receiver){if(throwConnect)throw new IllegalStateException("test connect failure");this.receiver=receiver;}
    public void emit(int... values){byte[] b=new byte[values.length];for(int i=0;i<b.length;i++)b[i]=(byte)values[i];
        try{receiver.onSend(b,0,b.length,0);}catch(java.io.IOException ex){throw new AssertionError(ex);}}
    public void close() throws java.io.IOException{closed=true;}
}
