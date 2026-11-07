package local.orange.z1androidtest;
import java.util.*;
public final class ProtocolTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        Set<Integer> notes=new HashSet<>(),samples=new HashSet<>();
        for(int c=0;c<64;c++){check(PadProtocol.cell(PadProtocol.drum(c))==c);notes.add(PadProtocol.drum(c));samples.add(PadProtocol.sample(c));}
        check(notes.size()==64 && samples.size()==64);check(PadProtocol.sample(56)==0);check(PadProtocol.drum(56)==36);check(PadProtocol.drum(63)==71);check(PadProtocol.drum(7)==99);check(PadProtocol.cell(35)==-1 && PadProtocol.cell(100)==-1);
        PadProtocol.Parser parser=new PadProtocol.Parser();List<String> seen=new ArrayList<>();PadProtocol.Listener sink=(s,d)->seen.add(s+":"+Arrays.toString(d));
        parser.feed(new byte[]{(byte)0x90,36,100,37,(byte)0xF8,127},0,6,sink);check(seen.equals(Arrays.asList("144:[36, 100]","144:[37, 127]")));
        parser.feed(new byte[]{38},0,1,sink);parser.feed(new byte[]{0},0,1,sink);check(seen.get(2).equals("144:[38, 0]"));
        parser.feed(new byte[]{(byte)0xF0},0,1,sink);parser.feed(new byte[]{0,(byte)0xFE},0,2,sink);parser.feed(new byte[]{(byte)0xF7},0,1,sink);check(seen.get(3).equals("240:[0]"));
        for(int i=0;i<128;i++){byte[] b={(byte)0xF0,(byte)i,(byte)0xF7};parser.feed(b,0,b.length,sink);check(seen.get(seen.size()-1).equals("240:["+i+"]"));}
        int before=seen.size();byte[] overlong=new byte[600];overlong[0]=(byte)0xF0;overlong[599]=(byte)0xF7;parser.feed(overlong,0,overlong.length,sink);check(before==seen.size());
        parser.feed(new byte[]{(byte)0xF0,1,(byte)0x90,40,100},0,5,sink);check(seen.get(seen.size()-1).equals("144:[40, 100]"));
        parser.reset();before=seen.size();parser.feed(new byte[]{41,100},0,2,sink);check(before==seen.size());
        PadProtocol.Parser independent=new PadProtocol.Parser();independent.feed(new byte[]{36,127},0,2,sink);check(before==seen.size());
        System.out.println("PASS: 64-cell sample/DRUM bijections; running status; split messages; realtime; 128 EASY press/release values; bounded SysEx; reset/isolation");
    }
}
