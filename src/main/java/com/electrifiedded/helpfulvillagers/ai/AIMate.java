package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.Messages;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.village.Village;

/**
 * Two adults who are not busy find each other and have a child (sometimes twins or triplets, much more
 * likely when there are beds nearby). Like vanilla, the village needs enough houses (doors) for it.
 */
public class AIMate extends EntityAIBase {

    private final EntityHelpfulVillager v;
    private EntityHelpfulVillager mate;
    private int timer;

    public AIMate(EntityHelpfulVillager v) {
        this.v = v;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (v.getGrowingAge() != 0 || v.isChild() || v.hasLeader() || v.getRNG().nextInt(500) != 0) return false;
        Village village = v.world.getVillageCollection().getNearestVillage(new BlockPos(v), 0);
        if (village == null) return false;
        int pop = v.world.getEntitiesWithinAABB(EntityVillager.class, new net.minecraft.util.math.AxisAlignedBB(village.getCenter())
                .grow(village.getVillageRadius())).size();
        if (pop >= (int) (village.getNumVillageDoors() * 0.35D) + 1) return false;
        mate = null;
        double bd = Double.MAX_VALUE;
        for (EntityHelpfulVillager o : v.world.getEntitiesWithinAABB(EntityHelpfulVillager.class, v.getEntityBoundingBox().grow(8, 3, 8))) {
            if (o == v || o.isChild() || o.getGrowingAge() != 0 || o.hasLeader()) continue;
            double d = v.getDistanceSq(o);
            if (d < bd) { bd = d; mate = o; }
        }
        return mate != null;
    }

    @Override
    public void startExecuting() {
        timer = 300;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return timer >= 0 && mate != null && mate.isEntityAlive() && mate.getGrowingAge() == 0 && v.getGrowingAge() == 0;
    }

    @Override
    public void resetTask() {
        mate = null;
    }

    @Override
    public void updateTask() {
        timer--;
        v.getLookHelper().setLookPositionWithEntity(mate, 10F, 30F);
        if (v.getDistanceSq(mate) > 2.25) v.getNavigator().tryMoveToEntityLiving(mate, 0.6D);
        else if (timer == 0) giveBirth();
        if (v.getRNG().nextInt(35) == 0) v.world.setEntityState(v, (byte) 12);
    }

    private boolean bedNearby() {
        BlockPos p = new BlockPos(v);
        for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-6, -2, -6), p.add(6, 2, 6))) {
            if (v.world.getBlockState(q).getBlock() instanceof BlockBed) return true;
        }
        return false;
    }

    private void giveBirth() {
        v.setGrowingAge(6000);
        mate.setGrowingAge(6000);
        int roll = v.getRNG().nextInt(bedNearby() ? 100 : 1000);
        int children = roll <= 10 ? 3 : roll <= 20 ? 2 : 1;
        for (int i = 0; i < children; i++) {
            EntityVillager child = v.createChild(mate);
            child.setGrowingAge(-24000);
            child.setLocationAndAngles(v.posX, v.posY, v.posZ, 0F, 0F);
            v.world.spawnEntity(child);
            v.world.setEntityState(child, (byte) 12);
        }
        String key = children == 1 ? "helpfulvillagers.msg.birth" : children == 2 ? "helpfulvillagers.msg.twins" : "helpfulvillagers.msg.triplets";
        Messages.broadcast(v.world, v.getPosition(), Messages.Kind.BIRTH, new TextComponentTranslation(key, v.getDisplayName(), mate.getDisplayName()));
    }
}
