package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;

/** Plain villagers have no work: they wander, go indoors at night and raise families. */
public class IdleJob extends Job {

    IdleJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    public boolean shouldRun() {
        return false;
    }

    @Override
    public void tick() {}
}
