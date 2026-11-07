package local.orange.z1androidtest;

import java.util.Arrays;

/** Immutable 64-cell, seven-bit RGB frame. This is the only device output payload type. */
public final class RgbFrame {
    public static final RgbFrame BLACK=new RgbFrame(new int[192]);
    private final int[] colors;
    public RgbFrame(int[] colors) {
        if(colors.length!=192)throw new IllegalArgumentException("64 RGB cells required");
        this.colors=colors.clone();
        for(int value:this.colors)if(value<0 || value>127)throw new IllegalArgumentException("seven-bit RGB required");
    }
    public int component(int cell,int channel){return colors[cell*3+channel];}
    public boolean isBlack(){return equals(BLACK);}
    /** Audited EASY full frame, indexed by DRUM position, not sample/PAD number. */
    public byte[] easyWireFrame(){
        byte[] bytes=new byte[259];bytes[0]=(byte)0xF0;bytes[1]=0x65;
        for(int cell=0;cell<64;cell++){
            int offset=2+cell*4;bytes[offset]=(byte)(PadProtocol.drum(cell)-36);
            for(int channel=0;channel<3;channel++)bytes[offset+1+channel]=(byte)component(cell,channel);
        }
        bytes[258]=(byte)0xF7;return bytes;
    }
    @Override public boolean equals(Object other){return other instanceof RgbFrame && Arrays.equals(colors,((RgbFrame)other).colors);}
    @Override public int hashCode(){return Arrays.hashCode(colors);}
}
