package io.github.enchantcracker1122.fishing;

import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

final class FishingTarget {
    private static final Set<String> CATCHES = new HashSet<String>(Arrays.asList(
        "fish","leather_boots","leather","bone","potion","string","fishing_rod","bowl",
        "stick","dye","tripwire_hook","rotten_flesh","waterlily","name_tag","saddle","bow","enchanted_book"));
    private final Item item;
    private final Map<Enchantment,Integer> requirements=new LinkedHashMap<Enchantment,Integer>();
    final String label;
    FishingTarget(String[] args) {
        if(args.length<2 || (args.length-2)%2!=0)
            throw new IllegalArgumentException("Use /fishpredict target <item> [<enchantment> <level> ...]");
        ResourceLocation id=new ResourceLocation(args[1]);
        if(!"minecraft".equals(id.getNamespace()) || !CATCHES.contains(id.getPath()))
            throw new IllegalArgumentException("That item is not in the vanilla fishing tables; use enchanted_book for books.");
        item=Item.REGISTRY.getObject(id);
        if(args.length>2 && item!=Items.ENCHANTED_BOOK && item!=Items.FISHING_ROD && item!=Items.BOW)
            throw new IllegalArgumentException("Only books, bows and rods are enchanted fishing catches.");
        for(int i=2;i<args.length;i+=2) {
            Enchantment ench=Enchantment.REGISTRY.getObject(new ResourceLocation(args[i]));
            int level;
            try { level=Integer.parseInt(args[i+1]); } catch(NumberFormatException ex) {
                throw new IllegalArgumentException("Enchantment levels must be whole numbers.");
            }
            if(ench==null || level<1 || level>ench.getMaxLevel() || ench.getMinEnchantability(level)>36)
                throw new IllegalArgumentException("Enchantment/level is not available from level-30 fishing loot.");
            if(item!=Items.ENCHANTED_BOOK && !ench.canApply(new ItemStack(item)))
                throw new IllegalArgumentException("That enchantment cannot appear on this catch.");
            for(Enchantment existing:requirements.keySet())
                if(!existing.isCompatibleWith(ench)) throw new IllegalArgumentException("Incompatible or duplicate enchantments.");
            requirements.put(ench,level);
        }
        label=String.join(" ",Arrays.copyOfRange(args,1,args.length));
    }
    boolean matches(ItemStack stack) {
        if(stack.getItem()!=item) return false;
        Map<Enchantment,Integer> present=EnchantmentHelper.getEnchantments(stack);
        for(Map.Entry<Enchantment,Integer> entry:requirements.entrySet())
            if(!present.containsKey(entry.getKey()) || present.get(entry.getKey())<entry.getValue()) return false;
        return true;
    }
}
