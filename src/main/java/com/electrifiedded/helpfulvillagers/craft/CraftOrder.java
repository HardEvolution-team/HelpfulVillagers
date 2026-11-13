package com.electrifiedded.helpfulvillagers.craft;

import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** One entry of a village craft queue. Player orders always go before villager (tool) orders. */
public class CraftOrder {

    public final ItemStack item;
    public final String requesterName;
    /** player UUID for player orders, null for villager orders */
    public final UUID player;
    /** villager orders: the job whose hall receives the item */
    public final Profession forProfession;
    /** last planning result when the order could not be made yet (not saved) */
    public transient java.util.List<ItemStack> missing = new java.util.ArrayList<>();

    public CraftOrder(ItemStack item, String requesterName, UUID player, Profession forProfession) {
        this.item = item.copy();
        this.requesterName = requesterName;
        this.player = player;
        this.forProfession = forProfession;
    }

    public boolean byPlayer() {
        return player != null;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("Item", item.writeToNBT(new NBTTagCompound()));
        t.setString("Name", requesterName);
        if (player != null) t.setUniqueId("Player", player);
        t.setInteger("For", forProfession == null ? -1 : forProfession.ordinal());
        return t;
    }

    public static CraftOrder read(NBTTagCompound t) {
        ItemStack item = new ItemStack(t.getCompoundTag("Item"));
        if (item.isEmpty()) return null;
        UUID p = t.hasUniqueId("Player") ? t.getUniqueId("Player") : null;
        int f = t.getInteger("For");
        return new CraftOrder(item, t.getString("Name"), p, f < 0 ? null : Profession.byId(f));
    }
}
