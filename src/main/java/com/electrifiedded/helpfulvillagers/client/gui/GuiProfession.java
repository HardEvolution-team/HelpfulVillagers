package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/** Job picker. A job is available once its guild hall exists in the village. */
public class GuiProfession extends GuiVillagerScreen {

    public GuiProfession(EntityHelpfulVillager v, NBTTagCompound data) {
        super(v, data, 230, 130);
    }

    @Override
    public void initGui() {
        super.initGui();
        int mask = data.getInteger("Unlocked");
        Profession[] all = Profession.values();
        for (int i = 0; i < all.length; i++) {
            int x, y;
            if (i < 4) { x = left + 10; y = top + 5 + i * 25; }
            else if (i < 8) { x = left + 120; y = top + 5 + (i - 4) * 25; }
            else { x = left + 65; y = top + 105; }
            GuiButton b = new GuiButton(i, x, y, 100, 20, I18n.format(all[i].translationKey()));
            b.enabled = (mask & (1 << i)) != 0 && all[i] != villager.getJob();
            buttonList.add(b);
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        Net.action(villager, MsgAction.SET_PROFESSION, b.id);
        mc.player.closeScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel(left, top, xSize, ySize);
        drawCenteredString(fontRenderer, I18n.format("helpfulvillagers.gui.choose_profession"), width / 2, top - 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        int mask = data.getInteger("Unlocked");
        for (GuiButton b : buttonList) {
            if (b.isMouseOver() && (mask & (1 << b.id)) == 0) {
                List<String> tip = new ArrayList<>();
                tip.add(I18n.format("helpfulvillagers.gui.locked"));
                tip.add(I18n.format("helpfulvillagers.gui.locked_hint." + Profession.byId(b.id).name));
                drawHoveringText(tip, mouseX, mouseY);
            }
        }
    }
}
