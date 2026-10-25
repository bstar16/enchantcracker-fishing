package io.github.enchantcracker1122.fishing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Main-thread adapter. Uses actual block shapes, including shallow pool floors/walls. */
final class FishingEnvironment implements FishingSimulation.Environment {
    private final World world;
    private final int ownerId,hookId;
    private final Probe probe;
    private final Map<BlockPos,IBlockState> touched=new LinkedHashMap<BlockPos,IBlockState>();
    private final Map<BlockPos,Boolean> skies=new LinkedHashMap<BlockPos,Boolean>();
    private final Map<BlockPos,Boolean> rains=new LinkedHashMap<BlockPos,Boolean>();
    private final java.util.Set<BlockPos> neighboringChunks=new java.util.HashSet<BlockPos>();
    private final double startX,startZ;
    FishingEnvironment(World world,int ownerId,int hookId,double startX,double startZ) {
        this.world=world;this.ownerId=ownerId;this.hookId=hookId;this.startX=startX;this.startZ=startZ;
        probe=new Probe(world);
    }
    static AxisAlignedBB box(double x,double y,double z) {
        return new AxisAlignedBB(x-.125D,y,z-.125D,x+.125D,y+.25D,z+.125D);
    }
    private IBlockState state(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        if(Math.abs(x-startX)>40 || Math.abs(z-startZ)>40 || y<1 || y>254)
            throw new IllegalStateException("Hook left the supported fishing area");
        if(!world.isBlockLoaded(pos)) throw new IllegalStateException("Fishing area has unloaded chunks");
        IBlockState result=world.getBlockState(pos); touched.put(pos,result);return result;
    }
    private void capture(AxisAlignedBB box) {
        for(int x=MathHelper.floor(box.minX);x<=MathHelper.floor(box.maxX);x++)
            for(int y=MathHelper.floor(box.minY);y<=MathHelper.floor(box.maxY);y++)
                for(int z=MathHelper.floor(box.minZ);z<=MathHelper.floor(box.maxZ);z++) {
                    state(x,y,z); // Remember dependencies; proximity to a block is not a collision.
                }
    }
    private void checkSpecialContact(AxisAlignedBB box) {
        AxisAlignedBB inside=box.shrink(.001D);
        for(int x=MathHelper.floor(inside.minX);x<=MathHelper.floor(inside.maxX);x++)
            for(int y=MathHelper.floor(inside.minY);y<=MathHelper.floor(inside.maxY);y++)
                for(int z=MathHelper.floor(inside.minZ);z<=MathHelper.floor(inside.maxZ);z++) {
                    Block block=state(x,y,z).getBlock();
                    if(block==Blocks.WEB || block==Blocks.SOUL_SAND || block==Blocks.PORTAL ||
                        block==Blocks.END_PORTAL || block==Blocks.END_GATEWAY || block==Blocks.FIRE ||
                        block==Blocks.LAVA || block==Blocks.FLOWING_LAVA)
                        throw new IllegalStateException("Hook contacts unsupported "+block.getRegistryName()+" at "+x+", "+y+", "+z);
                }
    }
    @Override public FishingSimulation.Water waterMovement(double x,double y,double z) {
        int chunkX=MathHelper.floor(x)>>4,chunkZ=MathHelper.floor(z)>>4;
        for(int cx=chunkX-1;cx<=chunkX+1;cx++)for(int cz=chunkZ-1;cz<=chunkZ+1;cz++) {
            BlockPos center=new BlockPos((cx<<4)+8,64,(cz<<4)+8);
            if(!world.isBlockLoaded(center))throw new IllegalStateException("Keep the hook and its neighboring chunks loaded");
            neighboringChunks.add(center);
        }
        AxisAlignedBB bb=box(x,y,z).grow(0,-0.4000000059604645D,0).shrink(.001D);
        // Capture the region read by vanilla water acceleration, including neighboring flow blocks.
        for(int bx=MathHelper.floor(bb.minX)-1;bx<=MathHelper.ceil(bb.maxX)+1;bx++)
            for(int by=MathHelper.floor(bb.minY)-1;by<=MathHelper.ceil(bb.maxY)+1;by++)
                for(int bz=MathHelper.floor(bb.minZ)-1;bz<=MathHelper.ceil(bb.maxZ)+1;bz++) state(bx,by,bz);
        probe.motionX=probe.motionY=probe.motionZ=0;
        probe.setPosition(x,y,z);
        boolean inside=world.handleMaterialAcceleration(bb,Material.WATER,probe);
        return new FishingSimulation.Water(inside,probe.motionX,probe.motionY,probe.motionZ);
    }
    @Override public float waterHeight(int x,int y,int z) {
        IBlockState block=state(x,y,z);
        if(block.getMaterial()!=Material.WATER) return 0;
        state(x,y+1,z);
        return BlockLiquid.getBlockLiquidHeight(block,world,new BlockPos(x,y,z));
    }
    @Override public boolean waterAt(int x,int y,int z) { return state(x,y,z).getMaterial()==Material.WATER; }
    @Override public boolean rainingAt(int x,int y,int z) {
        state(x,y,z);BlockPos p=new BlockPos(x,y,z);boolean v=world.isRainingAt(p);rains.put(p,v);return v;
    }
    @Override public boolean skyAt(int x,int y,int z) {
        state(x,y,z);BlockPos p=new BlockPos(x,y,z);boolean v=world.canSeeSky(p);skies.put(p,v);return v;
    }
    @Override public FishingSimulation.Movement move(double x,double y,double z,double mx,double my,double mz,boolean flying) {
        AxisAlignedBB from=box(x,y,z),swept=from.expand(mx,my,mz);
        // Collision shapes (fences, panes, etc.) can depend on adjacent blocks.
        capture(swept.grow(1));
        checkSpecialContact(from);
        Vec3d a=new Vec3d(x,y,z),b=a.add(mx,my,mz);
        if(flying) {
            RayTraceResult hit=world.rayTraceBlocks(a,b,false,true,false);
            if(hit!=null && hit.typeOfHit==RayTraceResult.Type.BLOCK) {
                BlockPos p=hit.getBlockPos();
                throw new IllegalStateException("Cast hits "+state(p.getX(),p.getY(),p.getZ()).getBlock().getRegistryName()+
                    " at "+p.getX()+", "+p.getY()+", "+p.getZ()+" before reaching water; change your aim");
            }
        }
        List<AxisAlignedBB> obstacles=world.getCollisionBoxes(null,swept);
        // Boats/shulkers can clip movement even after the hook starts bobbing.
        for(Entity entity:world.getEntitiesWithinAABBExcludingEntity(probe,swept.grow(1))) {
            if(entity.getEntityId()==ownerId || entity.getEntityId()==hookId)continue;
            AxisAlignedBB collision=entity.getCollisionBoundingBox();
            if(collision!=null && collision.intersects(swept))throw new IllegalStateException("Hook would collide with an entity");
        }
        if(flying) {
            for(Entity entity:world.getEntitiesWithinAABBExcludingEntity(probe,swept.grow(1))) {
                if(entity.getEntityId()==ownerId || entity.getEntityId()==hookId) continue;
                if(entity.canBeCollidedWith() || entity instanceof net.minecraft.entity.item.EntityItem)
                    if(entity.getEntityBoundingBox().grow(.30000001192092896D).calculateIntercept(a,b)!=null)
                        throw new IllegalStateException("Hook could hit an entity");
            }
        }
        List<FishingCollision.Box> shapes=new ArrayList<FishingCollision.Box>(obstacles.size());
        for(AxisAlignedBB obstacle:obstacles)shapes.add(new FishingCollision.Box(obstacle.minX,obstacle.minY,obstacle.minZ,
            obstacle.maxX,obstacle.maxY,obstacle.maxZ));
        FishingSimulation.Movement moved=FishingCollision.move(x,y,z,mx,my,mz,shapes);
        boolean clipped=moved.mx!=mx || moved.my!=my || moved.mz!=mz;
        if(flying && clipped)throw new IllegalStateException("Bobber physically collides during the cast at "+
            MathHelper.floor(moved.x)+", "+MathHelper.floor(moved.y)+", "+MathHelper.floor(moved.z)+"; change your aim");
        if(moved.my!=my) {
            Block floor=state(MathHelper.floor(moved.x),MathHelper.floor(moved.y-.20000000298023224D),MathHelper.floor(moved.z)).getBlock();
            if(floor==Blocks.SLIME_BLOCK || floor==Blocks.BED)
                throw new IllegalStateException("Hook lands on a bouncing block; use an ordinary pool floor");
        }
        checkSpecialContact(box(moved.x,moved.y,moved.z));
        return moved;
    }
    void validate() {
        for(BlockPos p:neighboringChunks)
            if(!world.isBlockLoaded(p))throw new IllegalStateException("Neighboring fishing chunk unloaded; recast");
        for(Map.Entry<BlockPos,IBlockState> p:touched.entrySet())
            if(!world.isBlockLoaded(p.getKey()) || world.getBlockState(p.getKey())!=p.getValue())
                throw new IllegalStateException("Fishing blocks changed or chunks unloaded; recast");
        for(Map.Entry<BlockPos,Boolean> p:skies.entrySet())
            if(world.canSeeSky(p.getKey())!=p.getValue()) throw new IllegalStateException("Sky cover changed; recast");
        for(Map.Entry<BlockPos,Boolean> p:rains.entrySet())
            if(world.isRainingAt(p.getKey())!=p.getValue()) throw new IllegalStateException("Weather changed; recast");
    }
    private static final class Probe extends Entity {
        Probe(World world) { super(world);setSize(.25F,.25F); }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
    }
}
