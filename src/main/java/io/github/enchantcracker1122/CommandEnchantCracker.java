package io.github.enchantcracker1122;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public final class CommandEnchantCracker extends CommandBase {
    private final CrackerController controller;

    public CommandEnchantCracker(CrackerController controller) {
        this.controller = controller;
    }

    @Override
    public String getName() {
        return "enchcracker";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/enchcracker target <item> <enchantment> [level] [<enchantment> <level> ...] | status | reset | rng | execute | pause | cancel";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[EnchantCracker] "
                + TextFormatting.RESET + controller.getStatus()));
            return;
        }
        if ("reset".equalsIgnoreCase(args[0])) {
            controller.resetAll();
            return;
        }
        if ("rng".equalsIgnoreCase(args[0])) {
            controller.startCalibration();
            return;
        }
        if ("execute".equalsIgnoreCase(args[0])) {
            controller.executePlan();
            return;
        }
        if ("pause".equalsIgnoreCase(args[0])) {
            controller.pauseDrops();
            return;
        }
        if ("cancel".equalsIgnoreCase(args[0])) {
            controller.cancelPlan();
            return;
        }
        if ("target".equalsIgnoreCase(args[0])) {
            if (args.length < 3) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + getUsage(sender)));
                return;
            }
            try {
                controller.planTarget(args[1], TargetRequirements.parse(args, 2));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + e.getMessage()));
            }
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.RED + getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "reset", "rng", "target", "execute", "pause", "cancel");
        }
        if (args.length == 2 && "target".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<String>();
            for (ResourceLocation id : Item.REGISTRY.getKeys()) {
                values.add(id.toString());
            }
            return getListOfStringsMatchingLastWord(args, values);
        }
        if (args.length >= 3 && args.length % 2 == 1 && "target".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<String>();
            for (ResourceLocation id : Enchantment.REGISTRY.getKeys()) {
                values.add(id.toString());
            }
            return getListOfStringsMatchingLastWord(args, values);
        }
        if (args.length >= 4 && args.length % 2 == 0 && "target".equalsIgnoreCase(args[0])) {
            String name = args[args.length - 2];
            if (!name.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
                return Collections.emptyList();
            }
            Enchantment enchantment = Enchantment.REGISTRY.getObject(new ResourceLocation(name));
            if (enchantment != null) {
                List<String> levels = new ArrayList<String>();
                for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
                    levels.add(Integer.toString(level));
                }
                return getListOfStringsMatchingLastWord(args, levels);
            }
        }
        return Collections.emptyList();
    }
}
