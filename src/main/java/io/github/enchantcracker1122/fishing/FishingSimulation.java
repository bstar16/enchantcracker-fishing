package io.github.enchantcracker1122.fishing;

/** 1.12.2 hook logic; environment and collision checks are supplied by the caller.
 * A result at tick N is AFTER N complete hook updates and BEFORE tick N+1.
 * The environment resolves ordinary pool-floor/wall collisions while bobbing.
 */
public final class FishingSimulation {
    public interface Environment {
        Water waterMovement(double x, double y, double z);
        float waterHeight(int x, int y, int z);
        boolean waterAt(int x, int y, int z);
        boolean rainingAt(int x, int y, int z);
        boolean skyAt(int x, int y, int z);
        Movement move(double x, double y, double z, double mx, double my, double mz, boolean flying);
    }
    public static final class Movement {
        public final double x,y,z,mx,my,mz;
        public Movement(double x,double y,double z,double mx,double my,double mz) {
            this.x=x;this.y=y;this.z=z;this.mx=mx;this.my=my;this.mz=mz;
        }
    }
    public static final class Water {
        public final boolean inside;
        public final double x, y, z;
        public Water(boolean inside, double x, double y, double z) {
            this.inside = inside; this.x = x; this.y = y; this.z = z;
        }
    }
    private static final float[] SIN = new float[65536];
    static { for (int i=0; i<SIN.length; i++) SIN[i] = (float)Math.sin(i * Math.PI * 2 / 65536); }
    public static float sin(float a) { return SIN[(int)(a * 10430.378F) & 65535]; }
    public static float cos(float a) { return SIN[(int)(a * 10430.378F + 16384.0F) & 65535]; }
    public static int floor(double d) { int i=(int)d; return d<i ? i-1 : i; }
    public final FishingRandom random;
    public double x,y,z,mx,my,mz;
    public int tick,catchable,waiting,approaching;
    public float angle;
    public boolean bobbing,inWater;
    private final int lure;

    /** RNG is positioned immediately after UUID generation, with an empty cache. */
    public FishingSimulation(FishingRandom random, double x, double y, double z,
                             float yaw, float pitch, int lure) {
        this.random=random; this.x=x; this.y=y; this.z=z; this.lure=lure;
        float c=cos(-yaw*0.017453292F-3.1415927F);
        float s=sin(-yaw*0.017453292F-3.1415927F);
        float cp=-cos(-pitch*0.017453292F), sp=sin(-pitch*0.017453292F);
        mx=-s; my=Math.max(-5.0F,Math.min(5.0F,-(sp/cp))); mz=-c;
        float length=(float)Math.sqrt(mx*mx+my*my+mz*mz);
        mx *= 0.6D/(double)length+0.5D+random.nextGaussian()*0.0045D;
        my *= 0.6D/(double)length+0.5D+random.nextGaussian()*0.0045D;
        mz *= 0.6D/(double)length+0.5D+random.nextGaussian()*0.0045D;
    }
    public void step(Environment e) {
        Water water=e.waterMovement(x,y,z);
        mx+=water.x; my+=water.y; mz+=water.z;
        if (water.inside && !inWater && tick>0) {
            // Base Entity splash: two sound + 6*3 bubble + 6*2 splash FLOATS.
            for (int i=0;i<32;i++) random.nextFloat();
        }
        inWater=water.inside;
        ++tick;
        int bx=floor(x),by=floor(y),bz=floor(z);
        float height=e.waterHeight(bx,by,bz);
        if (!bobbing && height>0) {
            mx*=0.3D; my*=0.2D; mz*=0.3D; bobbing=true;
            return;
        }
        if (bobbing) {
            mx*=0.9D; mz*=0.9D;
            double delta=y+my-(double)by-(double)height;
            if (Math.abs(delta)<0.01D) delta+=Math.signum(delta)*0.1D;
            my-=delta*(double)random.nextFloat()*0.2D;
            if (height>0) fishing(e,bx,by,bz);
        }
        if (!e.waterAt(bx,by,bz)) my-=0.03D;
        Movement moved=e.move(x,y,z,mx,my,mz,!bobbing);
        x=moved.x; y=moved.y; z=moved.z;
        mx=moved.mx; my=moved.my; mz=moved.mz;
        mx*=0.92D; my*=0.92D; mz*=0.92D;
    }
    private float between(float min,float max) { return min+random.nextFloat()*(max-min); }
    private void fishing(Environment e,int bx,int by,int bz) {
        int speed=1;
        if(random.nextFloat()<0.25F && e.rainingAt(bx,by+1,bz)) ++speed;
        if(random.nextFloat()<0.5F && !e.skyAt(bx,by+1,bz)) --speed;
        if(catchable>0) {
            if(--catchable<=0) { waiting=0; approaching=0; }
            else my-=0.2D*(double)random.nextFloat()*(double)random.nextFloat();
        } else if(approaching>0) {
            approaching-=speed;
            if(approaching>0) {
                angle=(float)((double)angle+random.nextGaussian()*4.0D);
                float a=angle*0.017453292F;
                double px=x+(double)(sin(a)*(float)approaching*0.1F);
                double py=(double)((float)floor(y)+1.0F);
                double pz=z+(double)(cos(a)*(float)approaching*0.1F);
                if(e.waterAt(floor(px),floor(py-1.0D),floor(pz))) random.nextFloat();
            } else {
                my=(double)(-0.4F*between(0.6F,1.0F));
                random.nextFloat(); random.nextFloat();
                catchable=20+random.nextInt(21);
            }
        } else if(waiting>0) {
            waiting-=speed;
            float chance=0.15F;
            if(waiting<20) chance=(float)((double)chance+(double)(20-waiting)*0.05D);
            else if(waiting<40) chance=(float)((double)chance+(double)(40-waiting)*0.02D);
            else if(waiting<60) chance=(float)((double)chance+(double)(60-waiting)*0.01D);
            if(random.nextFloat()<chance) {
                float a=between(0.0F,360.0F)*0.017453292F;
                float distance=between(25.0F,60.0F);
                double px=x+(double)(sin(a)*distance*0.1F);
                double py=(double)((float)floor(y)+1.0F);
                double pz=z+(double)(cos(a)*distance*0.1F);
                // This lookup really truncates toward zero in the old server.
                if(e.waterAt((int)px,(int)py-1,(int)pz)) random.nextInt(2);
            }
            if(waiting<=0) { angle=between(0.0F,360.0F); approaching=20+random.nextInt(61); }
        } else {
            waiting=100+random.nextInt(501)-lure*100; // No clamp: nonpositive delays reroll.
        }
    }
}
