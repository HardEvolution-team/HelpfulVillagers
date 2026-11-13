package com.electrifiedded.helpfulvillagers.entity;

import net.minecraft.entity.player.EntityPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Guard Villager": after pressing the button on a soldier or archer, the next villager the player
 * right-clicks becomes the one that guard protects.
 */
public final class GuardAssignments {

    private static final Map<UUID, EntityHelpfulVillager> PENDING = new ConcurrentHashMap<>();

    private GuardAssignments() {}

    public static void begin(EntityPlayer player, EntityHelpfulVillager guard) {
        PENDING.put(player.getUniqueID(), guard);
    }

    public static EntityHelpfulVillager take(EntityPlayer player) {
        return PENDING.remove(player.getUniqueID());
    }

    public static boolean isPending(EntityPlayer player) {
        return PENDING.containsKey(player.getUniqueID());
    }
}
