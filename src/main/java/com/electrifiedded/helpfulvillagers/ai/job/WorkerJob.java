package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.ai.CraftTask;
import com.electrifiedded.helpfulvillagers.craft.CraftOrder;
import com.electrifiedded.helpfulvillagers.craft.RecipeIndex;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.VillagerInventory;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.InventoryUtil;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Common day of a working villager: take player craft orders first, make sure a tool is in hand,
 * gather during the day, carry the haul to the guild hall chests when full or at nightfall, and spend
 * the night in the hall working on the village craft queue.
 */
public abstract class WorkerJob extends Job {

    protected boolean storeRequested;
    private int craftCheck;
    private int toolCheck;
    private BlockPos fetchChest;
    private BlockPos restSpot;
    private int restTicks;

    protected WorkerJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    public boolean shouldRun() {
        return v.village() != null && v.hall() != null && !v.isChild();
    }

    /** Whether this job gathers right now (default: daytime). */
    protected boolean worksNow() {
        return world.isDaytime();
    }

    /** Called while the villager has to walk off for a craft order (miner: get out of the shaft). */
    protected void leaveWorksite() {}

    /** One tick of actual gathering. */
    protected abstract void work();

    /** How many of this stack the villager keeps when emptying its bag (seeds, saplings, floor blocks...). */
    protected int keep(ItemStack stack) {
        return 0;
    }

    @Override
    public void tick() {
        if (v.craftTask != null) {
            status = "craft";
            leaveWorksite();
            if (!v.craftTask.tick()) v.craftTask = null;
            return;
        }
        if (--craftCheck <= 0) {
            craftCheck = 20;
            CraftTask t = CraftTask.tryStart(v, !worksNow());
            if (t != null) {
                pause();
                v.craftTask = t;
                return;
            }
        }
        if (profession().needsTool() && !v.hasTool() && !v.equipToolFromInventory()) {
            status = "fetchTool";
            fetchTool();
            return;
        }
        if (!worksNow() || storeRequested || v.inventory.isMainFull()) {
            status = "store";
            storeAndRest();
            return;
        }
        work();
    }

    // --- storing --------------------------------------------------------------------------------------

    protected boolean hasSomethingToStore() {
        Map<String, Integer> kept = new HashMap<>();
        for (int i = 0; i < VillagerInventory.MAIN; i++) {
            ItemStack s = v.inventory.getStackInSlot(i);
            if (s.isEmpty()) continue;
            String k = RecipeIndex.key(s);
            int allowed = keep(s) - kept.getOrDefault(k, 0);
            if (s.getCount() > allowed) return true;
            kept.merge(k, s.getCount(), Integer::sum);
        }
        return false;
    }

    protected void storeAndRest() {
        GuildHall hall = v.hall();
        if (hall == null) return;
        if (hasSomethingToStore()) {
            BlockPos chest = hall.chestWithRoom(world);
            if (chest != null) {
                if (goTo(chest, 2.5)) {
                    dumpInto(chest);
                    v.swing();
                    storeRequested = false;
                }
                return;
            }
        } else {
            storeRequested = false;
        }
        rest(hall);
    }

    protected void rest(GuildHall hall) {
        if (restSpot == null || --restTicks <= 0) {
            restSpot = hall.randomInside(v.getRNG());
            restTicks = 300 + v.getRNG().nextInt(300);
        }
        goTo(restSpot, 1.5);
    }

    private void dumpInto(BlockPos chestPos) {
        TileEntity te = world.getTileEntity(chestPos);
        if (!(te instanceof IInventory)) return;
        IInventory chest = (IInventory) te;
        Map<String, Integer> kept = new HashMap<>();
        for (int i = 0; i < VillagerInventory.MAIN; i++) {
            ItemStack s = v.inventory.getStackInSlot(i);
            if (s.isEmpty()) continue;
            String k = RecipeIndex.key(s);
            int allowed = Math.max(0, keep(s) - kept.getOrDefault(k, 0));
            int keepHere = Math.min(allowed, s.getCount());
            kept.merge(k, keepHere, Integer::sum);
            if (keepHere >= s.getCount()) continue;
            ItemStack give = s.splitStack(s.getCount() - keepHere);
            ItemStack rest = InventoryUtil.insert(chest, give);
            if (!rest.isEmpty()) s.grow(rest.getCount());
        }
        // also stow the hall's other chests if this one filled up
        GuildHall hall = v.hall();
        if (hall != null && hasSomethingToStore()) {
            BlockPos other = hall.chestWithRoom(world);
            if (other != null && !other.equals(chestPos)) storeRequested = true;
        }
        v.inventory.markDirty();
    }

    // --- exploring ------------------------------------------------------------------------------------

    /** A random surface spot {@code beyond} blocks outside the village edge. */
    protected BlockPos exploreTarget(int beyond) {
        VillageRecord village = v.village();
        BlockPos c = village != null ? village.center : v.getPosition();
        int r = (village != null ? village.radius : 16) + beyond;
        double a = v.getRNG().nextDouble() * Math.PI * 2;
        int x = c.getX() + (int) Math.round(Math.cos(a) * r);
        int z = c.getZ() + (int) Math.round(Math.sin(a) * r);
        return world.getHeight(new BlockPos(x, 0, z));
    }

    protected boolean insideAnyHall(BlockPos p) {
        VillageRecord village = v.village();
        if (village == null) return false;
        for (GuildHall h : village.halls()) if (h.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) return true;
        return false;
    }

    // --- tools ----------------------------------------------------------------------------------------

    protected void fetchTool() {
        fetchSupply(profession()::isTool, 1, true);
    }

    /**
     * Gets an item from the village chests. When none is stored and {@code orderIfMissing}, the cheapest
     * tool of the job is put on the village craft queue.
     */
    protected boolean fetchSupply(Predicate<ItemStack> what, int amount, boolean orderIfMissing) {
        VillageRecord village = v.village();
        GuildHall hall = v.hall();
        if (village == null || hall == null) return false;
        if (fetchChest == null || --toolCheck <= 0) {
            toolCheck = 40;
            fetchChest = village.findInChests(world, what, hall);
            if (fetchChest == null && orderIfMissing) orderTool(village);
        }
        if (fetchChest == null) {
            rest(hall);
            return false;
        }
        if (!goTo(fetchChest, 2.5)) return false;
        TileEntity te = world.getTileEntity(fetchChest);
        fetchChest = null;
        if (te instanceof IInventory) {
            ItemStack got = InventoryUtil.extract((IInventory) te, what, amount);
            if (!got.isEmpty()) {
                v.swing();
                v.inventory.addOrDrop(got);
                v.equipToolFromInventory();
                return true;
            }
        }
        return false;
    }

    private void orderTool(VillageRecord village) {
        for (CraftOrder o : village.active) {
            if (!o.byPlayer() && o.forProfession == profession()) return;
        }
        CraftOrder existing = null;
        for (CraftOrder o : village.queue) {
            if (!o.byPlayer() && o.forProfession == profession() && profession().isTool(o.item)) existing = o;
        }
        // an order that can be made already (or has not been tried yet) stays as it is
        if (existing != null && existing.missing.isEmpty()) return;
        // cheapest tool the village can make from what is in store; otherwise just the cheapest
        java.util.List<net.minecraft.item.Item> byPrice = new java.util.ArrayList<>(profession().tools);
        byPrice.sort(java.util.Comparator.comparingInt(t -> {
            int p = village.economy.isReady() ? village.economy.price(new ItemStack(t)) : -1;
            return p < 0 ? 10_000 + profession().tools.indexOf(t) : p;
        }));
        if (byPrice.isEmpty()) return;
        java.util.List<ItemStack> stock = village.stockSnapshot(world);
        net.minecraft.item.Item pick = byPrice.get(0);
        for (net.minecraft.item.Item t : byPrice) {
            if (com.electrifiedded.helpfulvillagers.craft.CraftPlanner.plan(new ItemStack(t), 1, stock).ok()) { pick = t; break; }
        }
        if (existing != null) {
            if (existing.item.getItem() == pick) return;
            village.queue.remove(existing);
        }
        village.addOrder(new CraftOrder(new ItemStack(pick), v.getDisplayName().getUnformattedText(), null, profession()));
    }
}
