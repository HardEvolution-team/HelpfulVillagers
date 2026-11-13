package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.item.ItemArrow;

/** Sword fighting for soldiers, and the fallback for archers who ran out of arrows. */
public class AIGuardMelee extends EntityAIBase {

    private final EntityHelpfulVillager guard;
    private int cooldown;
    private int repath;

    public AIGuardMelee(EntityHelpfulVillager guard) {
        this.guard = guard;
        setMutexBits(3);
    }

    private boolean meleeRole() {
        Profession p = guard.getJob();
        if (p == Profession.SOLDIER) return true;
        if (p != Profession.ARCHER) return false;
        boolean canShoot = guard.hasTool() && (ModConfig.infiniteArrows || guard.inventory.count(s -> s.getItem() instanceof ItemArrow) > 0);
        return !canShoot;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = guard.getAttackTarget();
        return t != null && t.isEntityAlive() && meleeRole() && !guard.isChild();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute() && guard.getDistanceSq(guard.getAttackTarget()) < 48 * 48;
    }

    @Override
    public void resetTask() {
        guard.getNavigator().clearPath();
        if (guard.getAttackTarget() != null && !guard.getAttackTarget().isEntityAlive()) guard.setAttackTarget(null);
    }

    @Override
    public void updateTask() {
        EntityLivingBase t = guard.getAttackTarget();
        if (t == null) return;
        guard.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        double reach = guard.width * 2F * guard.width * 2F + t.width + 1.5;
        double d = guard.getDistanceSq(t.posX, t.getEntityBoundingBox().minY, t.posZ);
        if (--repath <= 0) {
            repath = 4 + guard.getRNG().nextInt(6);
            guard.getNavigator().tryMoveToEntityLiving(t, 0.75D);
        }
        if (cooldown > 0) cooldown--;
        if (d <= reach && cooldown <= 0) {
            cooldown = 20;
            guard.attackEntityAsMob(t);
        }
    }
}
