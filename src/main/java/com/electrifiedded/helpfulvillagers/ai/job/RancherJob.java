package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.InventoryUtil;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemShears;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.IShearable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a herd in the fenced pasture next to the ranch hall: leads wild animals home on a lead, and once
 * a day breeds, shears and culls the herd (food and shears come from the village chests).
 */
public class RancherJob extends WorkerJob {

    private EntityAnimal wanted;
    private BlockPos dropOff;
    private final Set<UUID> checkedToday = new HashSet<>();
    private EntityAnimal tending;
    private BlockPos explore;
    private int searchDistance = 8;
    private int scanCooldown;

    RancherJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    protected int keep(ItemStack stack) {
        return stack.getItem() instanceof ItemShears ? 1 : 0;
    }

    private AxisAlignedBB pasture() {
        GuildHall h = v.hall();
        if (h == null) return null;
        return h.pastureBounds != null ? h.pastureBounds : h.bounds;
    }

    private List<EntityAnimal> herd() {
        AxisAlignedBB box = pasture();
        return box == null ? java.util.Collections.emptyList() : world.getEntitiesWithinAABB(EntityAnimal.class, box, a -> a != null && a.isEntityAlive());
    }

    @Override
    protected void work() {
        if (wanted != null) {
            bringHome();
            return;
        }
        if (tending != null || nextToTend()) {
            tend();
            return;
        }
        if (herd().size() < ModConfig.maxHerdPerSpecies * 3) {
            findAnimal();
        } else if (v.hall() != null) {
            rest(v.hall());
        }
    }

    // --- herding --------------------------------------------------------------------------------------

    private void findAnimal() {
        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            AxisAlignedBB home = pasture();
            EntityAnimal best = null;
            double bd = Double.MAX_VALUE;
            for (EntityAnimal a : world.getEntitiesWithinAABB(EntityAnimal.class, v.getEntityBoundingBox().grow(20, 6, 20))) {
                if (!herdable(a) || (home != null && home.contains(a.getPositionVector()))) continue;
                double d = v.getDistanceSq(a);
                if (d < bd) { bd = d; best = a; }
            }
            if (best != null) {
                wanted = best;
                explore = null;
                searchDistance = 8;
                return;
            }
        }
        if (explore == null) explore = exploreTarget(searchDistance);
        if (goTo(explore, 5.0) || isStuck()) {
            explore = null;
            searchDistance = Math.min(80, searchDistance + 8);
        }
    }

    private static boolean herdable(EntityAnimal a) {
        return a.isEntityAlive() && !(a instanceof EntityTameable) && !(a instanceof AbstractHorse)
                && !a.getLeashed() && a.canBeLeashedTo(null) && !a.hasCustomName();
    }

    private void bringHome() {
        if (!wanted.isEntityAlive() || (wanted.getLeashed() && wanted.getLeashHolder() != v)) {
            wanted = null;
            return;
        }
        if (!wanted.getLeashed()) {
            if (!goTo(new BlockPos(wanted), 2.5)) {
                if (isStuck()) wanted = null;
                return;
            }
            wanted.setLeashHolder(v, true);
            v.swing();
            GuildHall h = v.hall();
            dropOff = h == null ? null : (!h.pasture.isEmpty() ? h.pasture.get(v.getRNG().nextInt(h.pasture.size())) : h.randomInside(v.getRNG()));
            return;
        }
        if (dropOff == null) { release(); return; }
        if (wanted.getDistanceSq(v) > 100) wanted.getNavigator().tryMoveToEntityLiving(v, 1.1);
        if (goTo(dropOff, 2.5) || isStuck()) {
            wanted.setPositionAndUpdate(dropOff.getX() + 0.5, dropOff.getY(), dropOff.getZ() + 0.5);
            release();
        }
    }

    private void release() {
        if (wanted.getLeashed()) wanted.clearLeashed(true, false);
        if (dropOff != null) wanted.setHomePosAndDistance(dropOff, 6);
        ((EntityLiving) wanted).enablePersistence();
        wanted = null;
        dropOff = null;
    }

    // --- daily care -----------------------------------------------------------------------------------

    private boolean nextToTend() {
        for (EntityAnimal a : herd()) {
            if (!a.isChild() && checkedToday.add(a.getUniqueID())) {
                tending = a;
                return true;
            }
        }
        return false;
    }

    private void tend() {
        EntityAnimal a = tending;
        if (a == null || !a.isEntityAlive()) { tending = null; return; }
        if (!goTo(new BlockPos(a), 2.5)) {
            if (isStuck()) tending = null;
            return;
        }
        v.getLookHelper().setLookPositionWithEntity(a, 30F, 30F);
        v.setPickupRadius(3);
        if (cull(a)) { tending = null; return; }
        breed(a);
        shear(a);
        v.swing();
        tending = null;
    }

    /** Keeps at most maxHerdPerSpecies adults of one kind; extra ones go to the butcher. */
    private boolean cull(EntityAnimal a) {
        int adults = 0;
        Map<Class<?>, Integer> counts = new HashMap<>();
        for (EntityAnimal o : herd()) if (!o.isChild()) counts.merge(o.getClass(), 1, Integer::sum);
        adults = counts.getOrDefault(a.getClass(), 0);
        if (adults <= ModConfig.maxHerdPerSpecies) return false;
        a.attackEntityFrom(DamageSource.causeMobDamage(v), Math.max(20F, a.getHealth() + 1));
        return true;
    }

    private void breed(EntityAnimal a) {
        if (a.getGrowingAge() != 0 || a.isInLove()) return;
        int slot = v.inventory.find(a::isBreedingItem);
        if (slot < 0) {
            ItemStack food = takeFromVillage(a::isBreedingItem, 16);
            if (food.isEmpty()) return;
            v.inventory.addOrDrop(food);
            slot = v.inventory.find(a::isBreedingItem);
            if (slot < 0) return;
        }
        v.inventory.decrStackSize(slot, 1);
        a.setInLove(null);
    }

    private void shear(EntityAnimal a) {
        if (!(a instanceof IShearable)) return;
        IShearable sheep = (IShearable) a;
        ItemStack probe = new ItemStack(Items.SHEARS);
        if (!sheep.isShearable(probe, world, new BlockPos(a))) return;
        int slot = v.inventory.find(s -> s.getItem() instanceof ItemShears);
        if (slot < 0) {
            ItemStack shears = takeFromVillage(s -> s.getItem() instanceof ItemShears, 1);
            if (shears.isEmpty()) return;
            v.inventory.addOrDrop(shears);
            slot = v.inventory.find(s -> s.getItem() instanceof ItemShears);
            if (slot < 0) return;
        }
        ItemStack shears = v.inventory.getStackInSlot(slot);
        for (ItemStack wool : sheep.onSheared(shears, world, new BlockPos(a), 0)) v.inventory.addOrDrop(wool);
        shears.damageItem(1, v);
        if (shears.isEmpty()) v.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
    }

    /** Pulls items out of the ranch chests first, then any hall in the village (no walking: a helper fetches it). */
    private ItemStack takeFromVillage(java.util.function.Predicate<ItemStack> what, int amount) {
        VillageRecord village = v.village();
        if (village == null) return ItemStack.EMPTY;
        BlockPos chest = village.findInChests(world, what, v.hall());
        if (chest == null) return ItemStack.EMPTY;
        TileEntity te = world.getTileEntity(chest);
        return te instanceof IInventory ? InventoryUtil.extract((IInventory) te, what, amount) : ItemStack.EMPTY;
    }

    @Override
    public void onNewDay() {
        checkedToday.clear();
    }

    @Override
    public void pause() {
        super.pause();
        v.setPickupRadius(1);
        if (wanted != null && wanted.getLeashed() && wanted.getLeashHolder() == v) wanted.clearLeashed(true, false);
        wanted = null;
        tending = null;
    }
}
