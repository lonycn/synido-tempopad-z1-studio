package local.orange.z1androidtest;

/** Atomic attempt reservation shared across every controller/worker in one Android process. */
public final class SendAttemptLimiter {
    private long lastAttempt=Long.MIN_VALUE;
    public synchronized long delay(long now){
        return lastAttempt==Long.MIN_VALUE?0:Math.max(0,LightingState.INTERVAL_MS-(now-lastAttempt));
    }
    public synchronized boolean reserve(long now){
        if(delay(now)>0)return false;
        lastAttempt=now;return true;
    }
}
