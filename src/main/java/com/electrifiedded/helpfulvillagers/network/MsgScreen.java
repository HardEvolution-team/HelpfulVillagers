package com.electrifiedded.helpfulvillagers.network;

import com.electrifiedded.helpfulvillagers.HelpfulVillagers;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server -> client: open (or refresh) one of the villager screens with the data it shows. */
public class MsgScreen implements IMessage {

    public static final int DIALOG = 0, PROFESSION = 1, NICKNAME = 2, CRAFT_MENU = 3, CRAFT_STATS = 4,
            BARTER_BUY = 5, BARTER_DATA = 6, TEACH_RESULT = 7;

    int entityId;
    int screen;
    NBTTagCompound data;

    public MsgScreen() {}

    public MsgScreen(int entityId, int screen, NBTTagCompound data) {
        this.entityId = entityId;
        this.screen = screen;
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        screen = buf.readByte();
        data = ByteBufUtils.readTag(buf);
        if (data == null) data = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(screen);
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<MsgScreen, IMessage> {
        @Override
        public IMessage onMessage(MsgScreen msg, MessageContext ctx) {
            HelpfulVillagers.proxy.scheduleClient(() -> HelpfulVillagers.proxy.handleScreen(msg.entityId, msg.screen, msg.data));
            return null;
        }
    }
}
