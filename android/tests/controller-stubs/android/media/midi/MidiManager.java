package android.media.midi;
import android.os.Handler;
import java.util.*;
public final class MidiManager {
    public static class DeviceCallback {public void onDeviceAdded(MidiDeviceInfo info){}public void onDeviceRemoved(MidiDeviceInfo info){}}
    public interface OnDeviceOpenedListener {void onDeviceOpened(MidiDevice device);}
    public final ArrayList<MidiDeviceInfo> infos=new ArrayList<>();public final ArrayList<MidiDevice> opened=new ArrayList<>();
    public int openCalls;
    public boolean delayOpen,noInput,noReceive,throwReceive,throwConnect,throwOpen,nullOpen;
    private DeviceCallback callback;private Handler worker;private final ArrayList<Runnable> pending=new ArrayList<>();
    public void registerDeviceCallback(DeviceCallback callback,Handler worker){this.callback=callback;this.worker=worker;}
    public void unregisterDeviceCallback(DeviceCallback callback){this.callback=null;}
    public MidiDeviceInfo[] getDevices(){return infos.toArray(new MidiDeviceInfo[0]);}
    public void openDevice(MidiDeviceInfo info,OnDeviceOpenedListener listener,Handler handler){
        openCalls++;if(throwOpen)throw new IllegalStateException("test manager setup failure");
        MidiDevice device=new MidiDevice();device.noInput=noInput;device.noReceive=noReceive;device.throwReceive=throwReceive;device.throwConnect=throwConnect;opened.add(device);
        Runnable done=()->listener.onDeviceOpened(nullOpen?null:device);if(delayOpen)pending.add(done);else handler.post(done);
    }
    public void completeOpens(){for(Runnable done:pending)worker.post(done);pending.clear();}
    public void add(MidiDeviceInfo info){infos.add(info);if(callback!=null)worker.post(()->callback.onDeviceAdded(info));}
    public void remove(MidiDeviceInfo info){infos.remove(info);if(callback!=null)worker.post(()->callback.onDeviceRemoved(info));}
}
