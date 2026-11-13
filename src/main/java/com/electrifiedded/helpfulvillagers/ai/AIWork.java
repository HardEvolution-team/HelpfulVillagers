package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.entity.ai.EntityAIBase;

/** Runs the villager's current {@link com.electrifiedded.helpfulvillagers.ai.job.Job} while it has no leader. */
public class AIWork extends EntityAIBase {

    private final EntityHelpfulVillager v;

    public AIWork(EntityHelpfulVillager v) {
        this.v = v;
        setMutexBits(3);
    }

    private boolean can() {
        return !v.isChild() && !v.hasLeader() && v.getAttackTarget() == null && (v.craftTask != null || v.job().shouldRun());
    }

    @Override
    public boolean shouldExecute() {
        return can();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return can();
    }

    @Override
    public void updateTask() {
        v.job().tick();
    }

    @Override
    public void resetTask() {
        v.job().pause();
    }
}
