package local.orange.z1androidtest;

import java.util.*;

/** Deterministic scheduling of the production coalescer used by MainActivity. No Android doubles. */
public final class UiPublisherTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Queues implements UiPublisher.Scheduler {
        final ArrayDeque<Runnable> worker=new ArrayDeque<>(),capture=new ArrayDeque<>(),ui=new ArrayDeque<>();
        public void worker(Runnable r){worker.add(r);}
        public void delayedWorker(Runnable r){capture.add(r);}
        public void ui(Runnable r){ui.add(r);}
        void work(){worker.remove().run();}
        void capture(){capture.remove().run();}
        void deliver(){ui.remove().run();}
        void flush(){int steps=0;while(!worker.isEmpty() || !capture.isEmpty() || !ui.isEmpty()){
            check(++steps<100,"publisher must quiesce, not busy-retry rejected generations");
            if(!worker.isEmpty())work();else if(!capture.isEmpty())capture();else deliver();
        }}
    }
    private static final class Fixture {
        final Queues q=new Queues();final Instruments.ModeIntent intent=new Instruments.ModeIntent();
        Instruments.Mode audio=Instruments.Mode.KEY,grid=Instruments.Mode.EASY;
        long applied=intent.manual(Instruments.Mode.KEY);int event,shownEvent,captures;Runnable afterModeRead;
        final List<Instruments.Mode> deliveries=new ArrayList<>();
        final UiPublisher<ViewState> publisher=new UiPublisher<>(q,()->{
            captures++;Instruments.Mode read=audio;
            if(afterModeRead!=null){Runnable r=afterModeRead;afterModeRead=null;r.run();}
            // Identical to MainActivity captureUi: stamp actual worker-applied state, never requested intent.
            return new UiPublisher.State<>(applied,new ViewState(read,event));
        },intent::current,s->{grid=s.mode;shownEvent=s.event;deliveries.add(s.mode);});
        void manual(Instruments.Mode choice){long token=intent.manual(choice);q.worker(()->{
            if(!intent.current(token))return;applied=token;audio=choice;publisher.changed();
        });}
        void automaticEasy(){audio=Instruments.Mode.EASY;publisher.changed();}
        void converged(Instruments.Mode expected){check(audio==expected && grid==audio,"grid layout must converge to audio route without external event");}
    }
    private static final class ViewState {
        final Instruments.Mode mode;final int event;
        ViewState(Instruments.Mode mode,int event){this.mode=mode;this.event=event;}
    }
    private static void reviewerInterleaving(){
        Fixture f=new Fixture();f.publisher.changed();
        // Old KEY read, then UI advances intent before the rest of the old snapshot is assembled.
        f.afterModeRead=()->f.manual(Instruments.Mode.DRUM);f.q.capture();
        f.q.work(); // DRUM applies while old UI publication is pending.
        check(f.q.capture.isEmpty(),"only one publication may be pending");
        f.q.deliver();check(f.deliveries.isEmpty(),"old KEY must not acquire DRUM's requested generation");
        f.q.flush();f.converged(Instruments.Mode.DRUM);
        check(f.deliveries.equals(Arrays.asList(Instruments.Mode.DRUM)),"only actual DRUM state accepted");
        check(Instruments.key(0)<0 && Instruments.pitch(f.grid,0)>=0,"stale KEY filtering cannot strand a valid DRUM cell");
    }
    private static void requestDuringCaptureBeforeApply(){
        Fixture f=new Fixture();f.publisher.changed();f.afterModeRead=()->f.manual(Instruments.Mode.DRUM);f.q.capture();
        // No applied-state revision has changed yet: only the correct applied generation can reject this snapshot.
        f.q.deliver();check(f.deliveries.isEmpty(),"old KEY capture must keep old applied token even before DRUM applies");
        f.q.flush();f.converged(Instruments.Mode.DRUM);
    }
    private static void rejectionBeforeApply(){
        Fixture f=new Fixture();f.publisher.changed();f.q.capture();f.manual(Instruments.Mode.DRUM);
        f.q.deliver();check(f.deliveries.isEmpty(),"requested manual generation rejects older applied snapshot");
        f.q.flush();f.converged(Instruments.Mode.DRUM);
    }
    private static void sameGenerationChanges(){
        Fixture f=new Fixture();f.publisher.changed();f.q.capture();long generation=f.applied;
        f.automaticEasy();f.q.deliver();check(f.deliveries.isEmpty(),"same-generation automatic transition invalidates old revision");
        f.q.flush();check(f.applied==generation,"automatic EASY keeps manual generation");f.converged(Instruments.Mode.EASY);
        f.publisher.changed();f.q.capture();f.event=73;f.publisher.changed();
        f.q.deliver();f.q.flush();check(f.shownEvent==73,"non-mode changes between capture/delivery cannot be lost");
    }
    private static void changeAfterDeliveryBeforeAck(){
        Fixture f=new Fixture();f.publisher.changed();f.q.capture();f.q.deliver();
        check(f.grid==Instruments.Mode.KEY,"first current snapshot accepted");f.automaticEasy();
        check(f.q.capture.isEmpty(),"publication remains pending until worker acknowledgement");
        f.q.flush();f.converged(Instruments.Mode.EASY);
    }
    private static void changesInsideCaptureAndBurst(){
        Fixture f=new Fixture();f.publisher.changed();f.afterModeRead=()->{f.event=1;f.publisher.changed();};
        f.q.capture();f.q.deliver();check(f.deliveries.isEmpty(),"changes while snapshot source runs retain dirty revision");
        f.q.flush();check(f.shownEvent==1,"source-side changes eventually publish");int before=f.captures;
        for(int i=0;i<1000;i++){f.event=i;f.publisher.changed();}
        f.q.flush();check(f.shownEvent==999 && f.captures==before+1,"burst coalesces to one latest snapshot");
    }
    private static void newerManualAndClose(){
        Fixture f=new Fixture();f.publisher.changed();f.q.capture();f.manual(Instruments.Mode.DRUM);f.manual(Instruments.Mode.EASY);
        f.q.flush();f.converged(Instruments.Mode.EASY);check(!f.deliveries.contains(Instruments.Mode.DRUM),"superseded manual task cannot publish");
        f.publisher.changed();f.q.capture();int before=f.deliveries.size();f.publisher.close();f.q.flush();
        f.publisher.changed();check(f.deliveries.size()==before && f.q.capture.isEmpty(),"close suppresses stale delivery and future capture");
    }
    public static void main(String[] args){
        reviewerInterleaving();requestDuringCaptureBeforeApply();rejectionBeforeApply();sameGenerationChanges();changeAfterDeliveryBeforeAck();changesInsideCaptureAndBurst();newerManualAndClose();
        System.out.println("UiPublisherTest passed: "+checks+" checks; production applied-generation capture/delivery, revisions, automatic EASY, acknowledgements, coalescing and close");
    }
}
