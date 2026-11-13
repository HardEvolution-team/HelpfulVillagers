package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Guards pick hostile mobs: whatever attacks the one they protect, the village's last attacker, and
 * any monster inside the village (or near the protected one when escorting).
 */
public class AIGuardTarget extends EntityAITarget {

    private final EntityHelpfulVillager guard;
    private EntityLivingBase candidate;

    public AIGuardTarget(EntityHelpfulVillager guard) {
        super(guard, false, false);
        this.guard = guard;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!guard.getJob().isGuard() || guard.isChild() || guard.ticksExisted % 10 != 0) return false;
        if (!guard.hasTool() && !guard.getJob().isGuard()) return false;
        candidate = pick();
        return candidate != null;
    }

    private EntityLivingBase pick() {
        Entity leader = guard.getLeader();
        if (leader instanceof EntityLivingBase) {
            EntityLivingBase l = (EntityLivingBase) leader;
            if (hostile(l.getRevengeTarget())) return l.getRevengeTarget();
            if (l instanceof EntityLiving && hostile(((EntityLiving) l).getAttackTarget())) return ((EntityLiving) l).getAttackTarget();
            return nearestMob(l.getEntityBoundingBox().grow(12, 6, 12));
        }
        VillageRecord village = guard.village();
        if (village == null) return nearestMob(guard.getEntityBoundingBox().grow(12, 6, 12));
        if (hostile(village.lastAggressor)) return village.lastAggressor;
        return nearestMob(village.bounds());
    }

    private boolean hostile(EntityLivingBase e) {
        return e != null && e.isEntityAlive() && e instanceof IMob;
    }

    private EntityLivingBase nearestMob(AxisAlignedBB box) {
        EntityLivingBase best = null;
        double bd = getTargetDistance() * getTargetDistance();
        for (EntityLiving e : guard.world.getEntitiesWithinAABB(EntityLiving.class, box, e -> e instanceof IMob && e.isEntityAlive())) {
            if (e instanceof EntityCreeper && guard.getJob() == com.electrifiedded.helpfulvillagers.entity.Profession.SOLDIER
                    && guard.getHealth() < 10) continue; // a weak soldier lets creepers be
            double d = guard.getDistanceSq(e);
            if (d < bd && guard.getEntitySenses().canSee(e)) { bd = d; best = e; }
        }
        return best;
    }

    @Override
    public void startExecuting() {
        guard.setAttackTarget(candidate);
        super.startExecuting();
    }

    @Override
    protected double getTargetDistance() {
        return 32.0D;
    }
}
