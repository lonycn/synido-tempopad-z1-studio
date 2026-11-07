package local.orange.z1androidtest;
import java.util.*;
public final class InstrumentsTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        Set<Integer> pitches=new HashSet<>();int enabled=0,disabled=0;
        Map<Integer,Integer> repeat=new HashMap<>();
        for(int cell=0;cell<64;cell++){
            int r=7-cell/8,c=cell%8;
            check(PadProtocol.drum(cell)==36+4*r+c%4+32*(c/4));
            int drum=Instruments.drum(PadProtocol.drum(cell));check(drum>=36 && drum<=81);
            if(PadProtocol.drum(cell)<=81)check(drum==PadProtocol.drum(cell));
            else check(drum==Instruments.EXTENDED[PadProtocol.drum(cell)-82]);
            int key=Instruments.key(cell);if(key<0)disabled++;else {enabled++;pitches.add(key);repeat.put(key,repeat.getOrDefault(key,0)+1);}
        }
        check(enabled==52 && disabled==12 && pitches.size()==49);
        for(int key=36;key<=84;key++)check(repeat.get(key)==((key==48 || key==60 || key==72)?2:1));
        check(Instruments.dynamics(true,100).equals("无力度数据 / 固定音量"));
        for(int v=1;v<=127;v++)check(Instruments.dynamics(false,v).equals("力度 "+v+"/127"));
        List<String> events=new ArrayList<>();
        Instruments.Owners owners=new Instruments.Owners(new Instruments.Sink(){
            public void on(int n,int v){events.add("on:"+n+":"+v);}public void off(int n){events.add("off:"+n);}
            public void sustain(boolean d){events.add("pedal:"+d);}public void panic(){events.add("panic");}
        });
        owners.press("physical",60,7);owners.press("touch",60,127);owners.release("physical");check(events.equals(Arrays.asList("on:60:7")));
        owners.release("touch");check(events.get(1).equals("off:60"));owners.press("drag",61,83);owners.press("drag",62,84);check(events.contains("off:61"));
        owners.pedal("CH1",true);owners.pedal("CH3",true);int before=events.size();owners.pedal("CH1",false);check(events.size()==before);owners.pedal("CH3",false);check(events.get(events.size()-1).equals("pedal:false"));
        owners.pedal("CH1",true);owners.release("drag");owners.clear();check(events.get(events.size()-2).equals("pedal:false"));check(events.get(events.size()-1).equals("panic"));check(owners.owners.isEmpty());
        Instruments.ModeIntent intent=new Instruments.ModeIntent();long auto=intent.generation();long manual=intent.manual(Instruments.Mode.KEY);check(!intent.current(auto));check(intent.current(manual));check(intent.requested()==Instruments.Mode.KEY);check(!intent.current(manual-1));
        // Every mode transition uses the same owner/sustain panic primitive, including reselect.
        for(Instruments.Mode from:Instruments.Mode.values())for(Instruments.Mode to:Instruments.Mode.values()){
            owners.press("physical",60,91);owners.press("audition:1",60,100);owners.pedal("CH1",true);owners.clear();
            owners.press("audition:2",60,100);int count=events.size();owners.release("audition:1");check(events.size()==count);owners.release("audition:2");check(events.get(events.size()-1).equals("off:60"));
            long stale=intent.generation();long next=intent.manual(to);check(!intent.current(stale) && intent.current(next) && intent.requested()==to);
        }
        for(int velocity=1;velocity<=127;velocity++){owners.press("velocity",60,velocity);check(events.get(events.size()-1).equals("on:60:"+velocity));owners.release("velocity");}
        System.out.println("InstrumentsTest passed: "+checks+" checks");
    }
}
