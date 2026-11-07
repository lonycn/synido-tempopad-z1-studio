package local.orange.z1androidtest;

import android.hardware.usb.UsbDevice;
import android.media.midi.*;
import android.os.Handler;
import android.os.SystemClock;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/** All MIDI, lighting and lifecycle state lives on the existing audio worker, never the UI thread. */
final class MidiLighting {
    interface Listener {
        void message(int status,int[] data);
        void packet(byte[] bytes);
        void state(String connection,String lighting,RgbFrame frame);
        void disconnected();
    }
    private final MidiManager manager;
    private final Handler work;
    private final Listener listener;
    private final LifecycleGate gate=new LifecycleGate();
    private final LightingState lights;
    private final MidiSessionCoordinator sessions;
    private Object leaseToken;
    private boolean superseded,setupFailed;
    private final Diffusion diffusion=new Diffusion();
    private final PadProtocol.Parser parser=new PadProtocol.Parser();
    private MidiDevice device;
    private MidiOutputPort receive;
    private EasyRgbTransport output;
    private int deviceId=-1;
    private boolean opening,enabled=true,destroyed,started,performanceActive;
    private String setupFailureMessage;
    private String connection="未连接 Z-1",status="等待实体 EASY 输入";
    private Runnable destroyComplete;
    private final Runnable tick=this::tick;
    private final Runnable drain=this::drain;

    MidiLighting(MidiManager manager,Handler work,Listener listener){this(manager,work,listener,MidiSessionCoordinator.PROCESS);}
    MidiLighting(MidiManager manager,Handler work,Listener listener,MidiSessionCoordinator sessions){
        this.manager=manager;this.work=work;this.listener=listener;this.sessions=sessions;lights=new LightingState(sessions.limiter);
    }
    void start(){if(manager!=null){manager.registerDeviceCallback(callback,work);started=true;}publish();}
    private static boolean isZ1(MidiDeviceInfo info){
        if(info.getType()!=MidiDeviceInfo.TYPE_USB)return false;
        Object property=info.getProperties().get(MidiDeviceInfo.PROPERTY_USB_DEVICE);
        if(!(property instanceof UsbDevice))return false;
        UsbDevice usb=(UsbDevice)property;
        return usb.getVendorId()==10467 && usb.getProductId()==4101;
    }
    private ArrayList<MidiDeviceInfo> devices(){
        ArrayList<MidiDeviceInfo> found=new ArrayList<>();
        if(manager!=null)for(MidiDeviceInfo info:manager.getDevices())if(isZ1(info))found.add(info);
        return found;
    }
    private boolean uniqueSame(){
        ArrayList<MidiDeviceInfo> found=devices();
        return device!=null && found.size()==1 && found.get(0).getId()==deviceId;
    }
    private final MidiManager.DeviceCallback callback=new MidiManager.DeviceCallback(){
        @Override public void onDeviceAdded(MidiDeviceInfo info){if(isZ1(info)){setupFailed=false;refresh();}}
        @Override public void onDeviceRemoved(MidiDeviceInfo info){
            if(info.getId()==deviceId)closeConnection(false);
            if(isZ1(info)){setupFailed=false;refresh();}
        }
    };
    void foreground(boolean active){
        gate.foreground(active);
        if(active){superseded=false;refresh();}else closeConnection(true);
    }
    void reconnect(){setupFailed=false;superseded=false;closeConnection(true);}
    void setEnabled(boolean value){
        enabled=value;
        if(!value)panic(); // Control never grants authorization.
        publish();
    }
    private boolean easyMode=true;
    void easyMode(boolean value){easyMode=value;if(!value)panic();}
    void performanceActive(boolean active){performanceActive=active;if(!active)panic();}
    void panic(){
        diffusion.clear();work.removeCallbacks(tick);lights.clear();publish();scheduleDrain();
    }
    private void revoke(){
        diffusion.clear();lights.revoke();work.removeCallbacks(tick);work.removeCallbacks(drain);publish();
    }
    private void refresh(){
        if(!gate.mayOpen() || manager==null || superseded || setupFailed)return;
        ArrayList<MidiDeviceInfo> found=devices();
        if(found.size()!=1){
            if(device!=null || opening || leaseToken!=null)closeConnection(false);
            connection=found.isEmpty()?"未连接 Z-1 · 可触屏试听":"多台 Z-1 · 已关闭 MIDI / RGB";
            publish();return;
        }
        if(device!=null || opening)return;
        if(leaseToken==null){
            Object requested=new Object();leaseToken=requested;
            sessions.acquire(requested,()->work.post(()->{
                if(leaseToken!=requested || !gate.mayOpen() || superseded || setupFailed){sessions.release(requested);return;}
                refresh();
            }),()->work.post(()->{
                if(leaseToken==requested && sessions.owns(requested)){superseded=true;closeConnection(true);}
            }));
            return;
        }
        if(!sessions.owns(leaseToken))return;
        MidiDeviceInfo info=found.get(0);int token=gate.beginOpen();opening=true;deviceId=info.getId();
        connection="正在打开 USB MIDI…";publish();
        try{manager.openDevice(info,opened->{
            if(!gate.accepts(token)){close(opened);return;}
            opening=false;
            if(opened==null){setupError("MIDI 打开失败，请重新连接");return;}
            device=opened;
            if(!uniqueSame()){closeConnection(false);refresh();return;}
            try {
                receive=opened.openOutputPort(0);
                if(receive==null)throw new IOException("接收端口被占用");
                // The only Android device-write port is encapsulated by this audited frame transport.
                output=EasyRgbTransport.open(opened);
                lights.connected();parser.reset();
                receive.connect(new MidiReceiver(){
                    @Override public void onSend(byte[] bytes,int offset,int count,long timestamp){
                        byte[] copy=Arrays.copyOfRange(bytes,offset,offset+count);
                        work.post(()->{
                            // Keep observing Notes during bounded cleanup: they cancel a pending black
                            // rather than sending it into a newly selected hardware mode.
                            if(device!=opened || (!gate.accepts(token) && !gate.closing()))return;
                            if(!uniqueSame()){closeConnection(false);refresh();return;}
                            if(!gate.closing())listener.packet(copy);
                            parser.feed(copy,0,copy.length,MidiLighting.this::message);
                            if(gate.closing() && !lights.pending())finishClose(closeToken);
                        });
                    }
                    @Override public void onFlush(){work.post(()->{if(gate.accepts(token)){parser.reset();panic();listener.disconnected();}});}
                });
                connection="已连接 Synido TempoPAD Z-1";
                status=output==null?"RGB 端口不可用 · 音频仍可用":"等待实体 EASY 输入";
            }catch(Exception ex){setupError("连接错误："+ex.getMessage());}
            publish();
        },work);}catch(RuntimeException ex){setupError("连接错误："+ex.getMessage());}
    }
    private void setupError(String error){
        setupFailed=true;setupFailureMessage=error;closeConnection(false);connection=error;publish();
    }
    private void message(int status,int[] data){
        int kind=status&0xF0;
        boolean note=(kind==0x80 || kind==0x90) && data.length==2;
        if(note)revoke(); // All channels revoke, including Notes observed during cleanup.
        if(gate.closing())return; // EASY cannot revive effects or sound during cleanup.
        listener.message(status,data); // Event counters and sample playback precede lamp I/O.
        if(status==0xF0 && data.length==1 && !gate.closing()){
            lights.authorize();
            if(easyMode && enabled && performanceActive && data[0]<64 && !lights.disabled()){
                boolean hadWaves=diffusion.waveCount()>0;
                diffusion.expire(now());
                if(hadWaves && diffusion.waveCount()==0)lights.clear();
                lights.resumeEffects();diffusion.hit(PadProtocol.cell(36+data[0]),100,now());
                work.removeCallbacks(tick);work.post(tick);
            }
            publish();
        }
    }
    private static long now(){return SystemClock.uptimeMillis();}
    private void tick(){
        if(gate.closing() || !lights.authorized() || !enabled || !performanceActive)return;
        RgbFrame frame=diffusion.frame(now());
        if(diffusion.waveCount()==0)lights.clear();else lights.render(frame);
        publish();scheduleDrain();
        if(diffusion.waveCount()>0)work.postDelayed(tick,50);
    }
    private void scheduleDrain(){
        work.removeCallbacks(drain);
        if(lights.pending() && output!=null)work.postDelayed(drain,lights.delay(now()));
    }
    private void drain(){
        if(output==null || !lights.authorized() || !sessions.owns(leaseToken))return;
        if(!uniqueSame()){closeConnection(false);refresh();return;}
        RgbFrame frame=lights.beginAttempt(now());
        if(frame==null){scheduleDrain();return;}
        boolean sent=output.send(frame);
        lights.completeAttempt(frame,sent);
        if(lights.disabled()){
            diffusion.clear();work.removeCallbacks(tick);close(output);output=null;
            status="RGB 发送失败3次 · 已禁用，需重新连接";
        }
        publish();scheduleDrain();
        if(gate.closing() && !lights.pending())finishClose(closeToken);
        else if(sent && frame.isBlack() && diffusion.waveCount()>0){
            work.removeCallbacks(tick);work.post(tick); // Resume the latest wave after terminal success.
        }
    }
    private int closeToken;
    private Runnable closeDeadline;
    private void closeConnection(boolean tryBlack){
        if(gate.closing()){
            if(!tryBlack || !uniqueSame()){revoke();finishClose(closeToken);}
            return;
        }
        closeToken=gate.beginClose();opening=false;
        listener.disconnected();
        diffusion.clear();work.removeCallbacks(tick);
        if(tryBlack && uniqueSame() && lights.authorized() && output!=null){
            lights.clear();publish();scheduleDrain();
            if(lights.pending()){
                int token=closeToken;closeDeadline=()->finishClose(token);
                work.postDelayed(closeDeadline,300);return;
            }
        }
        finishClose(closeToken);
    }
    private void finishClose(int token){
        if(!gate.ownsClose(token))return;
        if(closeDeadline!=null){work.removeCallbacks(closeDeadline);closeDeadline=null;}work.removeCallbacks(drain);work.removeCallbacks(tick);
        lights.revoke();diffusion.clear();parser.reset();close(output);close(receive);close(device);
        output=null;receive=null;device=null;deviceId=-1;gate.finishClose(token);
        Object released=leaseToken;leaseToken=null;sessions.release(released);
        connection=setupFailed?setupFailureMessage:"MIDI 已关闭";status="等待实体 EASY 输入";publish();
        if(destroyed){if(destroyComplete!=null){Runnable done=destroyComplete;destroyComplete=null;done.run();}}
        else if(gate.mayOpen() && !superseded && !setupFailed)work.post(this::refresh);
    }
    void destroy(Runnable complete){
        destroyed=true;destroyComplete=complete;gate.destroy();
        if(started)manager.unregisterDeviceCallback(callback);
        closeConnection(true);
    }
    private void publish(){
        String value=lights.disabled()?status:!enabled?"RGB 已关闭":output==null?"RGB 未连接":lights.authorized()?"最近实体 EASY 已确认 · 非模式查询":"等待实体 EASY 输入";
        listener.state(connection,value,lights.rendered());
    }
    private static void close(Closeable resource){if(resource!=null)try{resource.close();}catch(IOException ignored){}}

    /** Sole physical output entrypoint; no arbitrary-byte send API is exposed to controller/UI. */
    private static final class EasyRgbTransport implements Closeable {
        private final MidiInputPort port;
        private EasyRgbTransport(MidiInputPort port){this.port=port;}
        static EasyRgbTransport open(MidiDevice sameDevice){
            try{
                MidiInputPort port=sameDevice.openInputPort(0);
                return port==null?null:new EasyRgbTransport(port);
            }catch(RuntimeException unavailable){return null;}
        }
        boolean send(RgbFrame frame){
            byte[] wire=frame.easyWireFrame();
            try{port.send(wire,0,wire.length);return true;}catch(IOException | RuntimeException ex){return false;}
        }
        @Override public void close() throws IOException{port.close();}
    }
}
