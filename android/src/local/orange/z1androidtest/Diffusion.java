package local.orange.z1androidtest;

import java.util.ArrayList;

/** Pure cell geometry/color model shared by screen and physical EASY LEDs. Millisecond clock. */
public final class Diffusion {
    public static final long DURATION_MS=1150;
    private static final class Wave {
        final int cell,velocity;final long time;
        Wave(int cell,int velocity,long time){this.cell=cell;this.velocity=velocity;this.time=time;}
    }
    private final ArrayList<Wave> waves=new ArrayList<>();
    public void hit(int cell,int velocity,long now){
        if(cell<0 || cell>=64)return;
        waves.add(new Wave(cell,Math.max(1,Math.min(127,velocity)),now));
        if(waves.size()>12)waves.remove(0);
    }
    public void clear(){waves.clear();}
    public int waveCount(){return waves.size();}
    public void expire(long now){waves.removeIf(w->now-w.time>=DURATION_MS);}
    public RgbFrame frame(long now){
        expire(now);
        int[] colors=new int[192];
        for(Wave w:waves){
            double age=Math.max(0,now-w.time)/1000.0,radius=age*9;
            double fade=Math.pow(Math.max(0,1-age/1.15),.7)*w.velocity/127.0;
            double hue=(PadProtocol.sample(w.cell)%16)/16.0+age*.12;
            double h=(hue-Math.floor(hue))*6,x=1-Math.abs(h%2-1);
            double[][] palette={{1,x,0},{x,1,0},{0,1,x},{0,x,1},{x,0,1},{1,0,x}};
            double[] color=palette[(int)h];
            for(int cell=0;cell<64;cell++){
                int dx=cell%8-w.cell%8,dy=cell/8-w.cell/8;
                // Normalize the diamond so opposite corners remain reachable within the existing duration.
                double distance=(Math.abs(dx)+Math.abs(dy))/Math.sqrt(2);
                double ring=Math.exp(-Math.pow((distance-radius)/.68,2));
                double center=distance<.1?Math.exp(-age*10):0;
                double intensity=Math.max(ring,center)*fade;
                if(intensity<=.012)continue;
                for(int c=0;c<3;c++)colors[cell*3+c]=Math.max(colors[cell*3+c],(int)(color[c]*Math.min(1,intensity)*100));
            }
        }
        return new RgbFrame(colors);
    }
}
