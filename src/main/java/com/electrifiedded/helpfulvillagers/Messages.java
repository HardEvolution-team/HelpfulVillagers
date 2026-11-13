package com.electrifiedded.helpfulvillagers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/** Birth/death announcements with a per-player setting (off / on / verbose) stored on the player. */
public final class Messages {

    public enum Kind {
        DEATH("hv_death"), BIRTH("hv_birth");
        final String key;

        Kind(String key) { this.key = key; }

        int defaultMode() {
            return this == DEATH ? ModConfig.defaultDeathMessages : ModConfig.defaultBirthMessages;
        }
    }

    public static final int OFF = 0, ON = 1, VERBOSE = 2;
    private static final double RANGE = 256;

    private Messages() {}

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static int mode(EntityPlayer p, Kind k) {
        NBTTagCompound t = persisted(p);
        return t.hasKey(k.key) ? t.getInteger(k.key) : k.defaultMode();
    }

    public static void setMode(EntityPlayer p, Kind k, int mode) {
        persisted(p).setInteger(k.key, mode);
    }

    public static void broadcast(World world, BlockPos where, Kind kind, ITextComponent text) {
        for (EntityPlayer p : world.playerEntities) {
            if (p.getDistanceSq(where) > RANGE * RANGE) continue;
            int mode = mode(p, kind);
            if (mode == OFF) continue;
            ITextComponent msg = text.createCopy();
            if (mode == VERBOSE) {
                msg.appendSibling(new TextComponentTranslation("helpfulvillagers.msg.at", where.getX(), where.getY(), where.getZ()));
            }
            msg.getStyle().setColor(kind == Kind.DEATH ? TextFormatting.RED : TextFormatting.GREEN);
            p.sendMessage(msg);
        }
    }
}
