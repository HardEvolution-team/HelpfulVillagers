package com.electrifiedded.helpfulvillagers.network;

import com.electrifiedded.helpfulvillagers.HelpfulVillagers;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Two messages are enough: client actions on a villager, and screen data from the server. */
public final class Net {

    public static SimpleNetworkWrapper channel;

    private Net() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(HelpfulVillagers.MODID);
        channel.registerMessage(MsgAction.Handler.class, MsgAction.class, 0, Side.SERVER);
        channel.registerMessage(MsgScreen.Handler.class, MsgScreen.class, 1, Side.CLIENT);
    }

    public static void openDialog(EntityPlayerMP player, EntityHelpfulVillager v) {
        sendScreen(player, v, MsgScreen.DIALOG, ScreenData.dialog(v));
    }

    public static void sendScreen(EntityPlayerMP player, EntityHelpfulVillager v, int screen, NBTTagCompound data) {
        channel.sendTo(new MsgScreen(v.getEntityId(), screen, data), player);
    }

    public static void action(EntityHelpfulVillager v, int action) {
        channel.sendToServer(new MsgAction(v.getEntityId(), action, 0, "", net.minecraft.item.ItemStack.EMPTY));
    }

    public static void action(EntityHelpfulVillager v, int action, int arg) {
        channel.sendToServer(new MsgAction(v.getEntityId(), action, arg, "", net.minecraft.item.ItemStack.EMPTY));
    }

    public static void action(EntityHelpfulVillager v, int action, int arg, String text, net.minecraft.item.ItemStack stack) {
        channel.sendToServer(new MsgAction(v.getEntityId(), action, arg, text, stack));
    }
}
