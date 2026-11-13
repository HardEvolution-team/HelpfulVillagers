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
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** What a villager can make: pick an item, set the amount, and put it on the village craft queue. */
public class GuiCraftMenu extends GuiVillagerScreen {

    private static final int QUEUE = 3, TEACH = 4, STATS = 5, UP = 6, DOWN = 7;

    private List<ItemStack> craftables = new ArrayList<>();
    private ItemStack current = ItemStack.EMPTY;
    private ItemStack selected = ItemStack.EMPTY;
    private int amount = 1;
    private int scrollRow;
    private boolean dragging;

    public GuiCraftMenu(EntityHelpfulVillager v, NBTTagCompound data) {
        super(v, data, 195, 136);
    }

    @Override
    public void initGui() {
        super.initGui();
        craftables = ScreenData.stacks(data, "Craftables");
        current = data.hasKey("Current") ? new ItemStack(data.getCompoundTag("Current")) : ItemStack.EMPTY;
        buttonList.add(new GuiButton(QUEUE, left + 83, top + 110, 60, 20, I18n.format("helpfulvillagers.gui.queue_item")));
        buttonList.add(new SpriteButton(UP, left + 58, top + 70, 19, 12, 0, 206, true));
        buttonList.add(new SpriteButton(DOWN, left + 58, top + 102, 19, 12, 0, 230, true));
        buttonList.add(new SpriteButton(TEACH, left + 173, top + 90, 16, 15, 17, 189, false));
        buttonList.add(new SpriteButton(STATS, left + 172, top + 110, 14, 16, 0, 188, false));
        updateButtons();
    }

    private void updateButtons() {
        for (GuiButton b : buttonList) {
            if (b.id == QUEUE) b.enabled = !selected.isEmpty();
            if (b.id == UP) b.enabled = !selected.isEmpty() && amount < selected.getMaxStackSize();
            if (b.id == DOWN) b.enabled = !selected.isEmpty() && amount > 1;
        }
    }

    private int rows() {
        return (craftables.size() + 8) / 9;
    }

    private int maxScroll() {
        return Math.max(0, rows() - 3);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        int step = isShiftKeyDown() ? 8 : 1;
        switch (b.id) {
            case QUEUE:
                Net.action(villager, MsgAction.QUEUE, amount, "", selected);
                break;
            case UP:
                amount = Math.min(selected.getMaxStackSize(), amount + step);
                break;
            case DOWN:
                amount = Math.max(1, amount - step);
                break;
            case TEACH:
                Net.action(villager, MsgAction.TEACH);
                break;
            case STATS:
                Net.action(villager, MsgAction.STATS);
                break;
            default:
                break;
        }
        updateButtons();
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        int idx = slotAt(mx, my);
        if (idx >= 0 && idx < craftables.size()) {
            selected = craftables.get(idx).copy();
            amount = Math.max(1, selected.getCount());
            selected.setCount(1);
            updateButtons();
        }
        if (in(mx, my, left + 174, top + 8, 12, 52)) dragging = true;
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

    private int slotAt(int mx, int my) {
        if (!in(mx, my, left + 8, top + 8, 162, 54)) return -1;
        int c = (mx - left - 8) / 18, r = (my - top - 8) / 18;
        return (r + scrollRow) * 9 + c;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (dragging && maxScroll() > 0) {
            float f = (mouseY - top - 8 - 7.5F) / (52 - 15F);
            scrollRow = Math.max(0, Math.min(maxScroll(), Math.round(f * maxScroll())));
        }
        drawDefaultBackground();
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(Tex.CRAFT_MENU);
        drawTexturedModalRect(left, top, 0, 0, xSize, ySize);
        int thumbY = maxScroll() == 0 ? 0 : (int) ((52 - 15) * (scrollRow / (float) maxScroll()));
        drawTexturedModalRect(left + 174, top + 8 + thumbY, maxScroll() > 0 ? 0 : 12, 241, 12, 15);
        drawCenteredString(fontRenderer, villager.getDisplayName().getFormattedText(), width / 2, top - 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);

        ItemStack hover = ItemStack.EMPTY;
        for (int i = 0; i < 27; i++) {
            int idx = scrollRow * 9 + i;
            if (idx >= craftables.size()) break;
            int x = left + 8 + (i % 9) * 18, y = top + 8 + (i / 9) * 18;
            drawItem(craftables.get(idx), x, y);
            if (in(mouseX, mouseY, x, y, 16, 16)) hover = craftables.get(idx);
        }
        if (!selected.isEmpty()) {
            ItemStack show = selected.copy();
            show.setCount(amount);
            drawItem(show, left + 59, top + 84);
            if (in(mouseX, mouseY, left + 59, top + 84, 16, 16)) hover = selected;
        }
        if (!current.isEmpty()) {
            drawItem(current, left + 117, top + 85);
            if (in(mouseX, mouseY, left + 117, top + 85, 16, 16)) {
                List<String> tip = tooltipOf(current);
                tip.add(0, I18n.format("helpfulvillagers.gui.crafting_now"));
                drawHoveringText(tip, mouseX, mouseY);
                return;
            }
        }
        if (craftables.isEmpty()) {
            drawCenteredString(fontRenderer, I18n.format("helpfulvillagers.gui.nothing_craftable"), left + 89, top + 30, 0xFFFFFF);
        }
        if (!hover.isEmpty()) {
            renderToolTip(hover, mouseX, mouseY);
            return;
        }
        for (GuiButton b : buttonList) {
            if (!b.isMouseOver()) continue;
            if (b.id == TEACH) drawHoveringText(I18n.format("helpfulvillagers.gui.teach"), mouseX, mouseY);
            if (b.id == STATS) drawHoveringText(I18n.format("helpfulvillagers.gui.stats"), mouseX, mouseY);
        }
    }

    /** Small button drawn from the craft menu texture. */
    private static final class SpriteButton extends GuiButton {
        private final int u, v;
        private final boolean arrow;

        SpriteButton(int id, int x, int y, int w, int h, int u, int v, boolean arrow) {
            super(id, x, y, w, h, "");
            this.u = u;
            this.v = v;
            this.arrow = arrow;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            mc.getTextureManager().bindTexture(Tex.CRAFT_MENU);
            GlStateManager.color(1F, 1F, 1F, 1F);
            int du = u, dv = v;
            if (arrow) {
                if (!enabled) du += width;
                else if (hovered) dv += height;
            } else if (hovered) {
                du += 1;
                dv += 1;
            }
            drawTexturedModalRect(x, y, du, dv, width, height);
        }
    }
}
