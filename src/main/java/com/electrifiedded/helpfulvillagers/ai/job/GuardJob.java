package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Soldiers and archers: keep a weapon, armour and (archers) arrows stocked from the hall, rest when
 * badly hurt, and otherwise patrol the village day and night. Fighting itself is done by the guard
 * combat tasks, which take over whenever there is a target.
 */
public class GuardJob extends WorkerJob {

    private BlockPos patrol;
    private int linger;
    private boolean armorChecked;

    GuardJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    protected boolean worksNow() {
        return true;
    }

    @Override
    protected int keep(ItemStack stack) {
        if (stack.getItem() instanceof ItemArrow) return v.getJob() == Profession.ARCHER ? 128 : 0;
        if (stack.getItem() instanceof ItemArmor) return 0;
        return 0;
    }

    @Override
    public void tick() {
        status = "guard";
        if (v.getAttackTarget() != null) return;
        if (v.getHealth() < v.getMaxHealth() * 0.4F && v.hall() != null) {
            rest(v.hall());
            return;
        }
        if (!armorChecked && v.hall() != null) {
            armorChecked = true;
            for (EntityEquipmentSlot slot : new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}) {
                final EntityEquipmentSlot s = slot;
                java.util.function.Predicate<ItemStack> piece = st -> st.getItem() instanceof ItemArmor && ((ItemArmor) st.getItem()).armorType == s;
                if (v.getItemStackFromSlot(slot).isEmpty() && v.village() != null && v.village().findInChests(world, piece, v.hall()) != null) {
                    if (fetchSupply(piece, 1, false)) {
                        v.inventory.equipBestArmor();
                    }
                    armorChecked = false;
                    return;
                }
            }
        }
        if (v.getJob() == Profession.ARCHER && !ModConfig.infiniteArrows && v.inventory.count(s -> s.getItem() instanceof ItemArrow) == 0
                && v.village() != null && v.village().findInChests(world, s -> s.getItem() instanceof ItemArrow, v.hall()) != null) {
            fetchSupply(s -> s.getItem() instanceof ItemArrow, 64, false);
            return;
        }
        super.tick();
    }

    @Override
    protected void work() {
        status = "patrol " + patrol;
        VillageRecord village = v.village();
        if (village == null) return;
        if (patrol == null || linger-- <= 0 && goTo(patrol, 3.0)) {
            patrol = pickPatrolPoint(village);
            linger = 60 + v.getRNG().nextInt(120);
            return;
        }
        if (goTo(patrol, 3.0)) v.getNavigator().clearPath();
        else if (isStuck()) patrol = null;
    }

    private BlockPos pickPatrolPoint(VillageRecord village) {
        if (!village.halls().isEmpty() && v.getRNG().nextInt(3) == 0) {
            GuildHall h = village.halls().get(v.getRNG().nextInt(village.halls().size()));
            return h.entrance;
        }
        int r = Math.max(8, village.radius - 8);
        int x = village.center.getX() + v.getRNG().nextInt(r * 2 + 1) - r;
        int z = village.center.getZ() + v.getRNG().nextInt(r * 2 + 1) - r;
        return world.getHeight(new BlockPos(x, 0, z));
    }

    @Override
    public void onNewDay() {
        armorChecked = false;
    }
}
