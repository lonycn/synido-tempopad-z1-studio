package local.orange.z1androidtest;

/** Pure latest-only drain; production injects the process-scoped atomic attempt limiter. */
public final class LightingState {
    public static final long INTERVAL_MS=100;
    private final SendAttemptLimiter limiter;
    public LightingState(){this(new SendAttemptLimiter());}
    public LightingState(SendAttemptLimiter limiter){this.limiter=limiter;}
    private boolean authorized,disabled,terminal;
    private int failures;
    private RgbFrame rendered=RgbFrame.BLACK,succeeded,pending;
    public void connected(){revoke();disabled=false;failures=0;succeeded=null;}
    public void authorize(){if(!disabled)authorized=true;}
    public void revoke(){authorized=false;terminal=false;pending=null;rendered=RgbFrame.BLACK;}
    public boolean authorized(){return authorized && !disabled;}
    public boolean disabled(){return disabled;}
    public RgbFrame rendered(){return rendered;}
    public RgbFrame succeeded(){return succeeded;}
    public boolean pending(){return pending!=null;}
    public int failures(){return failures;}
    public void render(RgbFrame frame){
        rendered=frame;
        if(!authorized() || terminal)return;
        pending=frame.equals(succeeded)?null:frame;
    }
    public void clear(){
        rendered=RgbFrame.BLACK;
        if(!authorized())return;
        terminal=true;pending=RgbFrame.BLACK.equals(succeeded)?null:RgbFrame.BLACK;
    }
    public void resumeEffects(){if(pending==null)terminal=false;}
    public long delay(long now){return limiter.delay(now);}
    /** Marks an attempt BEFORE any I/O, including a failed send. Never reset this clock on clear/reconnect. */
    public RgbFrame beginAttempt(long now){
        if(!authorized() || pending==null || !limiter.reserve(now))return null;
        return pending;
    }
    public void completeAttempt(RgbFrame frame,boolean success){
        if(success){succeeded=frame;failures=0;if(frame.equals(pending))pending=null;if(terminal && frame.isBlack())terminal=false;}
        else if(++failures>=3){disabled=true;revoke();}
    }
}
