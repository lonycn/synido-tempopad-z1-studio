package local.orange.z1androidtest;

import android.hardware.usb.UsbDevice;
import android.media.midi.*;
import android.os.*;
import java.util.*;

/** Host-only deterministic Android MIDI doubles; never opens any real port. */
public final class MidiLightingTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static MidiDeviceInfo z1(int id){return new MidiDeviceInfo(id,MidiDeviceInfo.TYPE_USB,new UsbDevice(10467,4101));}
    static final class Fixture implements MidiLighting.Listener {
        final Handler worker=new Handler();final MidiManager manager=new MidiManager();final MidiDeviceInfo info=z1(1);
        final MidiLighting controller;
        int messages,disconnects;String status,connection;RgbFrame frame;
        Fixture(){this(new MidiSessionCoordinator(),true);}
        Fixture(MidiSessionCoordinator sessions){this(sessions,false);}
        Fixture(MidiSessionCoordinator sessions,boolean reset){
            if(reset)SystemClock.time=0;
            controller=sessions==null?new MidiLighting(manager,worker,this):new MidiLighting(manager,worker,this,sessions);
            manager.infos.add(info);controller.start();controller.performanceActive(true);
        }
        void open(){controller.foreground(true);at(SystemClock.time);}
        MidiDevice device(){return manager.opened.get(manager.opened.size()-1);}
        void at(long time){worker.until(time);}
        void easy(int index){device().output.emit(0xF0,index,0xF7);at(SystemClock.time);}
        public void message(int status,int[] data){messages++;}
        public void packet(byte[] bytes){}
        public void state(String connection,String lighting,RgbFrame frame){this.connection=connection;this.status=lighting;this.frame=frame;}
        public void disconnected(){disconnects++;}
    }
    static boolean black(byte[] wire){for(int i=2;i<258;i+=4)for(int c=1;c<=3;c++)if(wire[i+c]!=0)return false;return true;}
    static void paced(List<Long> attempts){for(int i=1;i<attempts.size();i++)check(attempts.get(i)-attempts.get(i-1)>=100,"controller attempts >=100ms");}
    public static void main(String[] args){
        authorization();ambiguity();lifecycle();cleanupNote();failures();staleOpen();missingOutput();replacementWorkers();naturalTerminal();setupFailures();
        System.out.println("MIDI LIGHTING PASS: "+checks+" checks; fake Android ports only; authorization, same-device USB, ambiguity, cleanup Note cancellation, lifecycle, retries, idle, cross-worker handoff, natural terminal, stable setup errors");
    }
    static void authorization(){
        Fixture f=new Fixture();f.open();MidiDevice d=f.device();
        check(d.inputOpens==1 && d.outputOpens==1,"same opened device owns read and RGB ports");
        f.at(50);check(d.input.attempts.isEmpty(),"connect alone never sends");
        d.output.emit(0x90,36,100,0xF0,1,2,0xF7);f.at(50);
        check(d.input.attempts.isEmpty() && f.messages==2,"standard Note / unknown SysEx audio only");
        d.input.onAttempt=()->check(f.messages>=3,"sample/event callback precedes physical I/O");
        f.easy(0);check(d.input.sent.size()==1 && !black(d.input.sent.get(0)),"genuine EASY press diffuses");
        check(f.messages==3,"audio event handled before scheduled send");
        f.at(75);f.controller.panic();f.at(100);check(d.input.sent.size()==1,"panic cannot bypass pacing");
        d.output.emit(0x99,60,100);f.at(100);f.at(200);
        check(d.input.sent.size()==1 && f.frame.isBlack(),"all-channel Notes cancel pending black without output");
        f.easy(64);check(d.input.sent.size()==1,"EASY release grants no wave");
        f.controller.setEnabled(false);f.at(200);int count=d.input.sent.size();f.easy(1);f.at(400);
        check(d.input.sent.size()==count,"disabled control does not diffuse");
        f.controller.setEnabled(true);f.at(450);check(d.input.sent.size()==count,"enable alone never triggers");
        f.easy(2);f.at(1700);check(black(d.input.sent.get(d.input.sent.size()-1)),"diffusion ends in physical black");
        check(f.worker.size()==0,"idle stops all lighting timers");paced(d.input.attempts);
    }
    static void ambiguity(){
        Fixture f=new Fixture();f.open();MidiDevice first=f.device();f.easy(0);f.at(50);
        f.manager.add(z1(2));f.at(50);check(first.closed && first.input.closed && first.output.closed,"second Z-1 closes first, no blind clear");
        f.at(500);check(first.input.sent.size()==1,"no sends after ambiguity");
        check(f.manager.opened.size()==1,"multiple Z-1 fail closed");
        Fixture unknown=new Fixture();unknown.manager.infos.clear();
        unknown.manager.infos.add(new MidiDeviceInfo(3,2,new UsbDevice(10467,4101)));
        unknown.manager.infos.add(new MidiDeviceInfo(4,1,new UsbDevice(1,4101)));
        unknown.manager.infos.add(new MidiDeviceInfo(5,1,null));unknown.open();
        check(unknown.manager.opened.isEmpty(),"virtual, unknown USB and missing USB identity never opened");
    }
    static void lifecycle(){
        Fixture f=new Fixture();f.open();MidiDevice old=f.device();f.easy(0);f.at(50);
        f.controller.foreground(false);f.at(60);f.controller.foreground(true);
        check(!old.closed && f.manager.opened.size()==1,"rapid foreground waits for black deadline");
        f.at(100);check(old.closed && black(old.input.sent.get(1)),"paced terminal black before close");
        check(f.manager.opened.size()==2,"automatic reconnect after cleanup");MidiDevice next=f.device();
        old.output.emit(0xF0,1,0xF7);f.at(110);check(next.input.attempts.isEmpty(),"old receiver cannot authorize new device");
        f.easy(1);f.at(199);check(next.input.attempts.isEmpty(),"new connection retains last attempt timestamp");
        f.at(200);check(next.input.attempts.get(0)==200,"new connection at pacing boundary");
        f.at(220);final boolean[] done={false};f.controller.destroy(()->done[0]=true);
        f.at(300);check(done[0] && next.closed && black(next.input.sent.get(next.input.sent.size()-1)),"destroy black then dispose asynchronously");
        f.at(1000);check(f.manager.opened.size()==2,"destroy cannot auto-reconnect");
    }
    static void cleanupNote(){
        Fixture f=new Fixture();f.open();MidiDevice d=f.device();f.easy(0);f.at(50);f.controller.foreground(false);
        d.output.emit(0x90,36,100);f.at(60);f.at(500);
        check(d.closed && d.input.sent.size()==1,"new Note during cleanup revokes terminal black");
        check(f.messages==1,"cleanup input cannot revive audio");
        Fixture unplug=new Fixture();unplug.open();MidiDevice lost=unplug.device();unplug.easy(1);unplug.at(40);
        unplug.manager.remove(unplug.info);unplug.at(500);
        check(lost.closed && lost.input.sent.size()==1,"unplug closes without claiming black delivery");
    }
    static void failures(){
        Fixture f=new Fixture();f.open();MidiDevice d=f.device();d.input.fail=true;f.easy(0);f.at(250);
        check(d.input.attempts.size()==3 && d.input.closed && !d.closed,"3 paced failed attempts disable RGB only");
        int messages=f.messages;f.controller.setEnabled(false);f.controller.setEnabled(true);f.easy(1);f.at(1000);
        check(f.messages>messages && d.input.attempts.size()==3,"audio continues, EASY cannot revive failed RGB");paced(d.input.attempts);
        Fixture close=new Fixture();close.open();MidiDevice c=close.device();close.easy(0);close.at(50);c.input.fail=true;
        final boolean[] done={false};close.controller.destroy(()->done[0]=true);close.at(350);
        check(done[0] && c.closed && c.input.attempts.size()==4,"destroy bounded cleanup allows three black failures");paced(c.input.attempts);
    }
    static void staleOpen(){
        Fixture f=new Fixture();f.manager.delayOpen=true;f.open();MidiDevice old=f.device();
        f.controller.foreground(false);f.controller.foreground(true);f.at(0);check(f.manager.opened.size()==2,"new open generation");
        f.manager.completeOpens();f.at(0);
        check(old.closed && !f.device().closed && f.device().inputOpens==1,"stale async open closed without affecting replacement");
    }
    static void missingOutput(){
        Fixture f=new Fixture();f.manager.noInput=true;f.open();MidiDevice d=f.device();f.easy(0);f.at(2000);
        check(!d.closed && f.messages==1 && d.input.attempts.isEmpty(),"missing RGB port preserves receive/audio");
        check(f.worker.size()==0,"no-port effects also become idle");
    }
    static void replacementWorkers(){
        for(int failedClears:new int[]{0,1,3}){
            MidiSessionCoordinator sessions=failedClears==0?null:new MidiSessionCoordinator();
            Fixture old=new Fixture(sessions,true);old.open();MidiDevice previous=old.device();old.easy(0);old.at(50);
            previous.input.fail=failedClears>0;
            // A still-foreground old Activity must yield, not just cooperate via onStop.
            Fixture next=new Fixture(sessions);next.controller.foreground(true);
            Handler.advanceTo(60,old.worker,next.worker);
            check(next.manager.opened.isEmpty() && !previous.closed,"replacement waits for old port ownership/drain");
            if(failedClears==1){Handler.advanceTo(150,old.worker,next.worker);previous.input.fail=false;}
            long closedAt=failedClears==0?100:failedClears==1?200:300;
            Handler.advanceTo(closedAt,old.worker,next.worker);
            check(previous.closed && previous.input.closed && previous.output.closed,"old worker closes all its ports before handoff");
            check(next.manager.opened.size()==1,"replacement acquires exactly once after old cleanup");
            if(failedClears==0)check(black(previous.input.sent.get(previous.input.sent.size()-1)),"old final black delivered before replacement");
            MidiDevice replacement=next.device();
            Handler.advanceTo(closedAt+20,old.worker,next.worker);
            replacement.output.emit(0xF0,1,0xF7);previous.output.emit(0xF0,2,0xF7);
            old.controller.destroy(()->{}); // A late old lifecycle callback must not release the new lease.
            Handler.advanceTo(closedAt+99,old.worker,next.worker);
            check(replacement.input.attempts.isEmpty(),"new genuine EASY hit <100ms cannot bypass process pacing");
            Handler.advanceTo(closedAt+100,old.worker,next.worker);
            check(replacement.input.attempts.size()==1,"new worker sends at shared pacing boundary");
            Handler.advanceTo(closedAt+400,old.worker,next.worker);
            check(!replacement.closed && !replacement.input.closed,"old delayed cleanup cannot close replacement ports");
            check(old.manager.opened.size()==1,"retired old Activity never auto-reopens");
            ArrayList<Long> combined=new ArrayList<>(previous.input.attempts);combined.addAll(replacement.input.attempts);paced(combined);
            next.controller.destroy(()->{});Handler.advanceTo(closedAt+800,old.worker,next.worker);
        }
        // A waiter canceled before handoff must never open when the previous worker releases.
        MidiSessionCoordinator sessions=new MidiSessionCoordinator();Fixture old=new Fixture(sessions,true);old.open();old.easy(0);old.at(50);
        Fixture canceled=new Fixture(sessions);canceled.controller.foreground(true);canceled.controller.destroy(()->{});
        Handler.advanceTo(500,old.worker,canceled.worker);check(canceled.manager.opened.isEmpty(),"destroyed lease waiter cannot resurrect");
    }
    static void naturalTerminal(){
        for(int terminalFailures:new int[]{0,1,3}){
            Fixture f=new Fixture();f.open();MidiDevice d=f.device();f.easy(0);f.at(1100);
            check(d.input.attempts.get(d.input.attempts.size()-1)==1100 && !black(d.input.sent.get(d.input.sent.size()-1)),"corner ring still nonblack at 1100");
            f.at(1150);int sent=d.input.sent.size();d.input.fail=terminalFailures>0;
            f.at(1175);f.easy(1);f.at(1199);check(d.input.sent.size()==sent,"natural terminal black survives hit during throttle");
            f.at(1200);
            if(terminalFailures==0){check(d.input.sent.size()==sent+1 && black(d.input.sent.get(sent)),"natural black delivered before new wave");}
            else {check(d.input.sent.size()==sent,"failed terminal attempt does not replace black with new effect");}
            if(terminalFailures==1){d.input.fail=false;f.at(1300);check(black(d.input.sent.get(sent)),"failed terminal black retries first");}
            if(terminalFailures<3){
                long resumed=terminalFailures==0?1300:1400;f.at(resumed);
                check(!black(d.input.sent.get(d.input.sent.size()-1)),"new wave retained and resumes after terminal success");
                f.at(2400);check(black(d.input.sent.get(d.input.sent.size()-1)),"new wave also ends in black");
            }else{
                f.at(2000);check(d.input.attempts.size()==15 && d.input.closed,"natural terminal failure bounded to three attempts");
                check(!d.closed,"terminal failure leaves receive/audio available");
            }
            paced(d.input.attempts);check(f.worker.size()==0,"natural terminal path returns idle");
        }
        Fixture delayed=new Fixture();delayed.open();MidiDevice d=delayed.device();delayed.easy(0);delayed.at(1100);
        SystemClock.time=1140;d.output.emit(0xF0,1,0xF7);
        SystemClock.time=1175;delayed.at(1175); // A pre-tick input is processed after the old wave expired.
        delayed.at(1200);check(black(d.input.sent.get(d.input.sent.size()-1)),"delayed worker latches old expiry before appending new wave");
        delayed.at(1300);check(!black(d.input.sent.get(d.input.sent.size()-1)),"delayed-worker new wave retained");
    }
    static void setupFailures(){
        for(int failure=0;failure<6;failure++){
            Fixture f=new Fixture();
            f.manager.noReceive=failure==0;f.manager.throwReceive=failure==1;f.manager.throwConnect=failure==2;
            f.manager.throwOpen=failure==3;f.manager.nullOpen=failure==4;
            if(failure==5)f.manager.delayOpen=true;
            f.open();
            if(failure==5){f.controller.foreground(false);f.controller.destroy(()->{});f.manager.completeOpens();f.at(1000);check(f.manager.openCalls==1 && f.device().closed,"stale setup completion after stop/destroy only closes old device");continue;}
            f.manager.add(new MidiDeviceInfo(90,1,new UsbDevice(1,2)));f.at(SystemClock.time);
            String error=f.connection;check(error.contains("错误") || error.contains("失败"),"stable setup error published");
            final boolean[] unrelated={false};f.worker.postDelayed(()->unrelated[0]=true,10);f.at(5000);
            check(f.manager.openCalls==1 && unrelated[0] && f.worker.size()==0,"setup failure opens once, unrelated worker remains runnable");
            check(f.connection.equals(error),"setup failure does not churn UI status");
            f.manager.noReceive=f.manager.throwReceive=f.manager.throwConnect=f.manager.throwOpen=f.manager.nullOpen=false;
            f.controller.foreground(false);f.controller.foreground(true);f.at(5100);
            check(f.manager.openCalls==1,"foreground alone does not resurrect failed setup");
            f.controller.reconnect();f.at(5100);check(f.manager.openCalls==2 && !f.device().closed,"explicit reconnect recovers setup failure");
            f.easy(0);check(!f.device().input.sent.isEmpty(),"recovered setup handles genuine EASY input");
            f.controller.destroy(()->{});f.at(6000);check(f.manager.openCalls==2,"destroy leaves no stale retry");
        }
        Fixture topology=new Fixture();topology.manager.noReceive=true;topology.open();topology.manager.noReceive=false;
        topology.manager.remove(topology.info);topology.at(100);topology.manager.add(topology.info);topology.at(200);
        check(topology.manager.openCalls==2 && !topology.device().closed,"relevant topology change recovers stable setup error");
    }

}
