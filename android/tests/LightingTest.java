package local.orange.z1androidtest;

import java.util.Random;

public final class LightingTest {
    private static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static RgbFrame red(){int[] rgb=new int[192];rgb[27*3]=100;return new RgbFrame(rgb);}
    public static void main(String[] args){
        wire();diffusion();pacing();failures();randomized();
        System.out.println("LIGHTING PASS: "+checks+" checks; wire, diffusion, latest-only, terminal black, dedup, failures, reconnect pacing");
    }
    static void wire(){
        for(RgbFrame frame:new RgbFrame[]{RgbFrame.BLACK,red()}){
            byte[] bytes=frame.easyWireFrame();boolean[] seen=new boolean[64];
            check(bytes.length==259 && (bytes[0]&255)==240 && bytes[1]==101 && (bytes[258]&255)==247,"wire envelope");
            for(int cell=0;cell<64;cell++){
                int offset=2+cell*4,index=bytes[offset]&255;
                check(index==PadProtocol.drum(cell)-36 && !seen[index],"physical DRUM index");seen[index]=true;
                for(int c=0;c<3;c++)check((bytes[offset+1+c]&255)==frame.component(cell,c),"RGB seven-bit / black");
            }
        }
        int[] colors=new int[192];colors[0]=128;
        try{new RgbFrame(colors);throw new AssertionError("invalid RGB accepted");}catch(IllegalArgumentException expected){}
        colors[0]=100;RgbFrame immutable=new RgbFrame(colors);colors[0]=0;
        check(immutable.component(0,0)==100,"immutable colors");
    }
    static void diffusion(){
        Diffusion d=new Diffusion();d.hit(27,100,1000);RgbFrame start=d.frame(1000);
        check(!start.isBlack() && Math.max(start.component(27,0),Math.max(start.component(27,1),start.component(27,2)))>20,"center flash");
        RgbFrame expanded=d.frame(1250);boolean ring=false;
        for(int i=0;i<64;i++)for(int c=0;c<3;c++){check(expanded.component(i,c)<=100,"restrained intensity");if(i!=27 && expanded.component(i,c)>10)ring=true;}
        check(ring,"expanding ring");check(d.frame(2150).isBlack() && d.waveCount()==0,"1150ms expiry");
        for(int i=0;i<100;i++)d.hit(i%64,127,3000);
        check(d.waveCount()==12,"bounded waves");d.clear();check(d.frame(3000).isBlack(),"clear");
    }
    static void pacing(){
        LightingState s=new LightingState();s.connected();s.render(red());check(!s.pending(),"not authorized on connect");
        s.authorize();s.render(red());RgbFrame frame=s.beginAttempt(1000);check(frame.equals(red()),"first immediate");s.completeAttempt(frame,true);
        s.render(red());check(!s.pending(),"dedup successful only");s.clear();check(s.pending(),"black pending");
        check(s.beginAttempt(1099)==null && s.pending(),"black survives throttle");
        s.resumeEffects();s.render(red());check(s.rendered().equals(red()) && s.pending(),"render separated from terminal black");
        frame=s.beginAttempt(1100);check(frame.isBlack(),"terminal black not replaced by new wave");s.completeAttempt(frame,true);
        s.resumeEffects();s.render(red());check(s.pending(),"effects after terminal delivery");
        s.revoke();check(!s.pending() && s.beginAttempt(1200)==null,"Notes revoke without black");
        s.connected();s.authorize();s.render(red());check(s.beginAttempt(1199)==null,"rapid reconnect retains pacing");
        frame=s.beginAttempt(1200);check(frame!=null,"next exact deadline");s.completeAttempt(frame,true);
        s.clear();frame=s.beginAttempt(1300);s.completeAttempt(frame,true);s.clear();check(!s.pending(),"duplicate clear dedup");
        s.render(red());check(!s.pending(),"terminal clear latch");s.resumeEffects();s.render(red());check(s.pending(),"new hit resumes after clear");
    }
    static void failures(){
        LightingState s=new LightingState();s.connected();s.authorize();s.clear();
        for(int i=0;i<3;i++){
            RgbFrame frame=s.beginAttempt(1000+i*100);check(frame!=null && frame.isBlack(),"retry black");s.completeAttempt(frame,false);
            if(i<2)check(s.beginAttempt(1099+i*100)==null && s.pending(),"failed attempts also paced");
        }
        check(s.disabled() && !s.pending() && !s.authorized(),"three failures disable");s.authorize();s.render(red());check(!s.pending(),"input cannot revive failed port");
        s.connected();s.authorize();s.render(red());check(s.beginAttempt(1299)==null,"failure / reconnect pacing");check(s.beginAttempt(1300)!=null,"retry after reconnect");
    }
    static void randomized(){
        Random random=new Random(2102);LightingState s=new LightingState();long now=0,last=-100;
        for(int i=0;i<20000;i++){
            now+=random.nextInt(9);
            switch(random.nextInt(8)){
                case 0:s.connected();break;case 1:s.authorize();break;case 2:s.revoke();break;
                case 3:s.clear();break;case 4:s.resumeEffects();break;default:s.render(random.nextBoolean()?red():RgbFrame.BLACK);
            }
            RgbFrame frame=s.beginAttempt(now);
            if(frame!=null){check(now-last>=100,"random all-attempt spacing");last=now;s.completeAttempt(frame,random.nextBoolean());}
            if(!s.authorized())check(!s.pending(),"random no unauthorized pending");
        }
    }
}
