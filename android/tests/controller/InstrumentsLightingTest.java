package local.orange.z1androidtest;
/** Additional real-controller mode regression; existing LightingTest is deliberately untouched. */
public final class InstrumentsLightingTest {
    public static void main(String[] args){
        MidiLightingTest.Fixture f=new MidiLightingTest.Fixture();f.open();f.easy(0);f.at(50);
        int before=f.device().input.sent.size();f.controller.easyMode(false);f.at(100);
        MidiLightingTest.check(f.device().input.sent.size()==before+1 && MidiLightingTest.black(f.device().input.sent.get(before)),"leaving EASY delivers paced terminal black");
        f.easy(1);f.at(500);int count=f.device().input.sent.size();
        MidiLightingTest.check(count==before+1,"private input cannot diffuse while software mode rejects auto intent");
        f.device().output.emit(0x90,36,127);f.at(600);f.controller.easyMode(true);f.at(900);
        MidiLightingTest.check(f.device().input.sent.size()==count,"standard DRUM velocity never authorizes RGB and switching alone sends nothing");
        f.easy(2);f.at(1000);MidiLightingTest.check(f.device().input.sent.size()>count,"genuine EASY after accepted mode can resume RGB");
        MidiLightingTest.paced(f.device().input.attempts);
        System.out.println("InstrumentsLightingTest passed (real controller, host ports only)");
    }
}
