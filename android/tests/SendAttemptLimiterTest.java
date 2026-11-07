package local.orange.z1androidtest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class SendAttemptLimiterTest {
    static int checks;
    static void check(boolean condition,String detail){checks++;if(!condition)throw new AssertionError(detail);}
    public static void main(String[] args) throws Exception {
        SendAttemptLimiter limiter=new SendAttemptLimiter();
        CountDownLatch ready=new CountDownLatch(16),start=new CountDownLatch(1);
        AtomicInteger reserved=new AtomicInteger(),errors=new AtomicInteger();Thread[] workers=new Thread[16];
        for(int i=0;i<workers.length;i++){
            workers[i]=new Thread(()->{
                ready.countDown();
                try{start.await();if(limiter.reserve(1000))reserved.incrementAndGet();}
                catch(InterruptedException ex){errors.incrementAndGet();}
            });workers[i].start();
        }
        ready.await();start.countDown();for(Thread worker:workers)worker.join();
        check(errors.get()==0 && reserved.get()==1,"only one simultaneous worker reserves an attempt");
        check(!limiter.reserve(1099) && limiter.delay(1099)==1,"failed/successful attempt histories share the same clock");
        check(limiter.reserve(1100),"exact boundary");
        LightingState old=new LightingState(limiter),replacement=new LightingState(limiter);
        old.connected();old.authorize();old.clear();replacement.connected();replacement.authorize();replacement.render(LightingTest.red());
        check(old.beginAttempt(1200).isBlack(),"old terminal reserves process attempt");
        check(replacement.beginAttempt(1220)==null,"new state cannot reset old attempt history");
        check(replacement.beginAttempt(1300)!=null,"new state resumes at shared boundary");
        System.out.println("SEND LIMITER PASS: "+checks+" checks; 16 simultaneous threads and cross-state terminal attempt history");
    }
}
