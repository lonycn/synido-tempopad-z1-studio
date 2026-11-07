package android.media.midi;
import android.os.SystemClock;
import java.util.*;
public final class MidiInputPort implements java.io.Closeable {
    public final ArrayList<byte[]> sent=new ArrayList<>();public final ArrayList<Long> attempts=new ArrayList<>();
    public boolean fail,closed;public Runnable onAttempt;
    public void send(byte[] b,int offset,int count) throws java.io.IOException {
        if(closed)throw new AssertionError("send to closed output");attempts.add(SystemClock.time);
        if(onAttempt!=null)onAttempt.run();
        if(fail)throw new java.io.IOException("test failure");sent.add(Arrays.copyOfRange(b,offset,offset+count));
    }
    public void close() throws java.io.IOException{closed=true;}
}
