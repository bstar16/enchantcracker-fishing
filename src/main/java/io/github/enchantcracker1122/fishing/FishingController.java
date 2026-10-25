package io.github.enchantcracker1122.fishing;

import io.netty.channel.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/** World reads, inventory checks and rod uses run only on the Minecraft thread. */
public final class FishingController {
    private static final String HANDLER="enchantcracker_fishing";
    private static final Logger LOG=LogManager.getLogger("FishPredict");
    private static final long SECOND=FishingRun.SECOND;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final KeyBinding cancelKey=new KeyBinding("Cancel fishing predictor",Keyboard.KEY_NONE,"Enchant Cracker");
    private final FishingRun run=new FishingRun();
    private volatile boolean enabled,observerReady;
    private volatile long generation;
    private volatile CastInput prepared,mainSnapshot,offSnapshot;
    private volatile String observerError="";
    private NetworkManager observedManager;
    private boolean preview,automaticUse;
    private FishingTarget target;
    private Session session;
    private Anchor anchor;
    private FishingEnvironment lastEnvironment;
    private Retrieval retrieval;
    private int retiringHook=-1,ticks,offsetMs,pingOverride=-1;
    private String previewStatus="Set a target to start.",lastPublished="",activity="";
    private long lastTimeNanos,lastWorldTime;
    private double tickMillis=50;
    private int clockSamples;
    private FishingLoot loot;

    public void registerKey(){ClientRegistry.registerKeyBinding(cancelKey);}
    private void say(String text){
        LOG.info(text);
        if(mc.player!=null)mc.player.sendMessage(new TextComponentString("\u00a7b[FishPredict]\u00a7r "+text));
    }
    private void publish(){
        String line=run.state()+" | cast "+run.attempts()+" | "+run.detail();
        if(line.equals(lastPublished))return;
        lastPublished=line;LOG.info(line);
        if(run.state()==FishingRun.State.PAUSED || run.state()==FishingRun.State.COMPLETE)say(run.detail());
    }
    private void clearCast(){session=null;prepared=null;mainSnapshot=null;offSnapshot=null;retrieval=null;retiringHook=-1;}
    private void stop(String reason){
        ++generation;clearCast();anchor=null;lastEnvironment=null;preview=false;activity="";
        run.stop(reason,System.nanoTime());say(reason);publish();
    }
    private void pause(String reason){
        if(preview){session=null;prepared=null;previewStatus=reason;say(reason);return;}
        ++generation;clearCast();activity="";run.pause(reason+" Use /fishpredict resume, then cast again.",System.nanoTime());publish();
    }
    private void startTarget(){
        if(mc.player==null || mc.world==null)throw new IllegalArgumentException("Join a world before starting fishing.");
        ++generation;clearCast();anchor=null;lastEnvironment=null;preview=false;enabled=true;activity="";
        run.start(System.nanoTime());ensureObserver();
        say("Target: "+target.label+". Aim at a clear pool and cast ONCE; I will reel and recast until you collect it.");
        if(mc.player.fishEntity!=null)say("A hook is already out. Reel it in manually, then cast into the pool to begin.");
        publish();
    }
    public String status(){
        return (preview?"PREVIEW | "+previewStatus:run.state()+" | "+run.detail())+
            " | Target: "+(target==null?"none":target.label)+" | Casts: "+run.attempts()+" | Speed: "+run.speed().name().toLowerCase(Locale.ROOT)+
            " | Bobber packets: "+(observerReady?"connected":observerError.isEmpty()?"connecting":observerError);
    }
    public void command(String[] args){
        String op=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        if("target".equals(op)){FishingTarget parsed=new FishingTarget(args);target=parsed;startTarget();}
        else if("resume".equals(op) || "arm".equals(op)){
            if(target==null)throw new IllegalArgumentException("Set a target first: /fishpredict target enchanted_book mending 1");
            startTarget();
        }else if("on".equals(op)){
            stop("Preview mode. Cast to inspect predictions; use target to start automatic fishing.");
            enabled=true;preview=true;previewStatus="Cast into a clear pool to preview catches.";ensureObserver();
        }else if("off".equals(op)){stop("Stopped. HUD hidden.");enabled=false;}
        else if("cancel".equals(op) || "stop".equals(op)){stop("Stopped by you. No further rod-use requests will be sent.");}
        else if("clear".equals(op)){stop("Target cleared.");target=null;}
        else if("speed".equals(op)){
            if(args.length>2)throw new IllegalArgumentException("Use /fishpredict speed <fast|normal>");
            if(args.length==2){
                if("fast".equalsIgnoreCase(args[1]))run.speed(FishingRun.Speed.FAST);
                else if("normal".equalsIgnoreCase(args[1]))run.speed(FishingRun.Speed.NORMAL);
                else throw new IllegalArgumentException("Use /fishpredict speed <fast|normal>");
            }
            say("Recast speed: "+run.speed().name().toLowerCase(Locale.ROOT)+". Server confirmation and target reel timing remain required.");
        }
        else if("offset".equals(op)){
            if(args.length!=2)throw new IllegalArgumentException("Use /fishpredict offset <-500..500 milliseconds>");
            offsetMs=bounded(args[1],-500,500);say("Timing correction: "+offsetMs+" ms (positive = later).");
        }else if("ping".equals(op)){
            if(args.length!=2)throw new IllegalArgumentException("Use /fishpredict ping <auto|0..1000 RTT milliseconds>");
            pingOverride="auto".equals(args[1])?-1:bounded(args[1],0,1000);say("Round-trip ping: "+(pingOverride<0?"auto":pingOverride+" ms"));
        }else if("list".equals(op)){
            if(session==null){say("No active prediction. "+status());return;}
            int count=0;
            for(Sample sample:session.samples)if(sample.loot!=null && matches(sample)){
                say("Tick "+sample.tick+": "+sample.loot.description());if(++count==12)break;
            }
            if(count==0)say("No match in this cast; "+(preview?"preview only.":"automatic retry is queued."));
        }else if("status".equals(op))say(status()+" | RTT "+ping()+" ms | tick "+String.format(Locale.ROOT,"%.1f",tickMillis)+" ms | offset "+offsetMs+" ms");
        else say("/fishpredict target <item> [enchant level ...] | cancel | resume | status | speed <fast|normal> | on (preview) | off | list | offset | ping");
    }
    private static int bounded(String text,int min,int max){
        try{int n=Integer.parseInt(text);if(n>=min && n<=max)return n;}catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("Value must be "+min+".."+max);
    }
    private int ping(){
        if(pingOverride>=0)return pingOverride;
        if(mc.player==null || mc.getConnection()==null)return 0;
        NetworkPlayerInfo info=mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
        return info==null?0:Math.max(0,info.getResponseTime());
    }
    private boolean matches(Sample sample){return target==null || target.matches(sample.loot.stack);}
    private boolean stableClock(long now){return clockSamples>0 && tickMillis>=45 && tickMillis<=55 && now-lastTimeNanos<3*SECOND;}
    private void ensureObserver(){
        if(mc.getConnection()!=null)attach(mc.getConnection().getNetworkManager());
    }
    private void attach(NetworkManager manager){
        if(manager==observedManager)return;
        observedManager=manager;observerReady=false;observerError="";
        manager.channel().eventLoop().execute(()->{
            try{
                if(manager.channel().pipeline().get(HANDLER)==null)
                    manager.channel().pipeline().addBefore("packet_handler",HANDLER,new PacketObserver(manager));
                observerReady=true;
            }catch(RuntimeException ex){observerError="hook unavailable: "+ex.getClass().getSimpleName();LOG.error("Could not attach fishing packet observer",ex);}
        });
    }
    private void resetConnection(){
        ++generation;clearCast();anchor=null;lastEnvironment=null;enabled=false;preview=false;target=null;
        run.stop("Set a target to start automatic fishing.",System.nanoTime());lastPublished="";activity="";
        lastTimeNanos=0;clockSamples=0;tickMillis=50;offsetMs=0;pingOverride=-1;run.speed(FishingRun.Speed.FAST);
    }
    @SubscribeEvent public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event){
        mc.addScheduledTask(()->{resetConnection();attach(event.getManager());});
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){
        mc.addScheduledTask(()->{resetConnection();observedManager=null;observerReady=false;});
    }
    @SubscribeEvent public void usingRod(PlayerInteractEvent.RightClickItem event){
        if(!enabled || automaticUse || event.getEntityPlayer()!=mc.player || !event.getWorld().isRemote || event.getItemStack().getItem()!=Items.FISHING_ROD)return;
        if(!preview && run.running()){
            if(run.state()!=FishingRun.State.WAIT_CAST){stop("Stopped by your manual rod use.");return;}
            if(mc.player.fishEntity!=null)return; // User clearing an older hook before their first cast.
            prepared=snapshot(event.getHand());anchor=new Anchor(prepared,mc.player.posX,mc.player.posY,mc.player.posZ);
            run.castSent(System.nanoTime());publish();
        }else if(preview){
            if(mc.player.fishEntity!=null){session=null;prepared=null;previewStatus="Retrieved. Cast again for a new preview.";}
            else {prepared=snapshot(event.getHand());previewStatus="Cast sent; waiting for the bobber packet.";}
        }
    }
    private CastInput snapshot(EnumHand hand){
        if(mc.player==null)return null;
        ItemStack rod=mc.player.getHeldItem(hand);
        return rod.getItem()!=Items.FISHING_ROD?null:new CastInput(hand,rod.copy(),mc.player.getLuck(),mc.player.dimension,
            mc.player.inventory.currentItem,mc.player.rotationYaw,mc.player.rotationPitch,System.nanoTime(),generation);
    }
    private void refreshSnapshots(){
        if(enabled && mc.player!=null && mc.player.fishEntity==null){mainSnapshot=snapshot(EnumHand.MAIN_HAND);offSnapshot=snapshot(EnumHand.OFF_HAND);}
    }
    private void spawn(HookSpawn packet,CastInput cast,float yaw,float pitch,long arrived){
        if(!enabled || mc.player==null || mc.world==null || packet.owner!=mc.player.getEntityId())return;
        if(cast==null || cast.generation!=generation)return;
        if(!preview && !run.hookArrived(System.nanoTime()))return;
        if(arrived-cast.time>5*SECOND){pause("The cast input is stale; aim and make a fresh cast.");return;}
        try{
            int count=0;
            for(Enchantment ench:Enchantment.REGISTRY){++count;
                if(!"minecraft".equals(ench.getRegistryName().getNamespace()))throw new IllegalStateException("A client mod adds enchantments; vanilla fishing prediction is unsupported.");}
            if(count!=30)throw new IllegalStateException("The enchantment registry differs from vanilla 1.12.2.");
            int lure=EnchantmentHelper.getFishingSpeedBonus(cast.rod),luck=EnchantmentHelper.getFishingLuckBonus(cast.rod);
            if(lure>3 || luck>3)throw new IllegalStateException("Use Lure/Luck of the Sea levels 0-III.");
            FishingRandom rng=FishingRandom.recover(packet.uuid);rng.uuid();
            FishingSimulation sim=new FishingSimulation(rng,packet.x,packet.y,packet.z,yaw,pitch,lure);
            if(velocity(sim.mx)!=packet.mx || velocity(sim.my)!=packet.my || velocity(sim.mz)!=packet.mz)
                throw new IllegalStateException("Launch mismatch. Stand still, aim, wait one second, then cast on the patched server.");
            FishingEnvironment environment=new FishingEnvironment(mc.world,packet.owner,packet.id,packet.x,packet.z);
            if(loot==null)loot=new FishingLoot();
            Session next=new Session(packet,cast,environment,arrived);next.samples.add(new Sample(sim,null));
            for(int i=0;i<2400;i++){
                sim.step(environment);
                FishingLoot.Result result=sim.catchable>0?loot.predict(sim.random,luck+cast.luck):null;
                next.samples.add(new Sample(sim,result));
                if(result!=null && next.firstBite<0)next.firstBite=sim.tick;
                if(next.firstBite>=0 && result==null)break;
            }
            if(next.firstBite<0)throw new IllegalStateException("No bite within the prediction limit. Use a clearer pool.");
            for(Sample sample:next.samples)if(sample.loot!=null && matches(sample))++next.matching;
            session=next;lastEnvironment=environment;prepared=null;
            if(preview){previewStatus="Bite in about "+String.format(Locale.ROOT,"%.1f",next.firstBite*tickMillis/1000)+"s; "+next.matching+" matching ticks.";say(previewStatus);}
            else {
                validateAnchor(false);run.predicted(next.matching>0,System.nanoTime());
                if(run.attempts()==1)say("Bobber detected and prediction working. "+(next.matching>0?"This cast can produce your target.":"No target this time; automatically recasting."));
                publish();
            }
        }catch(RuntimeException ex){LOG.warn("Fishing prediction rejected",ex);pause(ex.getMessage());}
    }
    private static int velocity(double d){return (int)(Math.max(-3.9D,Math.min(3.9D,d))*8000.0D);}
    private static double distance(double x,double y,double z,double a,double b,double c){return Math.sqrt((x-a)*(x-a)+(y-b)*(y-b)+(z-c)*(z-c));}
    private void time(SPacketTimeUpdate packet,long now){
        if(lastTimeNanos!=0 && packet.getTotalWorldTime()>lastWorldTime){
            double observed=(now-lastTimeNanos)/1e6/(packet.getTotalWorldTime()-lastWorldTime);
            if(observed>=40 && observed<=200)tickMillis=clockSamples++==0?observed:tickMillis*.75+observed*.25;
            else {clockSamples=0;tickMillis=50;}
        }
        lastTimeNanos=now;lastWorldTime=packet.getTotalWorldTime();
    }
    private void retry(String reason){
        if(preview){previewStatus=reason;return;}
        if(session!=null)session.chosen=null;
        run.retry(reason,System.nanoTime());publish();
    }
    private void bite(SPacketSoundEffect packet,long now){
        Session s=session;
        if(s==null || s.biteReceived!=0 || packet.getSound()!=SoundEvents.ENTITY_BOBBER_SPLASH || Math.abs(packet.getVolume()-.25F)>.001F)return;
        if(!preview && run.state()!=FishingRun.State.WAIT_BITE)return;
        Sample previous=s.samples.get(s.firstBite-1);
        if(distance(packet.getX(),packet.getY(),packet.getZ(),previous.x,previous.y,previous.z)>.25D)return;
        if(Math.abs((now-s.spawnNanos)/1e6-s.firstBite*tickMillis)>Math.max(300,3*tickMillis)){
            pause("The bite disagreed with prediction. Check the patched server, fishing settings and tick lag.");return;
        }
        s.biteReceived=now;
        if(preview){previewStatus="Bite confirmed. Preview mode does not reel automatically.";return;}
        if(!stableClock(now)){retry("Server clock is unstable. Retrying once it settles.");return;}
        for(Sample candidate:s.samples)if(candidate.loot!=null && matches(candidate)){
            long send=now+(long)(((candidate.tick-s.firstBite)*tickMillis+tickMillis*.5-ping()+offsetMs)*1e6);
            if(send-System.nanoTime()<150_000_000L)continue;
            s.chosen=candidate;s.sendNanos=send;run.scheduled(System.nanoTime());publish();return;
        }
        retry("No matching tick remains with enough network lead time. Trying another cast.");
    }
    private void verifyVelocity(SPacketEntityVelocity packet,long now){
        Session s=session;if(s==null || packet.getEntityID()!=s.id)return;
        int estimate=(int)((now-s.spawnNanos)/1e6/tickMillis);
        for(Sample sample:s.samples)if(Math.abs(sample.tick-estimate)<=8 &&
            Math.abs(velocity(sample.mx)-packet.getMotionX())<=1 && Math.abs(velocity(sample.my)-packet.getMotionY())<=1 &&
            Math.abs(velocity(sample.mz)-packet.getMotionZ())<=1)return;
        pause("The server's hook velocity disagrees with prediction. Check server settings and the pool.");
    }
    private void verifyPosition(int id,double x,double y,double z,long now){
        Session s=session;if(s==null || id!=s.id)return;
        s.wireX=x;s.wireY=y;s.wireZ=z;
        int estimate=(int)((now-s.spawnNanos)/1e6/tickMillis);
        for(Sample sample:s.samples)if(Math.abs(sample.tick-estimate)<=8 && distance(x,y,z,sample.x,sample.y,sample.z)<.004D)return;
        pause("The server's hook position disagrees with prediction. Check server settings and the pool.");
    }
    private void validateAnchor(boolean casting){
        if(anchor==null || mc.player==null || mc.world==null || mc.player.isDead || mc.player.dimension!=anchor.cast.dimension)
            throw new IllegalStateException("Player or dimension changed.");
        ItemStack rod=mc.player.getHeldItem(anchor.cast.hand);
        if(mc.player.inventory.currentItem!=anchor.cast.slot || rod.getItem()!=Items.FISHING_ROD ||
            !ItemStack.areItemStackTagsEqual(rod,anchor.cast.rod) || mc.player.getLuck()!=anchor.cast.luck)
            throw new IllegalStateException("The rod, hand, hotbar slot or player luck changed.");
        if(mc.player.getDistanceSq(anchor.x,anchor.y,anchor.z)>.04 ||
            Math.abs(MathHelper.wrapDegrees(mc.player.rotationYaw-anchor.cast.yaw))>.5F ||
            Math.abs(mc.player.rotationPitch-anchor.cast.pitch)>.5F)
            throw new IllegalStateException("You moved or changed your aim. Stay still so recasts reach the same pool.");
        if(rod.getMaxDamage()-rod.getItemDamage()<=3)throw new IllegalStateException("Rod durability is low. Repair or replace it before continuing.");
        if(casting && mc.player.inventory.getFirstEmptyStack()<0)throw new IllegalStateException("Inventory is full. Make room for the catch.");
        if(lastEnvironment!=null)lastEnvironment.validate();
    }
    private void validateSession(){
        Session s=session;if(s==null)return;
        if(mc.player==null || mc.world==null || mc.player.dimension!=s.cast.dimension ||
            !ItemStack.areItemStacksEqual(mc.player.getHeldItem(s.cast.hand),s.cast.rod) || mc.player.getLuck()!=s.cast.luck)
            throw new IllegalStateException("The player, rod or luck changed during this cast.");
        Entity hook=mc.world.getEntityByID(s.id);
        if(hook==null || hook.isDead || mc.player.getDistanceSq(s.wireX,s.wireY,s.wireZ)>1024)
            throw new IllegalStateException("The hook disappeared or moved too far away.");
        s.environment.validate();
    }
    private boolean inputBlocked(){return mc.currentScreen!=null || !mc.inGameHasFocus || mc.isGamePaused();}
    private void useRod(EnumHand hand){
        automaticUse=true;
        try{
            EnumActionResult result=mc.playerController.processRightClick(mc.player,mc.world,hand);
            mc.player.swingArm(hand);
            if(result!=EnumActionResult.SUCCESS)throw new IllegalStateException("The rod-use request was not accepted locally.");
        }finally{automaticUse=false;}
    }
    private void reel(boolean forCatch,long now){
        Session s=session;
        if(s==null)throw new IllegalStateException("No validated hook is available to retrieve.");
        validateAnchor(false);validateSession();
        if(forCatch)retrieval=new Retrieval(s,now,countTargetInventory());
        retiringHook=s.id;session=null;prepared=null;run.reelSent(forCatch,now);
        useRod(s.cast.hand);publish();
    }
    private void recast(long now){
        validateAnchor(true);
        if(retiringHook!=-1 || mc.player.fishEntity!=null)throw new IllegalStateException("An old hook is still present; recast withheld.");
        prepared=snapshot(anchor.cast.hand);
        if(prepared==null)throw new IllegalStateException("Hold the original fishing rod.");
        if(anchor.cast.hand==EnumHand.MAIN_HAND)mainSnapshot=prepared;else offSnapshot=prepared;
        run.castSent(now);useRod(anchor.cast.hand);publish();
    }
    private int countTargetInventory(){
        if(mc.player==null || target==null)return 0;
        int count=0;
        for(ItemStack stack:mc.player.inventory.mainInventory)if(!stack.isEmpty() && target.matches(stack))count+=stack.getCount();
        for(ItemStack stack:mc.player.inventory.offHandInventory)if(!stack.isEmpty() && target.matches(stack))count+=stack.getCount();
        return count;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        while(cancelKey.isPressed())stop("Stopped using the cancel key.");
        if(mc.player==null || mc.world==null)return;
        ensureObserver();refreshSnapshots();if(!enabled)return;
        long now=System.nanoTime();activity="";++ticks;
        try{
            if(session!=null && ticks%5==0)validateSession();
            if(!preview && run.running() && run.state()!=FishingRun.State.WAIT_CAST &&
                run.state()!=FishingRun.State.WAIT_PICKUP && anchor!=null && ticks%5==0)validateAnchor(false);
            if(session!=null && now-session.spawnNanos>(long)((session.samples.size()+20)*tickMillis*1e6)){
                if(preview){session=null;previewStatus="First bite window ended; cast again.";}
                else retry("Bite window ended. Trying another cast.");
            }
            if(!preview){
                run.checkTimeout(now);
                if(retrieval!=null && run.state()==FishingRun.State.WAIT_PICKUP){
                    boolean collected=false;
                    for(Integer id:retrieval.targetItems)if(retrieval.collectedByYou.contains(id))collected=true;
                    if(run.confirmPickup(collected,countTargetInventory()>retrieval.inventoryBefore,now)){
                        session=null;prepared=null;retrieval=null;
                    }else if(now-retrieval.sentAt>10*SECOND)activity="Target is still uncollected: make inventory space or move close enough to pick it up.";
                }
                if(retrieval!=null && run.state()!=FishingRun.State.VERIFYING && run.state()!=FishingRun.State.WAIT_PICKUP)retrieval=null;
                if(run.state()==FishingRun.State.PAUSED){session=null;prepared=null;}
                if(inputBlocked() && run.running() && run.state()!=FishingRun.State.WAIT_CAST)activity="Close the menu and focus Minecraft to continue.";
                if(run.state()==FishingRun.State.RECAST && !stableClock(now))activity="Waiting for a stable server tick clock before recasting.";
                if(!inputBlocked() && run.running()){
                    FishingRun.Action action=run.due(now);
                    if(action==FishingRun.Action.REEL)reel(false,now);
                    else if(action==FishingRun.Action.CAST && stableClock(now))recast(now);
                }
                publish();
            }
        }catch(RuntimeException ex){pause(ex.getMessage());}
    }
    @SubscribeEvent public void renderTick(TickEvent.RenderTickEvent event){
        if(event.phase==TickEvent.Phase.START){refreshSnapshots();return;}
        if(preview || session==null || session.chosen==null || run.state()!=FishingRun.State.TIMED_REEL)return;
        long now=System.nanoTime();
        if(now<session.sendNanos)return;
        if(now-session.sendNanos>20_000_000L){retry("Missed the timed frame. Recasting for another opportunity.");return;}
        if(inputBlocked())return;
        try{reel(true,now);}catch(RuntimeException ex){pause(ex.getMessage());}
    }
    private void itemSpawn(HookSpawn packet,long now){
        Retrieval r=retrieval;
        if(r==null || now<r.sentAt || now-r.sentAt>6*SECOND || !verifying())return;
        Sample chosen=r.session.chosen;
        if(distance(packet.x,packet.y,packet.z,chosen.x,chosen.y,chosen.z)<1)r.candidates.add(packet.id);
    }
    private boolean verifying(){return run.state()==FishingRun.State.VERIFYING || run.state()==FishingRun.State.WAIT_PICKUP;}
    private void metadata(SPacketEntityMetadata packet){
        if(mc.world==null)return;
        if(session!=null && packet.getEntityId()==session.id){
            Entity hook=mc.world.getEntityByID(packet.getEntityId());
            if(hook instanceof net.minecraft.entity.projectile.EntityFishHook &&
                ((net.minecraft.entity.projectile.EntityFishHook)hook).caughtEntity!=null)pause("The hook hit an entity. Choose a clear pool.");
        }
        Retrieval r=retrieval;
        if(r==null || !verifying() || !r.candidates.contains(packet.getEntityId()))return;
        Entity item=mc.world.getEntityByID(packet.getEntityId());
        if(!(item instanceof EntityItem))return;
        ItemStack actual=((EntityItem)item).getItem();if(actual.isEmpty())return;
        if(r.candidates.size()>1){pause("Several catch items appeared together; pickup verification is ambiguous.");return;}
        if(target.matches(actual)){
            r.targetItems.add(packet.getEntityId());run.targetObserved(System.nanoTime());publish();
        }else if(!r.evaluated){
            r.evaluated=true;String adjustment=calibrate(r,actual);
            run.missed("Caught "+actual.getDisplayName()+" instead of the target."+adjustment,System.nanoTime());publish();
        }
    }
    private String calibrate(Retrieval r,ItemStack actual){
        int count=0,delta=0;
        for(Sample sample:r.session.samples)if(sample.loot!=null && Math.abs(sample.tick-r.session.chosen.tick)<=5 &&
            ItemStack.areItemStacksEqual(actual,sample.loot.stack)){++count;delta=sample.tick-r.session.chosen.tick;}
        if(count!=1 || delta==0)return "";
        offsetMs=Math.max(-500,Math.min(500,offsetMs-(int)Math.round(delta*tickMillis)));
        return " Adjusted timing to "+offsetMs+" ms.";
    }
    private void collected(SPacketCollectItem packet){
        Retrieval r=retrieval;if(r==null || mc.player==null || !verifying())return;
        if(!r.candidates.contains(packet.getCollectedItemEntityID()))return;
        if(packet.getEntityID()==mc.player.getEntityId())r.collectedByYou.add(packet.getCollectedItemEntityID());
        else if(r.targetItems.contains(packet.getCollectedItemEntityID())){
            run.missed("Another entity collected the target.",System.nanoTime());publish();
        }
    }
    private void destroyed(SPacketDestroyEntities packet){
        for(int id:packet.getEntityIDs()){
            if(id==retiringHook){retiringHook=-1;run.hookRemoved(System.nanoTime());publish();}
            Retrieval r=retrieval;
            if(r!=null && run.state()==FishingRun.State.WAIT_PICKUP && r.targetItems.contains(id) &&
                !r.collectedByYou.contains(id)){
                run.missed("The target item disappeared before you collected it.",System.nanoTime());publish();
            }
        }
    }
    private String phaseLabel(){
        if(preview)return "PREVIEW";
        switch(run.state()){
            case WAIT_CAST:return "CAST ONCE TO BEGIN";
            case WAIT_SPAWN:return "WAITING FOR BOBBER";
            case PREDICTING:return "CHECKING THIS CAST";
            case WAIT_BITE:return "WAITING FOR BITE";
            case TIMED_REEL:return "TIMING THE REEL";
            case REJECTING:case WAIT_REMOVAL:case RECAST:return "RECASTING";
            case VERIFYING:return "CHECKING THE CATCH";
            case WAIT_PICKUP:return "TARGET LANDED - WAITING FOR PICKUP";
            case COMPLETE:return "TARGET COLLECTED";
            case PAUSED:return "PAUSED - NEEDS YOUR ATTENTION";
            default:return "STOPPED";
        }
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(!enabled || event.getType()!=RenderGameOverlayEvent.ElementType.ALL || mc.player==null)return;
        ScaledResolution screen=event.getResolution();int width=Math.min(330,screen.getScaledWidth()-16);
        int x=screen.getScaledWidth()-width-8,y=8;
        List<String> lines=new ArrayList<String>();
        lines.addAll(mc.fontRenderer.listFormattedStringToWidth("Target: "+(target==null?"none":target.label),width-16));
        if(!preview){
            lines.add("Cast "+run.attempts()+" | Rejected "+run.rejected()+" | Retries "+run.misses());
            lines.add("Recast speed: "+run.speed().name().toLowerCase(Locale.ROOT));
        }
        String text=preview?previewStatus:run.detail();
        if(!observerReady)text="Packet observer: "+(observerError.isEmpty()?"connecting...":observerError);
        if(!activity.isEmpty())text=activity;
        if(session!=null && !preview && run.state()==FishingRun.State.WAIT_BITE){
            double seconds=Math.max(0,(session.firstBite*tickMillis-(System.nanoTime()-session.spawnNanos)/1e6)/1000);
            text="Target predicted. Bite in about "+String.format(Locale.ROOT,"%.1f",seconds)+"s.";
            if(!activity.isEmpty())text=activity;
        }
        if(session!=null && session.chosen!=null && run.state()==FishingRun.State.TIMED_REEL)
            text="Reeling in "+String.format(Locale.ROOT,"%.2f",Math.max(0,(session.sendNanos-System.nanoTime())/1e9))+"s.";
        lines.addAll(mc.fontRenderer.listFormattedStringToWidth(text,width-16));
        lines.addAll(mc.fontRenderer.listFormattedStringToWidth(run.state()==FishingRun.State.WAIT_CAST?
            "Hold still after casting. /fishpredict cancel stops.":"/fishpredict cancel | resume | status",width-16));
        int color=run.state()==FishingRun.State.COMPLETE?0xff66dd88:run.state()==FishingRun.State.PAUSED?0xffffad55:0xff47cfff;
        int height=29+lines.size()*11;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.disableDepth();GlStateManager.enableBlend();
            Gui.drawRect(x,y,x+width,y+height,0xe016202a);Gui.drawRect(x,y,x+width,y+2,color);
            mc.fontRenderer.drawStringWithShadow("FISHING  |  "+phaseLabel(),x+8,y+8,color);
            int row=y+24;
            for(String line:lines){mc.fontRenderer.drawStringWithShadow(line,x+8,row,0xffeeeeee);row+=11;}
        }finally{GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();}
    }
    private final class PacketObserver extends ChannelDuplexHandler {
        final NetworkManager manager;
        private float yaw,pitch,castYaw,castPitch;
        private boolean rotationKnown;
        private CastInput cast;
        PacketObserver(NetworkManager manager){this.manager=manager;}
        @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise)throws Exception{
            if(message instanceof CPacketPlayer){CPacketPlayer p=(CPacketPlayer)message;yaw=p.getYaw(yaw);pitch=p.getPitch(pitch);rotationKnown=true;}
            if(message instanceof CPacketPlayerTryUseItem && enabled){
                EnumHand hand=((CPacketPlayerTryUseItem)message).getHand();
                CastInput input=hand==EnumHand.MAIN_HAND?mainSnapshot:offSnapshot;
                CastInput latest=prepared;
                if(latest!=null && latest.hand==hand && (input==null || latest.time>input.time))input=latest;
                cast=input;castYaw=rotationKnown?yaw:input==null?0:input.yaw;castPitch=rotationKnown?pitch:input==null?0:input.pitch;
            }
            super.write(ctx,message,promise);
        }
        @Override public void channelRead(ChannelHandlerContext ctx,Object message)throws Exception{
            final long now=System.nanoTime();
            final HookSpawn hook=message instanceof SPacketSpawnObject && ((SPacketSpawnObject)message).getType()==90?new HookSpawn((SPacketSpawnObject)message):null;
            final HookSpawn item=message instanceof SPacketSpawnObject && ((SPacketSpawnObject)message).getType()==2?new HookSpawn((SPacketSpawnObject)message):null;
            final CastInput input=cast;final float y=castYaw,p=castPitch;
            super.channelRead(ctx,message);
            if(!(message instanceof SPacketTimeUpdate || hook!=null || item!=null || message instanceof SPacketSoundEffect ||
                message instanceof SPacketEntityVelocity || message instanceof SPacketEntityTeleport || message instanceof SPacketEntity ||
                message instanceof SPacketEntityMetadata || message instanceof SPacketCollectItem || message instanceof SPacketDestroyEntities ||
                message instanceof SPacketRespawn || message instanceof SPacketPlayerPosLook))return;
            mc.addScheduledTask(()->{
                if(manager!=observedManager)return;
                try{
                    if(message instanceof SPacketTimeUpdate)time((SPacketTimeUpdate)message,now);
                    if(!enabled)return;
                    if(item!=null)itemSpawn(item,now);
                    if(hook!=null)spawn(hook,input,y,p,now);
                    if(message instanceof SPacketSoundEffect)bite((SPacketSoundEffect)message,now);
                    if(message instanceof SPacketEntityVelocity)verifyVelocity((SPacketEntityVelocity)message,now);
                    if(message instanceof SPacketEntityTeleport){SPacketEntityTeleport t=(SPacketEntityTeleport)message;verifyPosition(t.getEntityId(),t.getX(),t.getY(),t.getZ(),now);}
                    if(message instanceof SPacketEntity && session!=null){
                        SPacketEntity e=(SPacketEntity)message;Entity entity=e.getEntity(mc.world);
                        if(entity!=null && entity.getEntityId()==session.id &&
                            (e instanceof SPacketEntity.S15PacketEntityRelMove || e instanceof SPacketEntity.S17PacketEntityLookMove))
                            verifyPosition(session.id,MathHelper.lfloor(session.wireX*4096)/4096D+e.getX()/4096D,
                                MathHelper.lfloor(session.wireY*4096)/4096D+e.getY()/4096D,
                                MathHelper.lfloor(session.wireZ*4096)/4096D+e.getZ()/4096D,now);
                    }
                    if(message instanceof SPacketEntityMetadata)metadata((SPacketEntityMetadata)message);
                    if(message instanceof SPacketCollectItem)collected((SPacketCollectItem)message);
                    if(message instanceof SPacketDestroyEntities)destroyed((SPacketDestroyEntities)message);
                    if(message instanceof SPacketRespawn || message instanceof SPacketPlayerPosLook)
                        if(session!=null || (!preview && run.running() && run.state()!=FishingRun.State.WAIT_CAST))pause("Player teleported or changed dimension.");
                }catch(RuntimeException ex){LOG.warn("Fishing observation failed",ex);pause("Packet observation failed: "+ex.getMessage());}
            });
        }
    }
    private static final class CastInput {
        final EnumHand hand;final ItemStack rod;final float luck,yaw,pitch;final int dimension,slot;final long time,generation;
        CastInput(EnumHand hand,ItemStack rod,float luck,int dimension,int slot,float yaw,float pitch,long time,long generation){
            this.hand=hand;this.rod=rod;this.luck=luck;this.dimension=dimension;this.slot=slot;this.yaw=yaw;this.pitch=pitch;this.time=time;this.generation=generation;
        }
    }
    private static final class Anchor {
        final CastInput cast;final double x,y,z;
        Anchor(CastInput cast,double x,double y,double z){this.cast=cast;this.x=x;this.y=y;this.z=z;}
    }
    private static final class HookSpawn {
        final int id,owner,mx,my,mz;final UUID uuid;final double x,y,z;
        HookSpawn(SPacketSpawnObject p){id=p.getEntityID();owner=p.getData();uuid=p.getUniqueId();x=p.getX();y=p.getY();z=p.getZ();mx=p.getSpeedX();my=p.getSpeedY();mz=p.getSpeedZ();}
    }
    private static final class Sample {
        final int tick;final double x,y,z,mx,my,mz;final FishingLoot.Result loot;
        Sample(FishingSimulation s,FishingLoot.Result loot){tick=s.tick;x=s.x;y=s.y;z=s.z;mx=s.mx;my=s.my;mz=s.mz;this.loot=loot;}
    }
    private static final class Session {
        final int id;final CastInput cast;final FishingEnvironment environment;final long spawnNanos;
        final List<Sample> samples=new ArrayList<Sample>();int firstBite=-1,matching;
        long biteReceived,sendNanos;double wireX,wireY,wireZ;Sample chosen;
        Session(HookSpawn p,CastInput cast,FishingEnvironment environment,long arrived){id=p.id;this.cast=cast;this.environment=environment;spawnNanos=arrived;wireX=p.x;wireY=p.y;wireZ=p.z;}
    }
    private static final class Retrieval {
        final Session session;final long sentAt;final int inventoryBefore;
        final Set<Integer> candidates=new HashSet<Integer>(),targetItems=new HashSet<Integer>(),collectedByYou=new HashSet<Integer>();
        boolean evaluated;
        Retrieval(Session session,long sentAt,int inventoryBefore){this.session=session;this.sentAt=sentAt;this.inventoryBefore=inventoryBefore;}
    }
}
