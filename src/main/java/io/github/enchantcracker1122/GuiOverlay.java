package io.github.enchantcracker1122;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class GuiOverlay {
    private final CrackerController controller;

    public GuiOverlay(CrackerController controller) {
        this.controller = controller;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiEnchantment)) {
            return;
        }
        List<String> lines = controller.getOverlayLines();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = 176;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line) + 12);
        }
        width = Math.min(width, 250);
        int height = lines.size() * 10 + 10;
        int guiRight = (event.getGui().width + 176) / 2;
        int x = guiRight + 8;
        if (x + width > event.getGui().width) {
            x = Math.max(4, (event.getGui().width - 176) / 2 - width - 8);
        }
        int y = 8;
        Gui.drawRect(x, y, x + width, y + height, 0xc0101010);
        Gui.drawRect(x, y, x + width, y + 1, 0xff35c9ff);
        int textY = y + 5;
        for (String line : lines) {
            font.drawStringWithShadow(line, x + 6, textY, 0xffffff);
            textY += 10;
        }
    }
}
