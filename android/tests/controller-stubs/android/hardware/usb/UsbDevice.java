package android.hardware.usb;
public final class UsbDevice {
    private final int vendor,product;
    public UsbDevice(int vendor,int product){this.vendor=vendor;this.product=product;}
    public int getVendorId(){return vendor;}public int getProductId(){return product;}
}
