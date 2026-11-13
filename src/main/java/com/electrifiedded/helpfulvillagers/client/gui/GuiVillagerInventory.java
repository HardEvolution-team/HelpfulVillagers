package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.gui.ContainerVillagerInventory;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

public class GuiVillagerInventory extends GuiContainer {

    private final ContainerVillagerInventory container;

    public GuiVillagerInventory(ContainerVillagerInventory container) {
        super(container);
        this.container = container;
        xSize = 176;
        ySize = 175;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = container.villager.getDisplayName().getUnformattedText();
        fontRenderer.drawString(title, xSize / 2 - fontRenderer.getStringWidth(title) / 2, -10, 0xFFFFFF);
        fontRenderer.drawString(I18n.format("helpfulvillagers.gui.equipment"), 8, 72, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.INVENTORY);
        // the texture has equipment hint icons to the right of the panel; include them
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize + 12, ySize);
    }
}
