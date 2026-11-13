package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Merchant "buy" tab: every item the village puts a price on, searchable. Click to choose (again to add
 * one, shift for +16, right click to remove), then Buy with credits.
 */
public class GuiBarterBuy extends GuiVillagerScreen {

    private GuiTextField search;
    private final List<ItemStack> shown = new ArrayList<>();
    private ItemStack selected = ItemStack.EMPTY;
    private int amount;
    private int scrollRow;
    private boolean dragging;

    public GuiBarterBuy(EntityHelpfulVillager v, NBTTagCompound data) {
        super(v, data, 195, 136);
        BarterState.load(data);
    }

    @Override
    public void refresh(NBTTagCompound data) {
        BarterState.load(data);
        filter();
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        String old = search == null ? "" : search.getText();
        search = new GuiTextField(0, fontRenderer, left + 82, top + 6, 89, fontRenderer.FONT_HEIGHT);
        search.setMaxStringLength(50);
        search.setEnableBackgroundDrawing(false);
        search.setTextColor(0xFFFFFF);
        search.setText(old);
        search.setFocused(true);
        buttonList.add(new GuiButton(0, left + 10, top + 110, 40, 20, I18n.format("helpfulvillagers.barter.buy")));
        filter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getText().toLowerCase(Locale.ROOT).trim();
        for (ItemStack s : BarterState.catalog) {
            if (q.isEmpty() || s.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) shown.add(s);
        }
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + 8) / 9 - 5);
    }

    private int total() {
        int p = BarterState.price(selected);
        return p < 0 ? -1 : p * amount;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0 && !selected.isEmpty()) {
            Net.action(villager, MsgAction.BUY, amount, "", selected);
            selected = ItemStack.EMPTY;
            amount = 0;
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            super.keyTyped(c, key);
            return;
        }
        if (search.textboxKeyTyped(c, key)) filter();
        else super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        int tab = BarterState.tabAt(mx, my, left, top);
        if (tab == 1) {
            Net.action(villager, MsgAction.BARTER_SELL);
            return;
        }
        super.mouseClicked(mx, my, button);
        search.mouseClicked(mx, my, button);
        if (in(mx, my, left + 9, top + 18, 162, 90)) {
            int idx = (scrollRow + (my - top - 18) / 18) * 9 + (mx - left - 9) / 18;
            if (idx >= 0 && idx < shown.size()) {
                ItemStack s = shown.get(idx);
                int step = isShiftKeyDown() ? 16 : 1;
                if (!selected.isEmpty() && ItemStack.areItemsEqual(selected, s) && ItemStack.areItemStackTagsEqual(selected, s)) {
                    amount = button == 1 ? Math.max(0, amount - step) : Math.min(s.getMaxStackSize() * 4, amount + step);
                    if (amount == 0) selected = ItemStack.EMPTY;
                } else if (button == 0) {
                    selected = s.copy();
                    amount = step;
                }
            }
        }
        if (in(mx, my, left + 175, top + 18, 12, 110)) dragging = true;
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        dragging = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - Integer.signum(wheel)));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        search.updateCursorCounter();
        for (GuiButton b : buttonList) {
            if (b.id == 0) b.enabled = !selected.isEmpty() && total() > 0 && total() <= BarterState.credits;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (dragging && maxScroll() > 0) {
            float f = (mouseY - top - 18 - 7.5F) / (110 - 15F);
            scrollRow = Math.max(0, Math.min(maxScroll(), Math.round(f * maxScroll())));
        }
        drawDefaultBackground();
        BarterState.drawTabs(this, mc, left, top, true, false);
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.BARTER_SEARCH);
        drawTexturedModalRect(left, top, 0, 0, xSize, ySize);
        BarterState.drawTabs(this, mc, left, top, true, true);
        mc.getTextureManager().bindTexture(Tex.BARTER_TABS);
        int thumb = maxScroll() == 0 ? 0 : (int) ((110 - 15) * (scrollRow / (float) maxScroll()));
        drawTexturedModalRect(left + 175, top + 18 + thumb, 232 + (maxScroll() > 0 ? 0 : 12), 0, 12, 15);
        search.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);

        fontRenderer.drawString(BarterState.creditsText(), left + 8, top + 6, 0x404040);
        ItemStack hover = ItemStack.EMPTY;
        if (!BarterState.ready) {
            drawCenteredString(fontRenderer, I18n.format("helpfulvillagers.barter.calculating"), left + 90, top + 55, 0xFFFFFF);
        }
        for (int i = 0; i < 45; i++) {
            int idx = scrollRow * 9 + i;
            if (idx >= shown.size()) break;
            int x = left + 9 + (i % 9) * 18, y = top + 18 + (i / 9) * 18;
            drawItem(shown.get(idx), x, y);
            if (in(mouseX, mouseY, x, y, 16, 16)) hover = shown.get(idx);
        }
        if (!selected.isEmpty()) {
            ItemStack show = selected.copy();
            show.setCount(amount);
            drawItem(show, left + 117, top + 112);
            int t = total();
            int color = t <= BarterState.credits ? 0x55FF55 : 0xFF5555;
            fontRenderer.drawStringWithShadow("$" + t, left + 137, top + 116, color);
        }
        drawItem(new ItemStack(net.minecraft.init.Items.EMERALD), left + 63, top + 112);
        if (in(mouseX, mouseY, left + 63, top + 112, 16, 16)) {
            drawHoveringText(I18n.format("helpfulvillagers.barter.emerald_hint", BarterState.emeraldValue), mouseX, mouseY);
        } else if (!hover.isEmpty()) {
            List<String> tip = tooltipOf(hover);
            tip.add(TextFormatting.GREEN + I18n.format("helpfulvillagers.barter.price", BarterState.price(hover)));
            drawHoveringText(tip, mouseX, mouseY);
        } else if (BarterState.tabAt(mouseX, mouseY, left, top) >= 0) {
            drawHoveringText(I18n.format(BarterState.tabAt(mouseX, mouseY, left, top) == 0 ? "helpfulvillagers.barter.tab_buy" : "helpfulvillagers.barter.tab_sell"), mouseX, mouseY);
        }
    }
}
