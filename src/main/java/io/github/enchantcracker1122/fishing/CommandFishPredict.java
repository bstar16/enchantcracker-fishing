package io.github.enchantcracker1122.fishing;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public final class CommandFishPredict extends CommandBase {
    private final FishingController controller;
    public CommandFishPredict(FishingController controller) { this.controller=controller; }
    @Override public String getName() { return "fishpredict"; }
    @Override public String getUsage(ICommandSender sender) { return "/fishpredict target <item> [enchant level ...] | cancel | resume | status | speed <fast|normal> | on | off | list | offset | ping"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender) { return true; }
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) {
        try { controller.command(args); }
        catch(IllegalArgumentException ex) { sender.sendMessage(new TextComponentString("[FishPredict] "+ex.getMessage())); }
    }
    @Override public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos) {
        if(args.length==1)return getListOfStringsMatchingLastWord(args,"target","cancel","stop","resume","status","speed","on","off","list","clear","arm","offset","ping");
        if(args.length==2 && "speed".equals(args[0]))return getListOfStringsMatchingLastWord(args,"fast","normal");
        if(args.length==2 && "target".equals(args[0]))return getListOfStringsMatchingLastWord(args,
            "enchanted_book","fishing_rod","bow","name_tag","saddle","waterlily","fish","leather_boots","leather","bone","potion","string","bowl","stick","dye","tripwire_hook","rotten_flesh");
        if(args.length>=3 && args.length%2==1 && "target".equals(args[0]))return getListOfStringsMatchingLastWord(args,
            "mending","unbreaking","luck_of_the_sea","lure","power","infinity","punch","flame","efficiency","fortune","silk_touch");
        if(args.length==2 && "ping".equals(args[0]))return getListOfStringsMatchingLastWord(args,"auto");
        return Collections.emptyList();
    }
}
