package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.gui.ContainerBarterSell;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import java.io.IOException;

/** Merchant "sell" tab: sell items or deposit emeralds for credits, withdraw credits as emeralds. */
public class GuiBarterSell extends GuiContainer {

    private final ContainerBarterSell container;
    private GuiButton sell, withdraw;

    public GuiBarterSell(ContainerBarterSell container) {
        super(container);
        this.container = container;
        xSize = 195;
        ySize = 136;
    }

    @Override
    public void initGui() {
        super.initGui();
        sell = new GuiButton(11, guiLeft + 85, guiTop + 32, 42, 20, I18n.format("helpfulvillagers.barter.sell"));
        withdraw = new GuiButton(12, guiLeft + 135, guiTop + 32, 50, 20, I18n.format("helpfulvillagers.barter.withdraw"));
        buttonList.add(sell);
        buttonList.add(withdraw);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ItemStack s = container.sellSlot.getStackInSlot(0);
        boolean emerald = container.holdsEmeralds();
        sell.displayString = I18n.format(emerald ? "helpfulvillagers.barter.deposit" : "helpfulvillagers.barter.sell");
        sell.enabled = !s.isEmpty() && (emerald || BarterState.price(s) > 0);
        ItemStack out = container.outputSlot.getStackInSlot(0);
        withdraw.enabled = BarterState.credits >= BarterState.emeraldValue && out.getCount() < 64;
        if (!container.villager.isEntityAlive()) mc.player.closeScreen();
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 11) Net.action(container.villager, MsgAction.SELL);
        else if (b.id == 12) Net.action(container.villager, MsgAction.WITHDRAW, isShiftKeyDown() ? 10 : 1);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (BarterState.tabAt(mx, my, guiLeft, guiTop) == 0) {
            Net.action(container.villager, MsgAction.BARTER_BUY);
            return;
        }
        super.mouseClicked(mx, my, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.disableLighting();
        BarterState.drawTabs(this, mc, guiLeft, guiTop, false, true);
        int tab = BarterState.tabAt(mouseX, mouseY, guiLeft, guiTop);
        if (tab >= 0) drawHoveringText(I18n.format(tab == 0 ? "helpfulvillagers.barter.tab_buy" : "helpfulvillagers.barter.tab_sell"), mouseX, mouseY);
        else renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        ItemStack s = container.sellSlot.getStackInSlot(0);
        if (!s.isEmpty()) {
            int value = container.holdsEmeralds() ? s.getCount() * BarterState.emeraldValue : BarterState.price(s) * s.getCount();
            String text = BarterState.price(s) < 0 && !container.holdsEmeralds() ? I18n.format("helpfulvillagers.barter.no_price") : "$" + value;
            fontRenderer.drawStringWithShadow(text, 120, 16, 0xFFFFFF);
        }
        String c = BarterState.creditsText();
        fontRenderer.drawStringWithShadow(c, xSize - 6 - fontRenderer.getStringWidth(c), -10, 0xFFFFFF);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        BarterState.drawTabs(this, mc, guiLeft, guiTop, false, false);
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.BARTER_INVENTORY);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        GuiInventory.drawEntityOnScreen(guiLeft + 44, guiTop + 47, 20, guiLeft + 44 - mouseX, guiTop + 20 - mouseY, mc.player);
    }
}
