package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import com.electrifiedded.helpfulvillagers.network.ScreenData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Village craft queue: every order with who asked for it; selecting one shows what it still lacks
 * (or, for the order this villager is working on, what it still has to fetch and what it carries).
 */
public class GuiCraftStats extends GuiVillagerScreen {

    private static final int BACK = 6, REMOVE = 7;

    private final List<ItemStack> queue = new ArrayList<>();
    private final List<String> requester = new ArrayList<>();
    private final List<Boolean> byPlayer = new ArrayList<>();
    private final List<Boolean> mine = new ArrayList<>();
    private final List<List<ItemStack>> missing = new ArrayList<>();
    private ItemStack current = ItemStack.EMPTY;
    private List<ItemStack> needed = new ArrayList<>();
    private List<ItemStack> collected = new ArrayList<>();
    private int selected = -1;
    private final int[] offset = new int[3];

    public GuiCraftStats(EntityHelpfulVillager v, NBTTagCompound data) {
        super(v, data, 195, 136);
    }

    @Override
    public void initGui() {
        super.initGui();
        queue.clear(); requester.clear(); byPlayer.clear(); mine.clear(); missing.clear();
        NBTTagList q = data.getTagList("Queue", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < q.tagCount(); i++) {
            NBTTagCompound e = q.getCompoundTagAt(i);
            List<ItemStack> it = ScreenData.stacks(e, "Item");
            queue.add(it.isEmpty() ? ItemStack.EMPTY : it.get(0));
            requester.add(e.getString("Name"));
            byPlayer.add(e.getBoolean("Player"));
            mine.add(e.getBoolean("Mine"));
            missing.add(ScreenData.stacks(e, "Missing"));
        }
        List<ItemStack> cur = ScreenData.stacks(data, "Current");
        current = cur.isEmpty() ? ItemStack.EMPTY : cur.get(0);
        needed = ScreenData.stacks(data, "Needed");
        collected = ScreenData.stacks(data, "Collected");
        if (selected >= queue.size()) selected = -1;
        int[] rowY = {21, 57, 93};
        for (int r = 0; r < 3; r++) {
            buttonList.add(new Arrow(r * 2, left + 4, top + rowY[r] - 1, true));
            buttonList.add(new Arrow(r * 2 + 1, left + 180, top + rowY[r] - 1, false));
        }
        buttonList.add(new GuiButton(BACK, left + 20, top + 112, 60, 20, I18n.format("gui.back")));
        buttonList.add(new GuiButton(REMOVE, left + 120, top + 112, 60, 20, I18n.format("helpfulvillagers.gui.remove")));
        updateButtons();
    }

    private List<ItemStack> row(int r) {
        if (r == 0) {
            List<ItemStack> l = new ArrayList<>();
            if (!current.isEmpty()) l.add(current);
            l.addAll(queue);
            return l;
        }
        if (selected < 0) return r == 1 ? needed : collected;
        return r == 1 ? missing.get(selected) : new ArrayList<>();
    }

    private int queueBase() {
        return current.isEmpty() ? 0 : 1;
    }

    private void updateButtons() {
        for (GuiButton b : buttonList) {
            if (b.id < 6) {
                int r = b.id / 2;
                boolean left = b.id % 2 == 0;
                b.enabled = left ? offset[r] > 0 : offset[r] + 9 < row(r).size();
            }
            if (b.id == REMOVE) b.enabled = selected >= 0 && (mine.get(selected) || !byPlayer.get(selected));
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id < 6) {
            int r = b.id / 2;
            offset[r] = Math.max(0, offset[r] + (b.id % 2 == 0 ? -1 : 1));
        } else if (b.id == BACK) {
            Net.action(villager, MsgAction.CRAFT_MENU);
        } else if (b.id == REMOVE && selected >= 0) {
            Net.action(villager, MsgAction.REMOVE_ORDER, selected);
            selected = -1;
        }
        updateButtons();
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (in(mx, my, left + 18, top + 21, 162, 16)) {
            int idx = (mx - left - 18) / 18 + offset[0] - queueBase();
            selected = idx >= 0 && idx < queue.size() ? idx : -1;
            offset[1] = offset[2] = 0;
            updateButtons();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.CRAFT_DETAILS);
        drawTexturedModalRect(left, top, 0, 0, xSize, ySize);
        fontRenderer.drawString(I18n.format("helpfulvillagers.gui.queue_title"), left + 18, top + 10, 0x404040);
        fontRenderer.drawString(I18n.format(selected < 0 ? "helpfulvillagers.gui.needed" : "helpfulvillagers.gui.missing"), left + 18, top + 46, 0x404040);
        fontRenderer.drawString(I18n.format("helpfulvillagers.gui.collected"), left + 18, top + 82, 0x404040);
        super.drawScreen(mouseX, mouseY, partialTicks);
        int[] rowY = {21, 57, 93};
        List<String> tip = null;
        for (int r = 0; r < 3; r++) {
            List<ItemStack> items = row(r);
            for (int i = 0; i < 9 && offset[r] + i < items.size(); i++) {
                int idx = offset[r] + i;
                int x = left + 18 + i * 18, y = top + rowY[r];
                if (r == 0 && idx - queueBase() == selected && selected >= 0) drawRect(x - 1, y - 1, x + 17, y + 17, 0x8000FF00);
                if (r == 0 && idx == 0 && queueBase() == 1) drawRect(x - 1, y - 1, x + 17, y + 17, 0x80FFD700);
                drawItem(items.get(idx), x, y);
                if (in(mouseX, mouseY, x, y, 16, 16)) {
                    tip = tooltipOf(items.get(idx));
                    if (r == 0) {
                        int q = idx - queueBase();
                        if (q < 0) tip.add(TextFormatting.GOLD + I18n.format("helpfulvillagers.gui.crafting_now"));
                        else tip.add(TextFormatting.GRAY + I18n.format(byPlayer.get(q) ? "helpfulvillagers.gui.by_player" : "helpfulvillagers.gui.by_villager", requester.get(q)));
                    }
                }
            }
        }
        if (tip != null) drawHoveringText(tip, mouseX, mouseY);
    }

    private static final class Arrow extends GuiButton {
        private final boolean leftArrow;

        Arrow(int id, int x, int y, boolean leftArrow) {
            super(id, x, y, 12, 19, "");
            this.leftArrow = leftArrow;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            mc.getTextureManager().bindTexture(Tex.CRAFT_DETAILS);
            GlStateManager.color(1F, 1F, 1F, 1F);
            int u = 195 + (!enabled ? width * 2 : hovered ? width : 0);
            int v = leftArrow ? height : 0;
            drawTexturedModalRect(x, y, u, v, width, height);
        }
    }
}
