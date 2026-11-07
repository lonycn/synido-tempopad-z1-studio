package local.orange.z1androidtest;

import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/** Worker-confined coalescing; UI may only read revision/closed and acknowledge on worker. */
final class UiPublisher<T> {
    interface Scheduler {
        void worker(Runnable task);
        void delayedWorker(Runnable task);
        void ui(Runnable task);
    }
    static final class State<T> {
        final long appliedGeneration;
        final T value;
        State(long appliedGeneration,T value){this.appliedGeneration=appliedGeneration;this.value=value;}
    }
    private final Scheduler scheduler;
    private final Supplier<State<T>> source;
    private final LongPredicate currentGeneration;
    private final Consumer<T> publish;
    private volatile long revision;
    private volatile boolean closed;
    private boolean pending;

    UiPublisher(Scheduler scheduler,Supplier<State<T>> source,LongPredicate currentGeneration,Consumer<T> publish){
        this.scheduler=scheduler;this.source=source;this.currentGeneration=currentGeneration;this.publish=publish;
    }
    void changed(){
        if(closed)return;
        revision++;
        enqueue();
    }
    private void enqueue(){
        if(pending || closed)return;
        pending=true;
        scheduler.delayedWorker(this::capture);
    }
    private void capture(){
        if(closed)return;
        final long capturedRevision=revision;
        final State<T> state=source.get();
        scheduler.ui(()->{
            try {
                if(!closed && revision==capturedRevision && currentGeneration.test(state.appliedGeneration))publish.accept(state.value);
            } finally {
                scheduler.worker(()->{
                    pending=false;
                    // Changes while capture/delivery was pending must not be lost, even in the same mode generation.
                    if(revision!=capturedRevision)enqueue();
                });
            }
        });
    }
    void close(){closed=true;}
}
