package local.orange.z1androidtest;

/** Host-only geometry and unchanged diffusion contracts; no Android device access. */
public final class DiamondTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static int cell(int x,int y){return y*8+x;}
    private static int peak(RgbFrame frame,int cell){
        return Math.max(frame.component(cell,0),Math.max(frame.component(cell,1),frame.component(cell,2)));
    }
    public static void main(String[] args){
        contourAndProgression();equalDistanceAndSymmetry();centerAndCorners();colorsBoundAndDedup();
        System.out.println("DIAMOND PASS: "+checks+" checks; normalized Manhattan contour, progression, symmetry, corners, bounds, dedup");
    }
    private static void contourAndProgression(){
        Diffusion d=new Diffusion();d.hit(cell(3,3),127,1000);
        RgbFrame first=d.frame(1157);
        int axial=cell(5,3),diagonal=cell(4,4);
        for(int c=0;c<3;c++)check(first.component(axial,c)==first.component(diagonal,c),"equal Manhattan distance must give equal same-channel intensity");
        check(peak(first,axial)>60,"distance-two diamond contour is lit at 157ms");
        // At the distance-three front the axial point leads the (2,2) diagonal;
        // when the distance-four front arrives, their order reverses.
        int axialThree=cell(6,3),diagonalFour=cell(5,5);
        RgbFrame earlier=d.frame(1235),later=d.frame(1314);
        check(peak(earlier,axialThree)>2*peak(earlier,diagonalFour),"axial distance-three front leads diagonal distance-four");
        check(peak(later,diagonalFour)>2*peak(later,axialThree),"diagonal distance-four front follows axial distance-three");
    }
    private static void equalDistanceAndSymmetry(){
        // Exercise every origin, all Manhattan shells and all channels. Clipping at
        // an edge never changes the intensity of the cells that remain on a shell.
        for(int origin=0;origin<64;origin++){
            Diffusion d=new Diffusion();d.hit(origin,127,1000);
            for(int age:new int[]{0,157,235,314,550,900,1100}){
                RgbFrame frame=d.frame(1000+age);int[][] shell=new int[15][3];boolean[] seen=new boolean[15];
                for(int target=0;target<64;target++){
                    int distance=Math.abs(target%8-origin%8)+Math.abs(target/8-origin/8);
                    for(int c=0;c<3;c++){
                        int value=frame.component(target,c);
                        if(seen[distance])check(value==shell[distance][c],"same-shell symmetry origin="+origin+" age="+age+" distance="+distance+" channel="+c);
                        else shell[distance][c]=value;
                    }
                    seen[distance]=true;
                }
            }
        }
        Diffusion d=new Diffusion();d.hit(cell(3,3),127,0);
        for(int age:new int[]{157,235,314,550}){
            RgbFrame frame=d.frame(age);
            for(int dx=0;dx<=3;dx++)for(int dy=0;dy<=3;dy++)for(int c=0;c<3;c++){
                int value=frame.component(cell(3+dx,3+dy),c);
                check(value==frame.component(cell(3-dx,3+dy),c),"horizontal reflection");
                check(value==frame.component(cell(3+dx,3-dy),c),"vertical reflection");
                check(value==frame.component(cell(3+dy,3+dx),c),"diagonal reflection");
            }
        }
    }
    private static void centerAndCorners(){
        for(int origin:new int[]{cell(3,3),cell(0,0),cell(7,0),cell(0,7),cell(7,7)}){
            Diffusion d=new Diffusion();d.hit(origin,127,1000);
            check(peak(d.frame(1000),origin)>90,"center flash at origin "+origin);
            check(d.frame(2150).isBlack() && d.waveCount()==0,"1150ms expiry and black at origin "+origin);
        }
        for(int origin:new int[]{cell(0,0),cell(7,0),cell(0,7),cell(7,7)}){
            int opposite=cell(7-origin%8,7-origin/8);
            Diffusion d=new Diffusion();d.hit(origin,127,1000);
            check(peak(d.frame(2100),opposite)>0,"opposite corner lit at 1100ms from "+origin);
            check(d.frame(2150).equals(RgbFrame.BLACK) && d.waveCount()==0,"corner wave terminal black at 1150ms");
        }
    }
    private static void colorsBoundAndDedup(){
        Diffusion d=new Diffusion();d.hit(-1,127,0);d.hit(64,127,0);
        check(d.waveCount()==0 && d.frame(0).isBlack(),"invalid cells ignored");
        for(int velocity:new int[]{-10,0,1,127,128,10000}){
            Diffusion actual=new Diffusion(),clamped=new Diffusion();
            actual.hit(27,velocity,0);clamped.hit(27,Math.max(1,Math.min(127,velocity)),0);
            for(int age:new int[]{0,157,550,1100}){
                RgbFrame frame=actual.frame(age);
                check(frame.equals(clamped.frame(age)),"velocity clamped");
                for(int target=0;target<64;target++)for(int c=0;c<3;c++){
                    int value=frame.component(target,c);
                    check(value>=0 && value<=100,"valid restrained color for clamped velocity");
                }
            }
        }
        // 13 hits must behave exactly like the 12 most recent hits.
        Diffusion lastTwelve=new Diffusion();
        for(int i=0;i<13;i++){
            d.hit(i,i==0?-10:i==1?0:i==2?128:127,1000);
            if(i>0)lastTwelve.hit(i,i==1?0:i==2?128:127,1000);
        }
        check(d.waveCount()==12 && lastTwelve.waveCount()==12,"12-wave limit");
        for(int age:new int[]{0,157,314,550,900,1100,1150}){
            RgbFrame frame=d.frame(1000+age);
            check(frame.equals(lastTwelve.frame(1000+age)),"oldest wave removed at 12-wave bound");
            for(int target=0;target<64;target++)for(int c=0;c<3;c++){
                int value=frame.component(target,c);
                check(value>=0 && value<=100,"valid restrained seven-bit color");
            }
        }
        d.hit(27,127,3000);RgbFrame rendered=d.frame(3157);
        check(rendered.equals(d.frame(3157)),"deterministic frame equality");
        LightingState state=new LightingState();state.connected();state.authorize();state.render(rendered);
        RgbFrame attempted=state.beginAttempt(3157);check(attempted!=null && attempted.equals(rendered),"first frame pending");
        state.completeAttempt(attempted,true);state.render(d.frame(3157));
        check(!state.pending(),"successful identical frame deduplicated");
        d.clear();check(d.waveCount()==0 && d.frame(3157).isBlack(),"clear produces black");
    }
}
