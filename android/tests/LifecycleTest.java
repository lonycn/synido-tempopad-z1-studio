package local.orange.z1androidtest;

public final class LifecycleTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        LifecycleGate gate=new LifecycleGate();check(!gate.mayOpen(),"background does not open");
        gate.foreground(true);int first=gate.beginOpen();check(gate.accepts(first),"foreground open");
        int closing=gate.beginClose();check(!gate.accepts(first),"close invalidates pending open/receive");
        gate.foreground(false);gate.foreground(true);check(!gate.mayOpen(),"rapid resume waits for bounded cleanup");
        gate.finishClose(closing);check(gate.mayOpen(),"resume after cleanup");
        int second=gate.beginOpen();check(!gate.accepts(first) && gate.accepts(second),"stale receive/open cannot affect new device");
        gate.finishClose(closing);check(gate.accepts(second) && !gate.ownsClose(closing),"stale close cannot close replacement");
        int closeSecond=gate.beginClose();gate.destroy();check(!gate.accepts(second) && !gate.mayOpen(),"destroy invalidates callbacks");
        gate.foreground(true);check(!gate.mayOpen(),"destroy cannot resume");
        gate.finishClose(closeSecond);check(!gate.mayOpen(),"destroy finishes only its cleanup");
        for(int i=0;i<1000;i++){
            LifecycleGate g=new LifecycleGate();g.foreground(true);int token=g.beginOpen();int close=g.beginClose();
            g.foreground(false);g.foreground(true);check(!g.accepts(token),"rapid lifecycle generation");
            g.finishClose(close);int replacement=g.beginOpen();check(g.accepts(replacement),"replacement accepted");
            check(!g.ownsClose(close),"old close timer rejected");
        }
        System.out.println("LIFECYCLE PASS: "+checks+" checks; stale open/receive/close, rapid stop/resume, destroy");
    }
}
