package android.media.midi;
import android.os.Bundle;
public final class MidiDeviceInfo {
    public static final int TYPE_USB=1;
    public static final String PROPERTY_USB_DEVICE="usb_device";
    private final int id,type;private final Bundle properties=new Bundle();
    public MidiDeviceInfo(int id,int type,Object usb){this.id=id;this.type=type;properties.put(PROPERTY_USB_DEVICE,usb);}
    public int getId(){return id;}public int getType(){return type;}public Bundle getProperties(){return properties;}
}
