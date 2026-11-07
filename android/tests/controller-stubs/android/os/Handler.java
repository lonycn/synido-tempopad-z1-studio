package android.os;
import java.util.*;
/** Deterministic single-worker scheduler, never used by the APK. */
public final class Handler {
    private static final class Task {Runnable run;long time,order;Task(Runnable r,long t,long o){run=r;time=t;order=o;}}
    private static long sequence;
    private final ArrayList<Task> tasks=new ArrayList<>();
    public boolean post(Runnable r){return postDelayed(r,0);}
    public boolean postDelayed(Runnable r,long delay){tasks.add(new Task(r,SystemClock.time+delay,sequence++));return true;}
    public void removeCallbacks(Runnable r){tasks.removeIf(t->t.run==r);}
    public void until(long target){advanceTo(target,this);}
    public static void advanceTo(long target,Handler... workers){
        int count=0;
        for(;;){
            Handler selected=null;Task next=null;
            for(Handler worker:workers)for(Task task:worker.tasks){
                if(next==null || task.time<next.time || (task.time==next.time && task.order<next.order)){next=task;selected=worker;}
            }
            if(next==null || next.time>target)break;
            if(++count>10000)throw new AssertionError("scheduler spinning");
            selected.tasks.remove(next);SystemClock.time=Math.max(SystemClock.time,next.time);next.run.run();
        }
        SystemClock.time=target;
    }
    public int size(){return tasks.size();}
}
