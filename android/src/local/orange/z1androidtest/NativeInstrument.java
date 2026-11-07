package local.orange.z1androidtest;

import android.content.Context;
import android.os.Handler;
import java.io.*;
import java.util.concurrent.*;

/** Handle confinement: worker owns handle; serialized loader owns all create/close and disk I/O. */
final class NativeInstrument {
    private static final ExecutorService loader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Z1-SF2-Loader");t.setDaemon(true);return t;});
    private static final boolean library;
    static {boolean ok=false;try{System.loadLibrary("z1instrument");ok=true;}catch(UnsatisfiedLinkError ignored){}library=ok;}
    private long handle; private boolean closed;
    private final Handler worker;
    interface Listener { void loaded(boolean success,String message); }
    NativeInstrument(Context context,Handler worker,Listener listener){
        this.worker=worker;
        loader.execute(()->{
            long created=0;String error="采样音源加载失败；EASY 保留";
            try {
                if(!library)throw new IOException("arm64 native library unavailable");
                File bank=new File(context.getFilesDir(),"FluidR3_GM-3.1.sf2");
                // Atomic cache replacement; never expose a partially copied bank to TSF.
                if(bank.length()!=148398306L){
                    File temp=new File(context.getFilesDir(),"FluidR3_GM.tmp");
                    try(InputStream in=context.getAssets().open("FluidR3_GM.sf2");OutputStream out=new FileOutputStream(temp)){
                        byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    }
                    if(temp.length()!=148398306L || !temp.renameTo(bank))throw new IOException("SF2 cache copy failed");
                }
                created=create(bank.getAbsolutePath());
                if(created==0)error="SF2 / AAudio 初始化失败；EASY 保留";
            } catch(Exception e){error="采样音源失败: "+e.getMessage()+"；EASY 保留";}
            final long result=created;final String message=error;
            // onDestroy waits for no loader: stale result is disposed on loader, never the MIDI worker.
            if(!worker.post(()->{if(closed){if(result!=0)loader.execute(()->dispose(result));return;}handle=result;listener.loaded(result!=0,result!=0?"FluidR3 GM · 128 layered voices · AAudio ready":message);})){if(result!=0)dispose(result);}
        });
    }
    boolean ready(){return handle!=0 && healthy(handle);}
    boolean note(boolean drum,int pitch,int velocity){return ready() && send(handle,0,drum?1:0,pitch,velocity);}
    void off(int pitch){if(ready())send(handle,1,0,pitch,0);}
    void pedal(boolean down){if(ready())send(handle,2,0,0,down?127:0);}
    void stop(){if(handle!=0)panic(handle);}
    void volume(float value){if(handle!=0)gain(handle,value);}
    void close(){closed=true;long old=handle;handle=0;if(old!=0)loader.execute(()->dispose(old));}
    private static native long create(String path);
    private static native boolean send(long h,int type,int channel,int key,int velocity);
    private static native void panic(long h);
    private static native void gain(long h,float value);
    private static native boolean healthy(long h);
    private static native void dispose(long h);
}
