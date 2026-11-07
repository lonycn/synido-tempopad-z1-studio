package local.orange.z1androidtest;

public final class ModeFeedbackTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        for(Instruments.Mode applied:Instruments.Mode.values()){
            int selected=0;
            for(Instruments.Mode button:Instruments.Mode.values()){
                String label=Instruments.modeLabel(applied,button);
                check(label.endsWith(button.name()));
                check(label.startsWith("● ")==(applied==button));
                if(label.startsWith("● "))selected++;
            }
            check(selected==1);
            for(int note=-1;note<=128;note++){
                int cell=Instruments.inputCell(applied,note);
                if(applied==Instruments.Mode.KEY){
                    check((cell>=0)==(note>=36 && note<=84));
                    if(cell>=0){check(Instruments.key(cell)==note);for(int i=0;i<cell;i++)check(Instruments.key(i)!=note);}
                }else{
                    check(cell==PadProtocol.cell(note));
                    if(cell>=0)check(PadProtocol.drum(cell)==note);
                }
            }
        }
        // No guessed correction from three reported corners: preserve documented baseline.
        check(Instruments.inputCell(Instruments.Mode.DRUM,45)==41);
        check(Instruments.inputCell(Instruments.Mode.DRUM,49)==33);
        check(Instruments.inputCell(Instruments.Mode.DRUM,56)==16);
        check(Instruments.inputCell(Instruments.Mode.DRUM,100)==-1);
        check(Instruments.inputCell(Instruments.Mode.KEY,-1)==-1);
        System.out.println("ModeFeedbackTest passed: "+checks+" checks; one applied-mode label, routing equivalence, KEY first owner, unmapped boundaries");
    }
}
