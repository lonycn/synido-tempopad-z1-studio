package local.orange.z1androidtest;

/** Generation ownership for asynchronous open/receive/close callbacks. Confined to one worker. */
public final class LifecycleGate {
    private int generation;
    private boolean foreground,closing,destroyed;
    public void foreground(boolean value){if(!destroyed)foreground=value;}
    public boolean mayOpen(){return foreground && !closing && !destroyed;}
    public int beginOpen(){return ++generation;}
    public boolean accepts(int token){return token==generation && mayOpen();}
    public int beginClose(){closing=true;return ++generation;}
    public boolean ownsClose(int token){return token==generation && closing;}
    public boolean closing(){return closing;}
    public void finishClose(int token){if(ownsClose(token))closing=false;}
    public void destroy(){destroyed=true;foreground=false;}
}
