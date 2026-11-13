package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiNickname extends GuiVillagerScreen {

    private GuiTextField field;

    public GuiNickname(EntityHelpfulVillager v, NBTTagCompound data) {
        super(v, data, 140, 60);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        field = new GuiTextField(0, fontRenderer, left + 10, top + 8, 120, 16);
        field.setMaxStringLength(32);
        field.setText(villager.hasCustomName() ? villager.getCustomNameTag() : "");
        field.setFocused(true);
        buttonList.add(new GuiButton(0, left + 10, top + 32, 120, 20, I18n.format("gui.done")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        Net.action(villager, MsgAction.NICKNAME, 0, field.getText(), ItemStack.EMPTY);
        mc.player.closeScreen();
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            actionPerformed(buttonList.get(0));
            return;
        }
        if (!field.textboxKeyTyped(c, key)) super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        field.mouseClicked(mx, my, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel(left, top, xSize, ySize);
        drawCenteredString(fontRenderer, I18n.format("helpfulvillagers.gui.nickname"), width / 2, top - 12, 0xFFFFFF);
        field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
