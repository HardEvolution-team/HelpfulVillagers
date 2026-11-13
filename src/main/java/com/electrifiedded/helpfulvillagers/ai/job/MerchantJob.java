package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import net.minecraft.util.math.BlockPos;

/** Merchants keep shop: they stay in their hall during the day so players can find them to barter. */
public class MerchantJob extends Job {

    private BlockPos spot;
    private int idle;

    MerchantJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    public boolean shouldRun() {
        return v.hall() != null && world.isDaytime();
    }

    @Override
    public void tick() {
        GuildHall hall = v.hall();
        if (hall == null) return;
        if (spot == null || --idle <= 0) {
            spot = hall.randomInside(v.getRNG());
            idle = 200 + v.getRNG().nextInt(400);
        }
        goTo(spot, 1.5);
    }
}
