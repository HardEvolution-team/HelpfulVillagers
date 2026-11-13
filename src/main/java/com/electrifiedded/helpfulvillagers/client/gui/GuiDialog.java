package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import com.electrifiedded.helpfulvillagers.network.MsgAction;
import com.electrifiedded.helpfulvillagers.network.Net;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;

/** Right-click menu of a villager. */
public class GuiDialog extends GuiVillagerScreen {

    private static final int FOLLOW = 0, STOP = 1, INVENTORY = 2, PROFESSION = 3, NICKNAME = 4, GUARD = 5, CRAFTING = 6, BARTER = 8;

    public GuiDialog(EntityHelpfulVillager v, NBTTagCompound data) {
        super(v, data, 120, 135);
    }

    @Override
    public void initGui() {
        Profession job = villager.getJob();
        boolean extra = job.isGuard() || job == Profession.MERCHANT || data.getBoolean("Crafter");
        ySize = extra ? 135 : 110;
        super.initGui();
        int x = left + 10, y = top + 5;
        boolean following = villager.getLeader() == mc.player;
        buttonList.add(new GuiButton(following ? STOP : FOLLOW, x, y, 100, 20,
                I18n.format(following ? "helpfulvillagers.gui.stop_following" : "helpfulvillagers.gui.follow")));
        buttonList.add(new GuiButton(INVENTORY, x, y + 25, 100, 20, I18n.format("helpfulvillagers.gui.inventory")));
        buttonList.add(new GuiButton(PROFESSION, x, y + 50, 100, 20, I18n.format("helpfulvillagers.gui.change_profession")));
        buttonList.add(new GuiButton(NICKNAME, x, y + 75, 100, 20, I18n.format("helpfulvillagers.gui.nickname")));
        if (job.isGuard()) buttonList.add(new GuiButton(GUARD, x, y + 100, 100, 20, I18n.format("helpfulvillagers.gui.guard")));
        else if (job == Profession.MERCHANT) buttonList.add(new GuiButton(BARTER, x, y + 100, 100, 20, I18n.format("helpfulvillagers.gui.barter")));
        else if (data.getBoolean("Crafter")) buttonList.add(new GuiButton(CRAFTING, x, y + 100, 100, 20, I18n.format("helpfulvillagers.gui.crafting")));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case FOLLOW: Net.action(villager, MsgAction.FOLLOW); mc.player.closeScreen(); break;
            case STOP: Net.action(villager, MsgAction.STOP_FOLLOW); mc.player.closeScreen(); break;
            case INVENTORY: Net.action(villager, MsgAction.INVENTORY); break;
            case PROFESSION: mc.displayGuiScreen(new GuiProfession(villager, data)); break;
            case NICKNAME: mc.displayGuiScreen(new GuiNickname(villager, data)); break;
            case GUARD: Net.action(villager, MsgAction.GUARD); mc.player.closeScreen(); break;
            case CRAFTING: Net.action(villager, MsgAction.CRAFT_MENU); break;
            case BARTER: Net.action(villager, MsgAction.BARTER_BUY); break;
            default: break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel(left, top, xSize, ySize);
        drawCenteredString(fontRenderer, villager.getDisplayName().getFormattedText(), width / 2, top - 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
