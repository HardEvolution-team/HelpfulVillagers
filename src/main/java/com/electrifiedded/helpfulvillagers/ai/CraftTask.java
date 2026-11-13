package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.craft.CraftOrder;
import com.electrifiedded.helpfulvillagers.craft.CraftPlan;
import com.electrifiedded.helpfulvillagers.craft.CraftPlanner;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.InventoryUtil;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Carries out one craft order: collect the planned materials from the hall chests, smelt in a hall
 * furnace, craft at a hall workbench, then deliver the result to the right hall.
 */
public class CraftTask {

    private enum Stage { COLLECT, STEPS, DELIVER }

    private static final int CRAFT_TICKS = 20;

    private final EntityHelpfulVillager v;
    private final World world;
    public final CraftOrder order;
    private final List<CraftPlan.Material> toCollect;
    private final List<CraftPlan.Step> steps;
    /** what the villager carries for this order */
    public final List<ItemStack> bag = new ArrayList<>();
    private Stage stage = Stage.COLLECT;
    private BlockPos target;
    private int timer;
    private int smeltWanted;
    private int smeltGot;
    private int moveTimeout;

    private CraftTask(EntityHelpfulVillager v, CraftOrder order, CraftPlan plan) {
        this.v = v;
        this.world = v.world;
        this.order = order;
        this.toCollect = new ArrayList<>(plan.materials);
        this.steps = new ArrayList<>(plan.steps);
    }

    /** Picks the first order this villager can make with what the village has in store. */
    public static CraftTask tryStart(EntityHelpfulVillager v, boolean includeVillagerOrders) {
        VillageRecord village = v.village();
        if (village == null || v.hall() == null || village.queue.isEmpty()) return null;
        if (!v.getJob().canCraft() && v.taught.isEmpty()) return null;
        int tried = 0;
        List<ItemStack> stock = null;
        for (CraftOrder o : village.queue) {
            if (!o.byPlayer() && !includeVillagerOrders) continue;
            if (!v.canCraft(o.item)) continue;
            if (++tried > 4) break;
            if (stock == null) stock = village.stockSnapshot(v.world);
            CraftPlan plan = CraftPlanner.plan(o.item, o.item.getCount(), stock);
            if (!plan.ok()) {
                o.missing = plan.missing;
                continue;
            }
            if (needsWorkbench(plan) && findStation(village, v, true) == null) continue;
            if (needsFurnace(plan) && findStation(village, v, false) == null) continue;
            o.missing = new ArrayList<>();
            village.queue.remove(o);
            village.active.add(o);
            return new CraftTask(v, o, plan);
        }
        return null;
    }

    private static boolean needsWorkbench(CraftPlan plan) {
        for (CraftPlan.Step s : plan.steps) if (!s.isSmelting()) return true;
        return false;
    }

    private static boolean needsFurnace(CraftPlan plan) {
        for (CraftPlan.Step s : plan.steps) if (s.isSmelting()) return true;
        return false;
    }

    /** Nearest workbench (or furnace) of the village, own hall first. */
    private static BlockPos findStation(VillageRecord village, EntityHelpfulVillager v, boolean bench) {
        GuildHall own = v.hall();
        if (own != null) {
            List<BlockPos> l = bench ? own.workbenches : own.furnaces;
            if (!l.isEmpty()) return l.get(0);
        }
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (GuildHall h : village.halls()) {
            for (BlockPos p : bench ? h.workbenches : h.furnaces) {
                double d = p.distanceSq(v.getPosition());
                if (d < bd) { bd = d; best = p; }
            }
        }
        return best;
    }

    /** Remaining materials still to fetch (for the craft status screen). */
    public List<ItemStack> stillNeeded() {
        List<ItemStack> out = new ArrayList<>();
        for (CraftPlan.Material m : toCollect) {
            ItemStack s = m.display.copy();
            s.setCount(m.amount);
            out.add(s);
        }
        return out;
    }

    // --- running --------------------------------------------------------------------------------------

    /** @return false when finished or given up */
    public boolean tick() {
        VillageRecord village = v.village();
        v.job().status = "craft:" + stage + " steps=" + steps.size() + " collect=" + toCollect.size() + " target=" + target;
        if (village == null) { abort(); return false; }
        switch (stage) {
            case COLLECT: return collect(village);
            case STEPS: return runSteps(village);
            default: return deliver(village);
        }
    }

    private boolean walk(BlockPos p) {
        if (v.isNear(p, 2.5)) {
            v.getNavigator().clearPath();
            moveTimeout = 0;
            return true;
        }
        if (v.getNavigator().noPath() || v.ticksExisted % 20 == 0) v.moveTo(v.standSpot(p), 0.6D);
        if (++moveTimeout > 20 * 60) {
            // could not reach it in a minute: pretend we got there rather than looping forever
            moveTimeout = 0;
            return true;
        }
        return false;
    }

    private boolean collect(VillageRecord village) {
        if (toCollect.isEmpty()) {
            stage = Stage.STEPS;
            target = null;
            return true;
        }
        CraftPlan.Material m = toCollect.get(0);
        if (target == null) {
            target = village.findInChests(world, m.filter, v.hall());
            if (target == null) { abort(); return false; }
        }
        if (!walk(target)) return true;
        TileEntity te = world.getTileEntity(target);
        target = null;
        if (!(te instanceof IInventory)) return true;
        ItemStack got = InventoryUtil.extract((IInventory) te, m.filter, m.amount);
        while (!got.isEmpty()) {
            m.amount -= got.getCount();
            bag.add(got);
            if (m.amount <= 0) break;
            got = InventoryUtil.extract((IInventory) te, m.filter, m.amount);
        }
        v.swing();
        if (m.amount <= 0) toCollect.remove(0);
        return true;
    }

    private boolean runSteps(VillageRecord village) {
        if (steps.isEmpty()) {
            stage = Stage.DELIVER;
            target = null;
            return true;
        }
        CraftPlan.Step step = steps.get(0);
        if (target == null) {
            target = findStation(village, v, !step.isSmelting());
            if (target == null) { abort(); return false; }
            timer = 0;
            smeltWanted = -1;
        }
        if (!walk(target)) return true;
        v.getLookHelper().setLookPosition(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 30F, 30F);
        if (step.isSmelting()) return smelt(village, step);
        if (timer++ % 5 == 0) v.swing();
        if (timer < CRAFT_TICKS) return true;
        if (!craft(step)) { abort(); return false; }
        steps.remove(0);
        target = null;
        return true;
    }

    private boolean craft(CraftPlan.Step step) {
        for (Ingredient ing : step.ingredients()) {
            List<ItemStack> used = takeFromBag(InventoryUtil.matching(ing), step.times);
            if (used == null) return false;
            for (ItemStack u : used) {
                ItemStack container = ForgeHooks.getContainerItem(u);
                if (!container.isEmpty()) {
                    container.setCount(u.getCount());
                    bag.add(container);
                }
            }
        }
        bag.add(step.output());
        return true;
    }

    /** Removes n matching items from the bag; null (and nothing removed) if there are not enough. */
    private List<ItemStack> takeFromBag(Predicate<ItemStack> filter, int n) {
        int have = 0;
        for (ItemStack s : bag) if (filter.test(s)) have += s.getCount();
        if (have < n) return null;
        List<ItemStack> used = new ArrayList<>();
        for (ItemStack s : bag) {
            if (n <= 0) break;
            if (s.isEmpty() || !filter.test(s)) continue;
            int t = Math.min(n, s.getCount());
            used.add(s.splitStack(t));
            n -= t;
        }
        bag.removeIf(ItemStack::isEmpty);
        return used;
    }

    private boolean smelt(VillageRecord village, CraftPlan.Step step) {
        TileEntity te = world.getTileEntity(target);
        if (!(te instanceof TileEntityFurnace)) { abort(); return false; }
        TileEntityFurnace furnace = (TileEntityFurnace) te;
        ItemStack expected = step.output();
        if (smeltWanted < 0) {
            // load the furnace once: input and enough fuel for the whole batch
            ItemStack in = furnace.getStackInSlot(0);
            ItemStack out = furnace.getStackInSlot(2);
            if ((!in.isEmpty() && !InventoryUtil.same(in, step.smeltInput)) || (!out.isEmpty() && !InventoryUtil.same(out, expected))) {
                if (timer++ > 20 * 30) { abort(); return false; }
                return true; // someone else is using it; wait a bit
            }
            List<ItemStack> inputs = takeFromBag(InventoryUtil.matching(step.smeltInput), step.times);
            if (inputs == null) { abort(); return false; }
            if (!addFuel(village, furnace, step.times * 200)) {
                for (ItemStack i : inputs) bag.add(i);
                abort();
                return false;
            }
            ItemStack load = inputs.get(0).copy();
            load.setCount(0);
            for (ItemStack i : inputs) load.grow(i.getCount());
            if (in.isEmpty()) furnace.setInventorySlotContents(0, load);
            else in.grow(load.getCount());
            smeltWanted = expected.getCount();
            smeltGot = 0;
            timer = 0;
            v.swing();
            return true;
        }
        if (++timer % 20 == 0) {
            ItemStack out = furnace.getStackInSlot(2);
            if (!out.isEmpty() && InventoryUtil.same(out, expected)) {
                int take = Math.min(out.getCount(), smeltWanted - smeltGot);
                bag.add(furnace.decrStackSize(2, take));
                smeltGot += take;
            }
        }
        if (smeltGot >= smeltWanted) {
            steps.remove(0);
            target = null;
            return true;
        }
        if (timer > step.times * 200 + 20 * 40) { abort(); return false; }
        return true;
    }

    /** Fuel comes from any hall chest; returns false if the village has none. */
    private boolean addFuel(VillageRecord village, TileEntityFurnace furnace, int burnTicks) {
        int have = furnace.getField(0);
        ItemStack slot = furnace.getStackInSlot(1);
        if (!slot.isEmpty()) have += TileEntityFurnace.getItemBurnTime(slot) * slot.getCount();
        if (have >= burnTicks) return true;
        for (IInventory chest : village.allChests(world)) {
            for (int i = 0; i < chest.getSizeInventory() && have < burnTicks; i++) {
                ItemStack s = chest.getStackInSlot(i);
                int per = TileEntityFurnace.getItemBurnTime(s);
                if (s.isEmpty() || per <= 0 || s.getItem().hasContainerItem(s)) continue;
                if (!slot.isEmpty() && !InventoryUtil.canMerge(slot, s)) continue;
                int need = Math.min(s.getCount(), (burnTicks - have + per - 1) / per);
                need = Math.min(need, s.getMaxStackSize() - (slot.isEmpty() ? 0 : slot.getCount()));
                if (need <= 0) continue;
                ItemStack fuel = chest.decrStackSize(i, need);
                if (slot.isEmpty()) {
                    furnace.setInventorySlotContents(1, fuel);
                    slot = furnace.getStackInSlot(1);
                } else {
                    slot.grow(fuel.getCount());
                }
                have += per * fuel.getCount();
            }
            if (have >= burnTicks) return true;
        }
        return have >= burnTicks;
    }

    private boolean deliver(VillageRecord village) {
        GuildHall dest = order.byPlayer() || order.forProfession == null ? v.hall()
                : village.nearestHall(order.forProfession, v.getPosition());
        if (dest == null) dest = v.hall();
        if (dest == null) { abort(); return false; }
        if (target == null) {
            target = dest.chestWithRoom(world);
            if (target == null) target = dest.chests.isEmpty() ? dest.entrance : dest.chests.get(0);
        }
        if (!walk(target)) return true;
        ItemStack result = ItemStack.EMPTY;
        List<ItemStack> made = takeFromBag(InventoryUtil.matching(order.item), order.item.getCount());
        if (made != null) {
            for (ItemStack m : made) {
                ItemStack rest = dest.store(world, m);
                if (!rest.isEmpty()) v.inventory.addOrDrop(rest);
            }
            result = order.item.copy();
        }
        for (ItemStack s : bag) v.inventory.addOrDrop(s);
        bag.clear();
        village.active.remove(order);
        v.swing();
        if (order.byPlayer() && !result.isEmpty()) {
            EntityPlayer p = world.getPlayerEntityByUUID(order.player);
            if (p != null) {
                p.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.order_ready", result.getCount(),
                        result.getTextComponent(), v.getDisplayName(), dest.door.getX(), dest.door.getY(), dest.door.getZ()));
            }
        }
        return false;
    }

    /** Gives everything back and puts the order back at the front of the queue. */
    public void abort() {
        for (ItemStack s : bag) if (!s.isEmpty()) v.inventory.addOrDrop(s);
        bag.clear();
        VillageRecord village = v.village();
        if (village != null) village.active.remove(order);
        if (village != null && !village.queue.contains(order)) village.queue.add(0, order);
        if (v.craftTask == this) v.craftTask = null;
    }

    // --- persistence: a half-done order is simply restarted after a reload ----------------------------

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("Order", order.write());
        NBTTagList b = new NBTTagList();
        for (ItemStack s : bag) if (!s.isEmpty()) b.appendTag(s.writeToNBT(new NBTTagCompound()));
        t.setTag("Bag", b);
        return t;
    }

    public static CraftTask restore(EntityHelpfulVillager v, NBTTagCompound t) {
        NBTTagList b = t.getTagList("Bag", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < b.tagCount(); i++) v.inventory.addOrDrop(new ItemStack(b.getCompoundTagAt(i)));
        CraftOrder o = CraftOrder.read(t.getCompoundTag("Order"));
        if (o != null && v.village() != null) v.village().queue.add(0, o);
        return null;
    }
}
