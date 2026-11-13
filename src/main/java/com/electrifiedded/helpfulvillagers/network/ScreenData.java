package com.electrifiedded.helpfulvillagers.network;

import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.craft.CraftOrder;
import com.electrifiedded.helpfulvillagers.econ.Economy;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** Builds (server) and reads (client) the NBT payloads of {@link MsgScreen}. */
public final class ScreenData {

    private ScreenData() {}

    public static NBTTagList stacks(List<ItemStack> list) {
        NBTTagList l = new NBTTagList();
        for (ItemStack s : list) {
            NBTTagCompound t = s.writeToNBT(new NBTTagCompound());
            t.setInteger("RealCount", s.getCount());
            l.appendTag(t);
        }
        return l;
    }

    public static List<ItemStack> stacks(NBTTagList l) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            ItemStack s = new ItemStack(t);
            if (s.isEmpty()) continue;
            s.setCount(t.getInteger("RealCount"));
            out.add(s);
        }
        return out;
    }

    public static List<ItemStack> stacks(NBTTagCompound t, String key) {
        return stacks(t.getTagList(key, Constants.NBT.TAG_COMPOUND));
    }

    private static int unlockedMask(EntityHelpfulVillager v) {
        VillageRecord village = v.village();
        int mask = 1;
        if (village != null) for (Profession p : village.unlocked()) mask |= 1 << p.ordinal();
        return mask;
    }

    public static NBTTagCompound dialog(EntityHelpfulVillager v) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Unlocked", unlockedMask(v));
        t.setBoolean("Village", v.village() != null);
        t.setBoolean("Crafter", v.getJob().canCraft() || !v.taught.isEmpty());
        return t;
    }

    public static NBTTagCompound craftMenu(EntityHelpfulVillager v) {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("Craftables", stacks(v.knownCraftables()));
        if (v.craftTask != null) t.setTag("Current", v.craftTask.order.item.writeToNBT(new NBTTagCompound()));
        return t;
    }

    public static NBTTagCompound stats(EntityHelpfulVillager v, EntityPlayer viewer) {
        NBTTagCompound t = new NBTTagCompound();
        VillageRecord village = v.village();
        NBTTagList queue = new NBTTagList();
        if (village != null) {
            for (CraftOrder o : village.queue) {
                NBTTagCompound e = new NBTTagCompound();
                ItemStack it = o.item.copy();
                List<ItemStack> one = new ArrayList<>();
                one.add(it);
                e.setTag("Item", stacks(one));
                e.setString("Name", o.requesterName);
                e.setBoolean("Player", o.byPlayer());
                e.setBoolean("Mine", o.player != null && o.player.equals(viewer.getUniqueID()));
                e.setTag("Missing", stacks(o.missing));
                queue.appendTag(e);
            }
        }
        t.setTag("Queue", queue);
        if (v.craftTask != null) {
            List<ItemStack> one = new ArrayList<>();
            one.add(v.craftTask.order.item.copy());
            t.setTag("Current", stacks(one));
            t.setTag("Needed", stacks(v.craftTask.stillNeeded()));
            t.setTag("Collected", stacks(v.craftTask.bag));
        }
        return t;
    }

    public static NBTTagCompound barter(EntityHelpfulVillager v, EntityPlayer viewer) {
        NBTTagCompound t = new NBTTagCompound();
        VillageRecord village = v.village();
        t.setInteger("EmeraldValue", ModConfig.emeraldValue);
        if (village == null) return t;
        t.setInteger("Credits", village.account(viewer.getUniqueID()));
        Economy eco = village.economy;
        t.setBoolean("Ready", eco.isReady());
        if (eco.isReady()) {
            NBTTagList prices = new NBTTagList();
            for (ItemStack s : eco.catalog()) {
                int p = eco.price(s);
                if (p <= 0) continue;
                NBTTagCompound e = s.writeToNBT(new NBTTagCompound());
                e.setInteger("Price", p);
                prices.appendTag(e);
            }
            t.setTag("Prices", prices);
        }
        return t;
    }

    /** Lighter refresh for the barter screens after a trade (credits + the traded item's new price). */
    public static NBTTagCompound account(EntityHelpfulVillager v, EntityPlayer viewer, ItemStack traded) {
        NBTTagCompound t = new NBTTagCompound();
        VillageRecord village = v.village();
        t.setInteger("Credits", village == null ? 0 : village.account(viewer.getUniqueID()));
        if (village != null && !traded.isEmpty()) {
            NBTTagCompound e = traded.writeToNBT(new NBTTagCompound());
            e.setInteger("Price", village.economy.price(traded));
            t.setTag("Update", e);
        }
        return t;
    }
}
