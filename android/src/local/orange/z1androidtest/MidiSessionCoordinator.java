package local.orange.z1androidtest;

import java.util.LinkedHashMap;

/** Process-wide USB session lease: replacement workers wait for the old worker to close its ports. */
final class MidiSessionCoordinator {
    static final MidiSessionCoordinator PROCESS=new MidiSessionCoordinator();
    final SendAttemptLimiter limiter=new SendAttemptLimiter();
    private static final class Request {
        final Object token;final Runnable acquired,retire;
        Request(Object token,Runnable acquired,Runnable retire){this.token=token;this.acquired=acquired;this.retire=retire;}
    }
    private final LinkedHashMap<Object,Request> waiting=new LinkedHashMap<>();
    private Request owner;
    private boolean retiring;
    // Callbacks only enqueue onto their own Handler; never perform device I/O under this lock.
    synchronized void acquire(Object token,Runnable acquired,Runnable retire){
        waiting.put(token,new Request(token,acquired,retire));
        if(owner==null)promote();
        else if(!retiring){retiring=true;owner.retire.run();}
    }
    synchronized boolean owns(Object token){return owner!=null && owner.token==token;}
    synchronized void release(Object token){
        waiting.remove(token);
        if(owns(token)){owner=null;retiring=false;promote();}
    }
    private void promote(){
        if(waiting.isEmpty())return;
        Object token=waiting.keySet().iterator().next();owner=waiting.remove(token);owner.acquired.run();
        if(!waiting.isEmpty()){retiring=true;owner.retire.run();}
    }
}
