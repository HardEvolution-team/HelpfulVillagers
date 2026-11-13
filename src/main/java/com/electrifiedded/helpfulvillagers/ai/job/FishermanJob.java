package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.EntityVillagerBobber;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTableList;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Walks to open water outside the village, casts, waits for a bite and reels in vanilla fishing loot. */
public class FishermanJob extends WorkerJob {

    private BlockPos shore;
    private BlockPos water;
    private BlockPos explore;
    private int searchDistance = 8;
    private int scanCooldown;
    private int wideScanCooldown;
    private EntityVillagerBobber bobber;
    private int waitTicks;
    private int biteTicks;
    private final Set<UUID> milkedSquids = new HashSet<>();

    FishermanJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    protected void work() {
        status = shore == null ? "look for water" : "fish at " + shore + (bobber != null ? " bobber" : "");
        if (shore == null) {
            findWater();
            return;
        }
        if (!goTo(shore.up(), 1.5)) {
            if (isStuck()) shore = null;
            return;
        }
        v.getLookHelper().setLookPosition(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5, 20F, 20F);
        fish();
        collectInk();
    }

    private void fish() {
        if (bobber == null || bobber.isDead) {
            bobber = null;
            if (--waitTicks > 0) return;
            bobber = new EntityVillagerBobber(world, v, water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5);
            world.spawnEntity(bobber);
            v.swing();
            int lure = EnchantmentHelper.getEnchantmentLevel(Enchantments.LURE, v.getHeldItemMainhand());
            waitTicks = Math.max(40, 100 + v.getRNG().nextInt(400) - lure * 100);
            biteTicks = 0;
            return;
        }
        if (!bobber.landed()) return;
        if (!bobber.hasBite()) {
            if (--waitTicks <= 0) {
                bobber.setBite(true);
                biteTicks = 10 + v.getRNG().nextInt(15);
            }
            return;
        }
        if (--biteTicks > 0) return;
        reelIn();
    }

    private void reelIn() {
        if (world instanceof WorldServer) {
            int luck = EnchantmentHelper.getEnchantmentLevel(Enchantments.LUCK_OF_THE_SEA, v.getHeldItemMainhand());
            LootContext ctx = new LootContext.Builder((WorldServer) world).withLuck(luck).build();
            for (ItemStack s : world.getLootTableManager().getLootTableFromLocation(LootTableList.GAMEPLAY_FISHING).generateLootForPools(v.getRNG(), ctx)) {
                v.inventory.addOrDrop(s);
            }
        }
        v.swing();
        v.damageTool(1);
        bobber.setDead();
        bobber = null;
        waitTicks = 20;
    }

    private void collectInk() {
        if (v.ticksExisted % 20 != 0) return;
        for (EntitySquid squid : world.getEntitiesWithinAABB(EntitySquid.class, v.getEntityBoundingBox().grow(8))) {
            if (milkedSquids.add(squid.getUniqueID())) v.inventory.addOrDrop(new ItemStack(Items.DYE, 1, EnumDyeColor.BLACK.getDyeDamage()));
        }
    }

    private void findWater() {
        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            boolean found = scan(v.getPosition(), 16, v.getPosition());
            if (!found && --wideScanCooldown <= 0 && v.village() != null) {
                wideScanCooldown = 10;
                found = scan(v.village().center, v.village().radius + 24, v.getPosition());
            }
            if (found) {
                explore = null;
                searchDistance = 8;
                return;
            }
        }
        if (explore == null) explore = exploreTarget(searchDistance);
        if (goTo(explore, 5.0) || isStuck()) {
            explore = null;
            searchDistance = Math.min(96, searchDistance + 10);
        }
    }

    private boolean isSurfaceWater(BlockPos p) {
        return world.getBlockState(p).getMaterial() == Material.WATER && world.isAirBlock(p.up());
    }

    private boolean scan(BlockPos center, int radius, BlockPos from) {
        double best = Double.MAX_VALUE;
        BlockPos bestShore = null, bestWater = null;
        for (BlockPos p : BlockPos.getAllInBoxMutable(center.add(-radius, -6, -radius), center.add(radius, 6, radius))) {
            if (!isSurfaceWater(p)) continue;
            if (v.village() != null && v.village().isInside(p.getX(), p.getY(), p.getZ())) continue;
            int count = 0;
            for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-2, 0, -2), p.add(2, 0, 2))) if (isSurfaceWater(q)) count++;
            if (count < 12) continue;
            for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
                for (int dist = 1; dist <= 3; dist++) {
                    BlockPos s = p.offset(f, dist);
                    if (world.getBlockState(s).getMaterial().isSolid() && world.isAirBlock(s.up()) && world.isAirBlock(s.up(2))) {
                        double d = s.distanceSq(from);
                        if (d < best) {
                            best = d;
                            bestShore = s.toImmutable();
                            bestWater = p.toImmutable();
                        }
                        break;
                    }
                }
            }
        }
        if (bestShore == null) return false;
        shore = bestShore;
        water = bestWater;
        return true;
    }

    @Override
    public void pause() {
        super.pause();
        if (bobber != null) bobber.setDead();
        bobber = null;
    }

    @Override
    public void onNewDay() {
        milkedSquids.clear();
        shore = null;
    }
}
