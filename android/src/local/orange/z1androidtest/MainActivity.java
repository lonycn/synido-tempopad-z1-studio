package local.orange.z1androidtest;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.media.midi.MidiManager;
import android.os.*;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Foreground-only offline sampler with audited, physical EASY-only RGB diffusion. */
public final class MainActivity extends Activity {
    private static final String TAG="Z1AndroidTest";
    private static final String[] DRUMS={"Deep Kick","Punch Kick","Snare","Rim Snare","Closed Hat","Open Hat","Clap","High Tom","Mid Tom","Low Tom","Crash","Ride","Cowbell","Shaker","Clave","Click"};
    private final Handler ui=new Handler(Looper.getMainLooper());
    private HandlerThread thread; private Handler work;
    private MidiLighting lighting;
    private boolean resumed=false,destroyed=false,windowFocused=false;
    private String lightingStatus="等待实体 EASY 输入";
    private RgbFrame lightingFrame=RgbFrame.BLACK;
    private NativeInstrument nativeAudio;
    private Instruments.Mode mode=Instruments.Mode.EASY;
    private final Instruments.ModeIntent modeIntent=new Instruments.ModeIntent();
    private long appliedIntent,auditionGeneration;
    private boolean nativeLoaded;
    private String nativeStatus="FluidR3 GM / 钢琴加载中（EASY 可用）";
    private final Instruments.Owners keys=new Instruments.Owners(new Instruments.Sink(){
        public void on(int pitch,int velocity){if(nativeAudio!=null)nativeAudio.note(false,pitch,velocity);}
        public void off(int pitch){if(nativeAudio!=null)nativeAudio.off(pitch);}
        public void sustain(boolean down){if(nativeAudio!=null)nativeAudio.pedal(down);}
        public void panic(){if(nativeAudio!=null)nativeAudio.stop();}
    });
    private SoundPool pool; private final int[] sounds=new int[64];
    private final LinkedHashMap<Integer,Integer> streams=new LinkedHashMap<>();
    private final HashMap<String,Integer> held=new HashMap<>();
    private final long[] flashes=new long[64]; private final int[] holds=new int[64];
    private int loaded=0,loadErrors=0,hits=0,packets=0;
    private float volume=.45f;
    private String midiStatus="正在连接…",audioStatus="加载 64 段音频…",last="等待敲击",raw="",inputMode="尚无输入";
    private UiPublisher<UiSnapshot> uiPublisher;
    private TextView connection,audio,events,rawText,lightingText,modeStatus;
    private final EnumMap<Instruments.Mode,Button> modeButtons=new EnumMap<>(Instruments.Mode.class);
    private Grid grid;
    private AudioManager audioManager; private AudioFocusRequest focus;
    private boolean audioFocus=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        thread=new HandlerThread("Z1-MIDI-Audio",android.os.Process.THREAD_PRIORITY_AUDIO);thread.start();work=new Handler(thread.getLooper());
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        uiPublisher=new UiPublisher<>(new UiPublisher.Scheduler(){
            public void worker(Runnable task){work.post(task);}
            public void delayedWorker(Runnable task){work.postDelayed(task,33);}
            public void ui(Runnable task){ui.post(task);}
        },this::captureUi,token->!isDestroyed() && modeIntent.current(token),this::publishUi);
        lighting=new MidiLighting((MidiManager)getSystemService(MIDI_SERVICE),work,new MidiLighting.Listener(){
            public void message(int status,int[] data){MainActivity.this.message(status,data);}
            public void packet(byte[] bytes){packets++;raw=hex(bytes);scheduleUi();}
            public void state(String m,String l,RgbFrame f){midiStatus=m;lightingStatus=l;lightingFrame=f;scheduleUi();}
            public void disconnected(){stopSounds();}
        });
        buildUi();
        work.post(()->{
            nativeAudio=new NativeInstrument(getApplicationContext(),work,(ok,message)->{nativeStatus=message;nativeLoaded=ok;if(ok)nativeAudio.volume(volume);scheduleUi();});
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            pool=new SoundPool.Builder().setMaxStreams(32).setAudioAttributes(attributes).build();
            pool.setOnLoadCompleteListener((p,id,status)->{
                work.post(()->{if(destroyed)return;
                if(status==0)loaded++;else loadErrors++;
                audioStatus=loadErrors>0?"音频加载失败 "+loadErrors+" 项":"音频 "+loaded+" / 64"+(loaded==64?" · 已就绪":" · 加载中");
                if(loaded==64)Log.i(TAG,"AUDIO_READY samples=64 voices=32");
                scheduleUi();});
            });
            for(int i=0;i<64;i++) {
                int res=getResources().getIdentifier(String.format(Locale.ROOT,"pad_%02d",i+1),"raw",getPackageName());
                sounds[i]=pool.load(MainActivity.this,res,1);
            }
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->{audioFocus=resumed && change==AudioManager.AUDIOFOCUS_GAIN;if(!audioFocus)stopSounds();lighting.performanceActive(audioFocus && windowFocused);scheduleUi();},work).build();
            if(resumed)requestFocus();
            lighting.start();
        });
    }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void buildUi() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(10),dp(18),dp(10));root.setBackgroundColor(Color.rgb(12,15,23));
        TextView title=text("Z1 STUDIO  /  安卓测试版 0.3.1",23,Color.WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(text("EASY / DRUM / KEY · 离线采样音源 · 软件不发送硬件模式命令",12,0xFF8B9AB0));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        grid=new Grid(this);body.addView(grid,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView sidebar=new ScrollView(this);body.addView(sidebar,new LinearLayout.LayoutParams(dp(285),-1));
        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(16),dp(8),0,0);sidebar.addView(side);
        LinearLayout modes=new LinearLayout(this);side.addView(modes);
        for(Instruments.Mode choice:Instruments.Mode.values()){
            Button button=new Button(this);button.setTextSize(14);modeButtons.put(choice,button);modes.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
            button.setOnClickListener(v->{grid.cancelTouches();long token=modeIntent.manual(choice);work.post(()->{if(!modeIntent.current(token))return;appliedIntent=token;changeMode(choice);lighting.easyMode(mode==Instruments.Mode.EASY);});});
        }
        modeStatus=text("",12,0xFF62E9C7);side.addView(modeStatus);renderModes(Instruments.Mode.EASY);
        side.addView(text("USB MIDI",12,0xFF91A2B8));connection=text("正在连接…",15,0xFF56E5C5);side.addView(connection);
        side.addView(text("AUDIO / LOCAL SAMPLES",12,0xFF91A2B8));audio=text(audioStatus,14,Color.WHITE);side.addView(audio);
        lightingText=text(lightingStatus,12,0xFF56E5C5);side.addView(lightingText);
        Switch lamps=new Switch(this);lamps.setText("实体 EASY 扩散");lamps.setTextColor(Color.WHITE);lamps.setChecked(true);
        lamps.setOnCheckedChangeListener((button,checked)->work.post(()->lighting.setEnabled(checked)));side.addView(lamps);
        events=text(last,16,Color.WHITE);side.addView(events);
        rawText=text("",11,0xFF8B9AB0);side.addView(rawText);
        side.addView(text("DRUM / KEY 请手动匹配硬件模式；重叠 Note 不能识别模式。\nKEY 重复 C 同时高亮；暗格停用。\nEASY 专用输入无力度；触屏固定音量。",12,0xFFB1BDD0));
        LinearLayout buttons=new LinearLayout(this);side.addView(buttons);
        Button test=new Button(this);test.setText("试听");test.setOnClickListener(v->work.post(()->{String owner="audition:"+(++auditionGeneration);hit(owner,56,100,"屏幕试听",true);work.postDelayed(()->release(owner),250);}));buttons.addView(test,new LinearLayout.LayoutParams(0,dp(48),1));
        Button stop=new Button(this);stop.setText("停止声音");stop.setOnClickListener(v->work.post(()->{stopSounds();lighting.panic();last="已停止所有声音";scheduleUi();}));buttons.addView(stop,new LinearLayout.LayoutParams(0,dp(48),1));
        Button credits=new Button(this);credits.setText("关于 / 离线音源许可");credits.setOnClickListener(v->new Thread(()->{
            StringBuilder notice=new StringBuilder("FluidR3 GM sampled acoustic piano / GM drums. TinySoundFont omits SF2 modulators, reverb and chorus. Layered voices limited to 128. AAudio low latency requested, not measured.\n\n");
            try{for(String file:getAssets().list("licenses")){notice.append(file).append("\n");try(java.io.InputStream in=getAssets().open("licenses/"+file)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);notice.append(new String(out.toByteArray(),"UTF-8")).append("\n\n");}}}catch(Exception e){notice.append(e.toString());}
            ui.post(()->{if(isDestroyed())return;ScrollView scroll=new ScrollView(this);TextView content=text(notice.toString(),13,Color.WHITE);scroll.addView(content);new android.app.AlertDialog.Builder(this).setTitle("0.3.1 · 离线音源与许可").setView(scroll).setPositiveButton("关闭",null).show();});
        },"Z1-Credits").start());side.addView(credits);
        Button reconnect=new Button(this);reconnect.setText("重新连接 Z-1");reconnect.setOnClickListener(v->work.post(()->lighting.reconnect()));side.addView(reconnect,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.addView(text("主音量",12,0xFFB1BDD0));
        SeekBar gain=new SeekBar(this);gain.setMax(100);gain.setProgress(45);bottom.addView(gain,new LinearLayout.LayoutParams(dp(230),dp(38)));
        gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){work.post(()->{volume=value/100f;if(nativeAudio!=null)nativeAudio.volume(volume);if(pool!=null)for(Map.Entry<Integer,Integer> e:streams.entrySet()){float g=volume*e.getValue()/127f;pool.setVolume(e.getKey(),g,g);}});}});
        TextView note=text("声音来自平板 · 优先用扬声器/有线耳机测试 · 实际延迟待听感确认",11,0xFF8B9AB0);bottom.addView(note,new LinearLayout.LayoutParams(0,-2,1));root.addView(bottom);
        setContentView(root);
    }
    /** Only applied worker snapshots call this; never mark an unaccepted request active. */
    private void renderModes(Instruments.Mode applied){
        for(Map.Entry<Instruments.Mode,Button> entry:modeButtons.entrySet()){
            boolean selected=entry.getKey()==applied;Button button=entry.getValue();
            button.setSelected(selected);button.setActivated(selected);
            button.setText(Instruments.modeLabel(applied,entry.getKey()));
            button.setTextColor(selected?0xFF08241D:0xFFCED6E4);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?0xFF62E9C7:0xFF283142));
            button.setContentDescription(selected?"当前软件模式 "+applied:"切换到 "+entry.getKey());
        }
        modeStatus.setText("当前软件模式："+applied+" · 已选中（硬件须匹配）");
    }
    private static String hex(byte[] bytes) {StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(16,bytes.length);i++){if(i>0)b.append(' ');b.append(String.format(Locale.ROOT,"%02X",bytes[i]&255));}return b.toString();}
    private void changeMode(Instruments.Mode next){
        if(next!=Instruments.Mode.EASY && (nativeAudio==null || !nativeAudio.ready())){
            last="音源尚未就绪 / 已失效；保留 "+mode+"。"+nativeStatus;scheduleUi();return;
        }
        stopSounds();mode=next;lighting.easyMode(next==Instruments.Mode.EASY);
        last="软件 "+mode+" · 请手动匹配硬件（EASY 敲垫可自动识别）";scheduleUi();
    }
    private void message(int status,int[] data) {
        if(status==0xF0 && data.length==1){
            if(appliedIntent!=modeIntent.generation()){lighting.easyMode(false);return;}
            if(mode!=Instruments.Mode.EASY){if(data[0]>=64)return;changeMode(Instruments.Mode.EASY);}
            int index=data[0]%64;String owner="easy:"+index;
            if(data[0]<64)hit(owner,PadProtocol.cell(36+index),100,"EASY 专用输入",true);else release(owner);return;
        }
        int kind=status&0xF0,ch=status&15;
        if(data.length!=2)return;
        if(kind==0xB0 && (data[0]==120 || data[0]==123)){stopSounds();lighting.panic();return;}
        if(ch!=0 && ch!=2){
            if(kind==0x90 && data[1]>0){inputMode="Note CH"+(ch+1);last=mode+" · 收到输入 "+data[0]+" · "+Instruments.dynamics(false,data[1])+"\n未演奏：当前仅接收 CH1 / CH3";scheduleUi();}
            return;
        }
        if(kind==0xB0){
            if(data[0]==64 && mode==Instruments.Mode.KEY && active())keys.pedal("midi:"+ch,data[1]>=64);
            return;
        }
        String owner="note:"+ch+":"+data[0];
        if(kind==0x90 && data[1]>0){
            int cell=Instruments.inputCell(mode,data[0]);
            if(cell>=0)hit(owner,cell,data[1],"Note CH"+(ch+1),false);
            else {inputMode="Note CH"+(ch+1);last=mode+" · 收到输入 "+data[0]+" · "+Instruments.dynamics(false,data[1])+"\n未演奏：超出当前布局音区 "+(mode==Instruments.Mode.KEY?"36–84":"36–99")+"；请核对实体模式/音区";scheduleUi();}
            // Local diagnostic only, after audio submission. No device writes or mode guessing.
            Log.i(TAG,"NOTE_ON software="+mode+" channel="+(ch+1)+" pitch="+data[0]+" velocity="+data[1]+" cell="+cell);
        } else if(kind==0x80 || kind==0x90)release(owner);
    }
    private boolean active(){return audioFocus && resumed && windowFocused;}
    private void hit(String owner,int cell,int velocity,String source,boolean fixed) {
        if(cell<0 || cell>=64)return;
        int input=Instruments.pitch(mode,cell);if(input<0)return;
        release(owner);if(!active())return;
        held.put(owner,cell);
        for(int i=0;i<64;i++)if(i==cell || (mode==Instruments.Mode.KEY && Instruments.key(i)==input)){holds[i]++;flashes[i]=SystemClock.uptimeMillis();}
        hits++;inputMode=source;
        int sample=PadProtocol.sample(cell),output=input;String name;boolean played=false;
        if(mode==Instruments.Mode.EASY){
            name=sample<16?DRUMS[sample]:"Pluck "+(sample+1);
            if(pool!=null && loaded==64 && loadErrors==0){float g=volume*velocity/127f;int stream=pool.play(sounds[sample],g,g,1,0,1);
                if(stream!=0){played=true;streams.put(stream,velocity);if(streams.size()>32){Integer old=streams.keySet().iterator().next();streams.remove(old);}}
            }
        }else{
            output=mode==Instruments.Mode.DRUM?Instruments.drum(input):input;
            name=mode==Instruments.Mode.DRUM?Instruments.GM[output-36]:"Acoustic Piano "+Instruments.name(output);
            if(nativeAudio!=null && nativeAudio.ready()){
                if(mode==Instruments.Mode.KEY)keys.press(owner,output,velocity);else played=nativeAudio.note(true,output,velocity);if(mode==Instruments.Mode.KEY)played=nativeAudio.ready();
            }else nativeStatus="SF2 / AAudio 不可用；请选择 EASY 或重启重试（无替代钢琴）";
        }
        last=mode+" · 触发 "+hits+" 次\n输入 "+input+" → 输出 "+output+" · "+name+"\n"+Instruments.dynamics(fixed,velocity)+(played?"":"\n音频未提交，请检查加载/焦点");scheduleUi();
    }
    private void release(String owner){Integer cell=held.remove(owner);keys.release(owner);if(cell!=null){int pitch=Instruments.pitch(mode,cell);for(int i=0;i<64;i++)if(i==cell || (mode==Instruments.Mode.KEY && Instruments.key(i)==pitch))holds[i]=Math.max(0,holds[i]-1);scheduleUi();}}
    private void stopSounds(){if(pool!=null){for(Integer stream:streams.keySet())pool.stop(stream);}streams.clear();keys.clear();held.clear();Arrays.fill(holds,0);Arrays.fill(flashes,0);scheduleUi();}
    private void requestFocus(){if(focus!=null)audioFocus=audioManager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;lighting.performanceActive(resumed && audioFocus && windowFocused);scheduleUi();}
    private static final class UiSnapshot {
        final Instruments.Mode mode;final String midi,lighting,audio,event,raw;
        final RgbFrame frame;final long[] flashes;final int[] holds;
        UiSnapshot(Instruments.Mode mode,String midi,String lighting,String audio,String event,String raw,RgbFrame frame,long[] flashes,int[] holds){
            this.mode=mode;this.midi=midi;this.lighting=lighting;this.audio=audio;this.event=event;this.raw=raw;this.frame=frame;this.flashes=flashes;this.holds=holds;
        }
    }
    private void scheduleUi(){if(!destroyed)uiPublisher.changed();}
    private UiPublisher.State<UiSnapshot> captureUi(){
        if(nativeLoaded && !nativeAudio.ready()){
            nativeLoaded=false;nativeStatus="AAudio 输出已断开 / 不可用：请重新打开 App；EASY 仍可用";stopSounds();
        }
        // Both mode and appliedIntent belong to this worker state, not the newest UI request.
        return new UiPublisher.State<>(appliedIntent,new UiSnapshot(mode,midiStatus,lightingStatus,
            audioStatus+"\n"+nativeStatus+(audioFocus?"":" · 无音频焦点"),last,inputMode+"\n数据包 "+packets+"\nRAW "+raw,
            lightingFrame,flashes.clone(),holds.clone()));
    }
    private void publishUi(UiSnapshot s){
        grid.mode=s.mode;renderModes(s.mode);connection.setText(s.midi);lightingText.setText(s.lighting);audio.setText(s.audio);events.setText(s.event);rawText.setText(s.raw);
        grid.flashes=s.flashes;grid.holds=s.holds;grid.rgb=s.frame;grid.invalidate();
    }
    @Override public void onResume(){super.onResume();work.post(()->{resumed=true;requestFocus();lighting.foreground(true);});}
    @Override public void onStop(){super.onStop();grid.cancelTouches();work.post(()->{resumed=false;lighting.foreground(false);stopSounds();if(focus!=null)audioManager.abandonAudioFocusRequest(focus);audioFocus=false;});}
    @Override public void onDestroy(){super.onDestroy();work.post(()->{destroyed=true;uiPublisher.close();stopSounds();if(pool!=null){pool.release();pool=null;}if(nativeAudio!=null)nativeAudio.close();lighting.destroy(()->thread.quitSafely());});}

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(grid!=null && !focused)grid.cancelTouches();
        if(work!=null)work.post(()->{windowFocused=focused;if(!focused){stopSounds();lighting.performanceActive(false);}else if(resumed)requestFocus();});
    }

    private final class Grid extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final SparseIntArray pointers=new SparseIntArray();
        Instruments.Mode mode=Instruments.Mode.EASY;
        RgbFrame rgb=RgbFrame.BLACK;
        long[] flashes=new long[64];int[] holds=new int[64];float left,top,pad,gap;
        Grid(Context c){super(c);setContentDescription("64格音频打击垫，最下方为PAD1到8。可多点触摸试听。");}
        void geometry(){float size=Math.min(getWidth()-dp(12),getHeight()-dp(12));gap=dp(5);pad=(size-7*gap)/8;left=(getWidth()-size)/2;top=(getHeight()-size)/2;}
        int cell(float x,float y){geometry();int col=(int)Math.floor((x-left)/(pad+gap)),row=(int)Math.floor((y-top)/(pad+gap));if(col<0||col>7||row<0||row>7)return -1;if(x-left-col*(pad+gap)>pad||y-top-row*(pad+gap)>pad)return -1;int cell=row*8+col;return mode==Instruments.Mode.KEY && Instruments.key(cell)<0?-1:cell;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);geometry();long now=SystemClock.uptimeMillis();boolean animate=false;
            for(int i=0;i<64;i++){int sample=PadProtocol.sample(i);float age=(now-flashes[i])/600f;float glow=flashes[i]==0?0:Math.max(0,1-age);if(holds[i]>0)glow=Math.max(glow,.35f);if(age<1 && flashes[i]>0)animate=true;
                float x=left+(i%8)*(pad+gap),y=top+(i/8)*(pad+gap);RectF rect=new RectF(x,y,x+pad,y+pad);
                int pitch=Instruments.pitch(mode,i);boolean disabled=mode==Instruments.Mode.KEY && pitch<0;boolean black=mode==Instruments.Mode.KEY && pitch>=0 && (pitch%12==1 || pitch%12==3 || pitch%12==6 || pitch%12==8 || pitch%12==10);
                int color=Color.HSVToColor(new float[]{(sample%16)*22.5f,.68f,.95f});
                p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(Math.max(25+(int)(glow*25),rgb.component(i,0)*2),Math.max(30+(int)(glow*37),rgb.component(i,1)*2),Math.max(41+(int)(glow*48),rgb.component(i,2)*2)));if(mode==Instruments.Mode.KEY)p.setColor(disabled?0xFF151820:black?Color.rgb(24+(int)(glow*80),25,35):Color.rgb(210,210-(int)(glow*70),200));canvas.drawRoundRect(rect,dp(7),dp(7),p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(glow>.1f?2:1));p.setColor(color);p.setAlpha((int)(80+glow*175));canvas.drawRoundRect(rect,dp(7),dp(7),p);p.setAlpha(255);p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(dp(15));p.setColor(mode==Instruments.Mode.KEY && !black?0xFF222222:Color.WHITE);canvas.drawText(mode==Instruments.Mode.KEY?(disabled?"—":Instruments.name(pitch)):mode==Instruments.Mode.DRUM?""+pitch:String.format(Locale.ROOT,"%02d",sample+1),x+pad/2,y+pad*.52f,p);
                p.setTypeface(Typeface.DEFAULT);p.setTextSize(dp(8));p.setColor(0xFFA9B7CB);canvas.drawText(mode==Instruments.Mode.KEY?(disabled?"停用":"PIANO"):mode==Instruments.Mode.DRUM?Instruments.GM[Instruments.drum(pitch)-36]:sample<16?DRUMS[sample]:"PLUCK",x+pad/2,y+pad*.80f,p);
            }if(animate)postInvalidateOnAnimation();
        }
        void cancelTouches(){for(int i=0;i<pointers.size();i++){String owner="touch:"+pointers.keyAt(i);work.post(()->release(owner));}pointers.clear();}
        @Override public boolean onTouchEvent(MotionEvent event){int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_CANCEL){cancelTouches();return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){int id=event.getPointerId(event.getActionIndex());pointers.delete(id);work.post(()->release("touch:"+id));performClick();return true;}
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_MOVE){
                for(int i=0;i<event.getPointerCount();i++){int id=event.getPointerId(i),current=cell(event.getX(i),event.getY(i)),previous=pointers.get(id,-1);if(current!=previous){pointers.put(id,current);work.post(()->{release("touch:"+id);if(current>=0)hit("touch:"+id,current,100,"屏幕触摸",true);});}}return true;
            }return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
