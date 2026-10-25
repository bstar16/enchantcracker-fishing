package io.github.enchantcracker1122.fishing;

import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.*;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.*;

/** Immutable vanilla 1.12.2 fishing tables, evaluated by Minecraft's own loot classes.
 * Built explicitly to avoid client loot-load events/resource packs changing predictions.
 */
public final class FishingLoot extends LootTableManager {
    private static final LootCondition[] NONE = new LootCondition[0];
    private final Map<ResourceLocation,LootTable> tables = new HashMap<ResourceLocation,LootTable>();
    private static ResourceLocation id(String name) { return new ResourceLocation("minecraft:gameplay/fishing"+name); }
    public FishingLoot() {
        super(null);
        NBTTagCompound water = new NBTTagCompound(); water.setString("Potion","minecraft:water");
        tables.put(id("/junk"), table(
            item(Items.LEATHER_BOOTS,10,damage(.90F)),item(Items.LEATHER,10),item(Items.BONE,10),
            item(Items.POTIONITEM,10,new SetNBT(NONE,water)),item(Items.STRING,5),
            item(Items.FISHING_ROD,2,damage(.90F)),item(Items.BOWL,10),item(Items.STICK,5),
            item(Items.DYE,1,data(0),new SetCount(NONE,new RandomValueRange(10))),
            item(Item.getByNameOrId("minecraft:tripwire_hook"),10),item(Items.ROTTEN_FLESH,10)));
        tables.put(id("/treasure"),table(
            item(Item.getByNameOrId("minecraft:waterlily"),1),item(Items.NAME_TAG,1),item(Items.SADDLE,1),
            item(Items.BOW,1,damage(.25F),enchanted()),item(Items.FISHING_ROD,1,damage(.25F),enchanted()),
            item(Items.BOOK,1,enchanted())));
        tables.put(id("/fish"),table(item(Items.FISH,60,data(0)),item(Items.FISH,25,data(1)),
                                  item(Items.FISH,2,data(2)),item(Items.FISH,13,data(3))));
        tables.put(id(""),table(new LootEntryTable(id("/junk"),10,-2,NONE,"junk"),
            new LootEntryTable(id("/treasure"),5,2,NONE,"treasure"),
            new LootEntryTable(id("/fish"),85,-1,NONE,"fish")));
    }
    @Override public void reloadLootTables() { /* super constructor: do not load Forge tables */ }
    @Override public LootTable getLootTableFromLocation(ResourceLocation id) {
        LootTable table=tables.get(id);
        if(table==null) throw new IllegalArgumentException("Unsupported fishing table: "+id);
        return table;
    }
    private static LootTable table(LootEntry... entries) {
        return new LootTable(new LootPool[]{new LootPool(entries,NONE,new RandomValueRange(1),
                                                        new RandomValueRange(0),"main")});
    }
    private static LootEntry item(Item item,int weight,LootFunction... functions) {
        return new LootEntryItem(item,weight,0,functions,NONE,item.getRegistryName().toString());
    }
    private static LootFunction data(int n) { return new SetMetadata(NONE,new RandomValueRange(n)); }
    private static LootFunction damage(float max) { return new SetDamage(NONE,new RandomValueRange(0,max)); }
    private static LootFunction enchanted() { return new EnchantWithLevels(NONE,new RandomValueRange(30),true); }
    public Result predict(FishingRandom physics,float luck) {
        FishingRandom trial=physics.copy();
        LootContext context=new LootContext(luck,null,this,null,null,null);
        List<ItemStack> items=getLootTableFromLocation(id("")).generateLootForPools(trial,context);
        if(items.size()!=1) throw new IllegalStateException("Expected one vanilla fishing result");
        return new Result(items.get(0),1+trial.nextInt(6));
    }
    public static final class Result {
        public final ItemStack stack;
        public final int xp;
        public Result(ItemStack stack,int xp) { this.stack=stack; this.xp=xp; }
        public String description() {
            StringBuilder s=new StringBuilder(stack.getCount()+"x "+stack.getDisplayName());
            for(Map.Entry<Enchantment,Integer> enchant:EnchantmentHelper.getEnchantments(stack).entrySet())
                s.append("; ").append(enchant.getKey().getTranslatedName(enchant.getValue()));
            if(stack.isItemStackDamageable()) s.append(" [damage ").append(stack.getItemDamage()).append("]");
            return s.append("; XP ").append(xp).toString();
        }
    }
}
