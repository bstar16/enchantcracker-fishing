package io.github.enchantcracker1122;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
    modid = EnchantCrackerMod.MODID,
    name = EnchantCrackerMod.NAME,
    version = EnchantCrackerMod.VERSION,
    clientSideOnly = true,
    acceptableRemoteVersions = "*"
)
public final class EnchantCrackerMod {
    public static final String MODID = "enchantcracker1122";
    public static final String NAME = "Enchant Cracker 1.12.2";
    public static final String VERSION = "1.4.2";

    public static final CrackerController CONTROLLER = new CrackerController();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(CONTROLLER);
        MinecraftForge.EVENT_BUS.register(new GuiOverlay(CONTROLLER));
        ClientCommandHandler.instance.registerCommand(new CommandEnchantCracker(CONTROLLER));
        io.github.enchantcracker1122.fishing.FishingController fishing = new io.github.enchantcracker1122.fishing.FishingController();
        fishing.registerKey();
        MinecraftForge.EVENT_BUS.register(fishing);
        ClientCommandHandler.instance.registerCommand(new io.github.enchantcracker1122.fishing.CommandFishPredict(fishing));
    }
}
