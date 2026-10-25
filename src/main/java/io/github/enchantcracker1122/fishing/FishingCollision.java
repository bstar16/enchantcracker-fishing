package io.github.enchantcracker1122.fishing;

import java.util.List;

/** Entity.move's zero-step-height Y/X/Z clipping, independent of client/world side effects. */
public final class FishingCollision {
    private FishingCollision() {}
    public static final class Box {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        public Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
            this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;
        }
        Box offset(double x,double y,double z){return new Box(minX+x,minY+y,minZ+z,maxX+x,maxY+y,maxZ+z);}
        double clipY(Box b,double d) {
            if(b.maxX>minX && b.minX<maxX && b.maxZ>minZ && b.minZ<maxZ) {
                if(d>0 && b.maxY<=minY)d=Math.min(d,minY-b.maxY);
                else if(d<0 && b.minY>=maxY)d=Math.max(d,maxY-b.minY);
            }
            return d;
        }
        double clipX(Box b,double d) {
            if(b.maxY>minY && b.minY<maxY && b.maxZ>minZ && b.minZ<maxZ) {
                if(d>0 && b.maxX<=minX)d=Math.min(d,minX-b.maxX);
                else if(d<0 && b.minX>=maxX)d=Math.max(d,maxX-b.minX);
            }
            return d;
        }
        double clipZ(Box b,double d) {
            if(b.maxX>minX && b.minX<maxX && b.maxY>minY && b.minY<maxY) {
                if(d>0 && b.maxZ<=minZ)d=Math.min(d,minZ-b.maxZ);
                else if(d<0 && b.minZ>=maxZ)d=Math.max(d,maxZ-b.minZ);
            }
            return d;
        }
    }
    public static FishingSimulation.Movement move(double x,double y,double z,double mx,double my,double mz,List<Box> blocks) {
        Box b=new Box(x-.125D,y,z-.125D,x+.125D,y+.25D,z+.125D);
        double dx=mx,dy=my,dz=mz;
        for(Box block:blocks)dy=block.clipY(b,dy);
        if(dy!=0)b=b.offset(0,dy,0);
        for(Box block:blocks)dx=block.clipX(b,dx);
        if(dx!=0)b=b.offset(dx,0,0);
        for(Box block:blocks)dz=block.clipZ(b,dz);
        if(dz!=0)b=b.offset(0,0,dz);
        // Ordinary Block.onLanded zeros vertical velocity; special surfaces are checked by the adapter.
        return new FishingSimulation.Movement((b.minX+b.maxX)/2,b.minY,(b.minZ+b.maxZ)/2,
            dx==mx?mx:0,dy==my?my:0,dz==mz?mz:0);
    }
}
