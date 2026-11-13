package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Behaviour of one job. Driven every tick by {@link com.electrifiedded.helpfulvillagers.ai.AIWork}. */
public abstract class Job {

    protected final EntityHelpfulVillager v;
    protected final World world;
    /** what the job did last tick (debug) */
    public String status = "-";

    // movement bookkeeping
    private BlockPos moveTarget;
    private double bestDist;
    private int stuckTicks;
    private int repathTicks;

    // digging bookkeeping
    private BlockPos digPos;
    private int digTicks;
    private int digNeeded;

    protected Job(EntityHelpfulVillager v) {
        this.v = v;
        this.world = v.world;
    }

    public static Job create(EntityHelpfulVillager v, Profession p) {
        switch (p) {
            case LUMBERJACK: return new LumberjackJob(v);
            case FARMER: return new FarmerJob(v);
            case MINER: return new MinerJob(v);
            case FISHERMAN: return new FishermanJob(v);
            case RANCHER: return new RancherJob(v);
            case SOLDIER:
            case ARCHER: return new GuardJob(v);
            case MERCHANT: return new MerchantJob(v);
            default: return new IdleJob(v);
        }
    }

    public Profession profession() {
        return v.getJob();
    }

    public abstract boolean shouldRun();

    public abstract void tick();

    /** AI task interrupted (leader, combat, child...). */
    public void pause() {
        clearMove();
        cancelDig();
    }

    /** Job is being replaced or the villager died. */
    public void stop() {
        pause();
    }

    public void onNewDay() {}

    public NBTTagCompound write() {
        return new NBTTagCompound();
    }

    public void read(NBTTagCompound tag) {}

    // --- movement -------------------------------------------------------------------------------------

    /** Walks toward {@code pos}; true once within {@code reach} blocks. */
    protected boolean goTo(BlockPos pos, double reach) {
        if (v.isNear(pos, reach)) {
            clearMove();
            return true;
        }
        double d = v.getDistanceSqToCenter(pos);
        if (!pos.equals(moveTarget)) {
            moveTarget = pos;
            bestDist = d;
            stuckTicks = 0;
            repathTicks = 0;
        }
        if (d < bestDist - 0.5) {
            bestDist = d;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
        boolean noPath = v.getNavigator().noPath();
        if (noPath && repathTicks > 4) repathTicks = 4;
        if (--repathTicks <= 0) {
            boolean ok = v.moveTo(v.standSpot(pos), speed());
            // pathfinder gave up but the target is close: just walk straight at it
            if (!ok && d < 64) v.getMoveHelper().setMoveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed());
            repathTicks = 10;
        }
        // wedged on a ledge / block edge: hop and force a fresh path
        if (stuckTicks > 0 && stuckTicks % 25 == 0) {
            if (v.onGround) v.getJumpHelper().setJumping();
            repathTicks = 0;
        }
        return false;
    }

    /** True when the current goTo target has not been approached for a while. */
    protected boolean isStuck() {
        return stuckTicks > 100;
    }

    protected boolean stuckFor(int ticks) {
        return stuckTicks > ticks;
    }

    protected void clearMove() {
        moveTarget = null;
        stuckTicks = 0;
        v.getNavigator().clearPath();
    }

    protected double speed() {
        return 0.6D;
    }

    // --- digging --------------------------------------------------------------------------------------

    /** Mines {@code pos} over time like a player with the held tool. True when the block is gone. */
    protected boolean dig(BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock().isAir(state, world, pos)) {
            cancelDig();
            return true;
        }
        if (!pos.equals(digPos)) {
            cancelDig();
            digPos = pos;
            digTicks = 0;
            digNeeded = digTime(state, pos);
        }
        v.getLookHelper().setLookPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30F, 30F);
        if (digTicks % 5 == 0) v.swing();
        digTicks++;
        world.sendBlockBreakProgress(v.getEntityId(), pos, Math.min(9, digTicks * 10 / Math.max(1, digNeeded)));
        if (digTicks < digNeeded) return false;
        harvest(pos);
        cancelDig();
        return true;
    }

    protected void cancelDig() {
        if (digPos != null) world.sendBlockBreakProgress(v.getEntityId(), digPos, -1);
        digPos = null;
        digTicks = 0;
    }

    private int digTime(IBlockState state, BlockPos pos) {
        float hardness = state.getBlockHardness(world, pos);
        if (hardness < 0) return Integer.MAX_VALUE;
        ItemStack tool = v.getHeldItemMainhand();
        float speed = tool.isEmpty() ? 1.0F : Math.max(1.0F, tool.getDestroySpeed(state));
        int eff = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, tool);
        if (eff > 0 && speed > 1.0F) speed += eff * eff + 1;
        return Math.max(4, Math.min(200, Math.round(hardness * 30F / speed)));
    }

    /** Breaks a block and puts its drops straight into the villager's bag. */
    protected void harvest(BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.isAir(state, world, pos)) return;
        ItemStack tool = v.getHeldItemMainhand();
        int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool);
        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, state, fortune);
        world.playEvent(2001, pos, Block.getStateId(state));
        world.setBlockToAir(pos);
        for (ItemStack d : drops) v.inventory.addOrDrop(d);
        if (state.getBlockHardness(world, pos) > 0) v.damageTool(1);
    }
}
