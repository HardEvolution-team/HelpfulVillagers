package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/** Follows the player (or villager) it was told to; catches up by teleporting like a tamed wolf. */
public class AIFollowLeader extends EntityAIBase {

    private final EntityHelpfulVillager v;
    private int repath;

    public AIFollowLeader(EntityHelpfulVillager v) {
        this.v = v;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        Entity l = v.getLeader();
        return l != null && l.isEntityAlive() && v.getAttackTarget() == null && v.getDistanceSq(l) > 9;
    }

    @Override
    public boolean shouldContinueExecuting() {
        Entity l = v.getLeader();
        return l != null && l.isEntityAlive() && v.getAttackTarget() == null && v.getDistanceSq(l) > 4 && !v.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        repath = 0;
    }

    @Override
    public void resetTask() {
        v.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        Entity l = v.getLeader();
        if (l == null) return;
        v.getLookHelper().setLookPositionWithEntity(l, 10F, v.getVerticalFaceSpeed());
        if (--repath > 0) return;
        repath = 10;
        if (v.getDistanceSq(l) > 24 * 24 && l instanceof EntityPlayer && l.onGround) {
            teleportNear(l);
            return;
        }
        double speed = v.getDistanceSq(l) > 100 ? 0.8D : 0.6D;
        v.getNavigator().tryMoveToEntityLiving(l, speed);
    }

    private void teleportNear(Entity l) {
        BlockPos base = new BlockPos(l);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                BlockPos p = base.add(dx, 0, dz);
                if (v.world.getBlockState(p.down()).isTopSolid() && v.world.isAirBlock(p) && v.world.isAirBlock(p.up())) {
                    v.setLocationAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, v.rotationYaw, v.rotationPitch);
                    v.getNavigator().clearPath();
                    return;
                }
            }
        }
    }
}
