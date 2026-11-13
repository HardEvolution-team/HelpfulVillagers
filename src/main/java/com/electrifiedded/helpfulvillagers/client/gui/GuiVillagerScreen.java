package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/** Base of the non-container villager screens: closes itself when the villager is gone. */
public abstract class GuiVillagerScreen extends GuiScreen {

    protected final EntityHelpfulVillager villager;
    protected NBTTagCompound data;
    protected int left, top;
    protected int xSize, ySize;

    protected GuiVillagerScreen(EntityHelpfulVillager villager, NBTTagCompound data, int xSize, int ySize) {
        this.villager = villager;
        this.data = data;
        this.xSize = xSize;
        this.ySize = ySize;
    }

    /** New data from the server for this screen. */
    public void refresh(NBTTagCompound data) {
        this.data = data;
        initGui();
    }

    @Override
    public void initGui() {
        left = (width - xSize) / 2;
        top = (height - ySize) / 2;
        buttonList.clear();
    }

    @Override
    public void updateScreen() {
        if (!villager.isEntityAlive() || mc.player.getDistanceSq(villager) > 100) mc.player.closeScreen();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    protected void drawPanel(int x, int y, int w, int h) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.DIALOG);
        drawTexturedModalRect(x, y, 0, 0, w, h);
    }

    protected void drawItem(ItemStack s, int x, int y) {
        if (s.isEmpty()) return;
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        itemRender.renderItemAndEffectIntoGUI(mc.player, s, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, s, x, y, s.getCount() > 1 ? String.valueOf(s.getCount()) : null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
    }

    protected static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    protected List<String> tooltipOf(ItemStack s) {
        return s.isEmpty() ? new ArrayList<>() : getItemToolTip(s);
    }
}
