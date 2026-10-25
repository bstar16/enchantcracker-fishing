package io.github.enchantcracker1122.fishing;

import java.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;
import static org.junit.Assert.*;

public class FishingCollisionTest {
    private FishingCollision.Box cube(double x,double y,double z){return new FishingCollision.Box(x,y,z,x+1,y+1,z+1);}
    private FishingSimulation.Movement move(double x,double y,double z,double mx,double my,double mz,FishingCollision.Box... blocks){
        return FishingCollision.move(x,y,z,mx,my,mz,Arrays.asList(blocks));
    }
    @Test public void nearbyPoolWallDoesNotBlockClearMovement(){
        FishingSimulation.Movement r=move(.5,63.8,.5,.05,-.1,0,cube(1,63,0));
        assertEquals(.55,r.x,1e-12);assertEquals(63.7,r.y,1e-12);assertEquals(.05,r.mx,0);
    }
    @Test public void sweptBoxOverlapIsNotNecessarilyAnImpact(){
        // Y resolves first: by the time X moves, the hook has cleared the obstacle.
        FishingSimulation.Movement r=move(.5,63.8,.5,.7,.3,0,cube(1,63,0));
        assertEquals(1.2,r.x,1e-12);assertEquals(64.1,r.y,1e-12);assertEquals(.7,r.mx,0);
    }
    @Test public void singleBlockPoolFloorClipsBiteDip(){
        FishingSimulation.Movement r=move(.5,63.08,.5,.01,-.35,.02,cube(0,62,0));
        assertEquals(63,r.y,0);assertEquals(0,r.my,0);assertEquals(.51,r.x,1e-12);assertEquals(.52,r.z,1e-12);
    }
    @Test public void singleBlockPoolWallsKeepBobbingHookInside(){
        FishingSimulation.Movement r=move(.8,63.3,.8,.3,-.1,.3,cube(1,63,0),cube(0,63,1),cube(1,63,1));
        assertEquals(.875,r.x,0);assertEquals(.875,r.z,0);assertEquals(0,r.mx,0);assertEquals(0,r.mz,0);
        assertEquals(-.1,r.my,0);
    }
    @Test public void partialBlockShapeDoesNotBecomeFullCube(){
        FishingCollision.Box slab=new FishingCollision.Box(0,62,0,1,62.5,1);
        FishingSimulation.Movement r=move(.5,63,.5,0,-.25,0,slab);
        assertEquals(62.75,r.y,0);assertEquals(-.25,r.my,0);
        r=move(.5,62.6,.5,0,-.25,0,slab);assertEquals(62.5,r.y,0);assertEquals(0,r.my,0);
    }
    @Test public void negativeCoordinatesAndExactTouchingRemainValid(){
        FishingSimulation.Movement r=move(-.125,63,-.5,0,-.2,0,cube(0,62,-1),cube(-1,62,-1));
        assertEquals(63,r.y,0);assertEquals(-.125,r.x,0);
        r=move(-.125,63,-.5,0,.2,0,cube(0,63,-1));assertEquals(63.2,r.y,1e-12);
    }
    @Test public void matchesNativeAxisClippingAcross4096Movements(){
        Random random=new Random(1620);
        for(int n=0;n<4096;n++){
            double x=random.nextDouble()*4-2,y=63+random.nextDouble(),z=random.nextDouble()*4-2;
            double dx=random.nextDouble()*2-1,dy=random.nextDouble()*2-1,dz=random.nextDouble()*2-1;
            List<FishingCollision.Box> blocks=new ArrayList<FishingCollision.Box>();
            List<AxisAlignedBB> nativeBoxes=new ArrayList<AxisAlignedBB>();
            for(int i=0;i<8;i++){
                double bx=random.nextInt(5)-2,by=random.nextInt(3)+62,bz=random.nextInt(5)-2;
                FishingCollision.Box b=new FishingCollision.Box(bx,by,bz,bx+1,by+(i%2==0?.5:1),bz+1);
                blocks.add(b);nativeBoxes.add(new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
            }
            FishingSimulation.Movement actual=FishingCollision.move(x,y,z,dx,dy,dz,blocks);
            AxisAlignedBB b=new AxisAlignedBB(x-.125,y,z-.125,x+.125,y+.25,z+.125);
            double mx=dx,my=dy,mz=dz;
            for(AxisAlignedBB box:nativeBoxes)dy=box.calculateYOffset(b,dy);
            b=b.offset(0,dy,0);
            for(AxisAlignedBB box:nativeBoxes)dx=box.calculateXOffset(b,dx);
            b=b.offset(dx,0,0);
            for(AxisAlignedBB box:nativeBoxes)dz=box.calculateZOffset(b,dz);
            b=b.offset(0,0,dz);
            assertEquals((b.minX+b.maxX)/2,actual.x,0);assertEquals(b.minY,actual.y,0);assertEquals((b.minZ+b.maxZ)/2,actual.z,0);
            assertEquals(mx==dx?mx:0,actual.mx,0);assertEquals(my==dy?my:0,actual.my,0);assertEquals(mz==dz?mz:0,actual.mz,0);
        }
    }
}
