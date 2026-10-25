package io.github.enchantcracker1122.fishing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class FishingLootTest {
    @BeforeClass public static void setup() {Bootstrap.register();}
    @Test public void matchesRebuiltPaperFullNbtAndXp()throws Exception {
        FishingLoot loot=new FishingLoot();int count=0;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/fishing-paper-oracle.tsv"),StandardCharsets.UTF_8))) {
            String line;
            while((line=in.readLine())!=null) {
                String[] cols=line.split("\t",4);
                FishingRandom rng=new FishingRandom(Long.parseLong(cols[0]));
                long seed=rng.state();
                FishingLoot.Result actual=loot.predict(rng,Float.parseFloat(cols[1]));
                ItemStack expected=new ItemStack(JsonToNBT.getTagFromJson(cols[3]));
                assertTrue("case "+count+": "+cols[3]+" != "+actual.stack.serializeNBT(),
                    ItemStack.areItemStacksEqual(expected,actual.stack));
                assertEquals(Integer.parseInt(cols[2]),actual.xp);
                assertEquals(seed,rng.state());++count;
            }
        }
        assertEquals(28672,count);
    }
    @Test public void targetsEnforceFishingRules() {
        new FishingTarget(new String[]{"target","enchanted_book","mending","1"});
        new FishingTarget(new String[]{"target","fishing_rod","mending","1","lure","3"});
        for(String[] bad:new String[][]{
            {"target","diamond"},{"target","fish","mending","1"},
            {"target","bow","infinity","1","mending","1"},{"target","enchanted_book","sharpness","5"}}) {
            try {new FishingTarget(bad);fail("Accepted impossible target");}catch(IllegalArgumentException expected){}
        }
    }
}
