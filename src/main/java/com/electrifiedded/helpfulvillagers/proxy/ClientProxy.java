package com.electrifiedded.helpfulvillagers.proxy;

import com.electrifiedded.helpfulvillagers.client.RenderHelpfulVillager;
import com.electrifiedded.helpfulvillagers.client.RenderVillagerBobber;
import com.electrifiedded.helpfulvillagers.client.gui.BarterState;
import com.electrifiedded.helpfulvillagers.client.gui.GuiBarterBuy;
import com.electrifiedded.helpfulvillagers.client.gui.GuiCraftMenu;
import com.electrifiedded.helpfulvillagers.client.gui.GuiCraftStats;
import com.electrifiedded.helpfulvillagers.client.gui.GuiDialog;
import com.electrifiedded.helpfulvillagers.client.gui.GuiNickname;
import com.electrifiedded.helpfulvillagers.client.gui.GuiProfession;
import com.electrifiedded.helpfulvillagers.client.gui.GuiTeach;
import com.electrifiedded.helpfulvillagers.client.gui.GuiVillagerScreen;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.EntityVillagerBobber;
import com.electrifiedded.helpfulvillagers.network.MsgScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHelpfulVillager.class, RenderHelpfulVillager::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityVillagerBobber.class, RenderVillagerBobber::new);
    }

    @Override
    public void scheduleClient(Runnable r) {
        Minecraft.getMinecraft().addScheduledTask(r);
    }

    @Override
    public void handleScreen(int entityId, int screen, NBTTagCompound data) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        Entity e = mc.world.getEntityByID(entityId);
        if (!(e instanceof EntityHelpfulVillager)) return;
        EntityHelpfulVillager v = (EntityHelpfulVillager) e;
        GuiScreen open = mc.currentScreen;
        switch (screen) {
            case MsgScreen.DIALOG: mc.displayGuiScreen(new GuiDialog(v, data)); break;
            case MsgScreen.PROFESSION: mc.displayGuiScreen(new GuiProfession(v, data)); break;
            case MsgScreen.NICKNAME: mc.displayGuiScreen(new GuiNickname(v, data)); break;
            case MsgScreen.CRAFT_MENU:
                if (open instanceof GuiCraftMenu) ((GuiVillagerScreen) open).refresh(data);
                else mc.displayGuiScreen(new GuiCraftMenu(v, data));
                break;
            case MsgScreen.CRAFT_STATS:
                if (open instanceof GuiCraftStats) ((GuiVillagerScreen) open).refresh(data);
                else mc.displayGuiScreen(new GuiCraftStats(v, data));
                break;
            case MsgScreen.BARTER_BUY: mc.displayGuiScreen(new GuiBarterBuy(v, data)); break;
            case MsgScreen.BARTER_DATA:
                if (open instanceof GuiBarterBuy) ((GuiBarterBuy) open).refresh(data);
                else BarterState.load(data);
                break;
            case MsgScreen.TEACH_RESULT:
                if (open instanceof GuiTeach) ((GuiTeach) open).showResult(data.getString("Key"));
                break;
            default: break;
        }
    }
}
