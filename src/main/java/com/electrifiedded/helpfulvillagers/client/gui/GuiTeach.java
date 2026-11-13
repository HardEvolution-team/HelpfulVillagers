package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.gui.ContainerTeach;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

/** Lay out a recipe and press Teach; Reset forgets everything this villager was taught. */
public class GuiTeach extends GuiContainer {

    private final ContainerTeach container;
    private String message = "";
    private int messageTicks;

    public GuiTeach(ContainerTeach container) {
        super(container);
        this.container = container;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.add(new GuiButton(0, guiLeft + 88, guiTop + 58, 80, 20, I18n.format("helpfulvillagers.gui.teach_button")));
        buttonList.add(new GuiButton(1, guiLeft + xSize + 4, guiTop + 4, 70, 20, I18n.format("helpfulvillagers.gui.reset_recipes")));
        buttonList.add(new GuiButton(2, guiLeft - 74, guiTop + 4, 70, 20, I18n.format("gui.back")));
    }

    public void showResult(String key) {
        message = I18n.format(key);
        messageTicks = 60;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) Net.action(container.villager, MsgAction.TEACH_RECIPE);
        else if (b.id == 1) Net.action(container.villager, MsgAction.RESET_RECIPES);
        else if (b.id == 2) Net.action(container.villager, MsgAction.CRAFT_MENU);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (messageTicks > 0) messageTicks--;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (messageTicks > 0) drawCenteredString(fontRenderer, message, width / 2, guiTop - 12, 0xFFFF55);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("helpfulvillagers.gui.teach"), 28, 6, 0x404040);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.CRAFTING_TABLE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }
}
