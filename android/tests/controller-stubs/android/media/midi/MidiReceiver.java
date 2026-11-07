package android.media.midi;
public abstract class MidiReceiver {
    public abstract void onSend(byte[] b,int offset,int count,long timestamp) throws java.io.IOException;
    public void onFlush() throws java.io.IOException { }
}
