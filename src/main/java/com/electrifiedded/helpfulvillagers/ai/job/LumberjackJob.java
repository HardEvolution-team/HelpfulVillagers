package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds natural trees, fells the whole trunk and replants a matching sapling. */
public class LumberjackJob extends WorkerJob {

    private static final int SCAN_RADIUS = 20;
    private static final int MAX_LOGS = 256;

    private BlockPos treeBase;
    private List<BlockPos> logs;
    private BlockPlanks.EnumType woodType;
    private BlockPos explore;
    private int searchDistance = 10;
    private int scanCooldown;
    private int wideScanCooldown;

    LumberjackJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    protected int keep(ItemStack stack) {
        return Block.getBlockFromItem(stack.getItem()) instanceof BlockSapling ? 8 : 0;
    }

    @Override
    protected void work() {
        v.setPickupRadius(3);
        if (treeBase == null) {
            status = "explore " + explore + " d" + searchDistance;
            findTree();
            return;
        }
        status = "tree " + treeBase + (logs == null ? " approach" : " logs=" + logs.size());
        if (logs == null) {
            if (!goTo(treeBase, 3.0)) {
                if (isStuck()) treeBase = null;
                return;
            }
            logs = collectTree(treeBase);
            if (logs == null) { treeBase = null; return; }
        }
        while (!logs.isEmpty() && !isLog(world.getBlockState(logs.get(0)))) logs.remove(0);
        if (logs.isEmpty()) {
            replant();
            treeBase = null;
            logs = null;
            return;
        }
        if (!v.hasTool()) return;
        dig(logs.get(0));
    }

    private void findTree() {
        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            BlockPos found = scan(v.getPosition(), SCAN_RADIUS, v.getPosition());
            if (found == null && --wideScanCooldown <= 0 && v.village() != null) {
                // every ~10 s look over the whole village surroundings before wandering further out
                wideScanCooldown = 10;
                found = scan(v.village().center, v.village().radius + 20, v.getPosition());
            }
            if (found != null) {
                treeBase = found;
                logs = null;
                explore = null;
                searchDistance = 10;
                return;
            }
        }
        if (explore == null) explore = exploreTarget(searchDistance);
        if (goTo(explore, 5.0) || isStuck()) {
            explore = null;
            searchDistance = Math.min(96, searchDistance + 10);
        }
    }

    private static boolean isLog(IBlockState s) {
        return s.getBlock() instanceof BlockLog;
    }

    private BlockPos scan(BlockPos center, int radius, BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.getAllInBoxMutable(center.add(-radius, -6, -radius), center.add(radius, 10, radius))) {
            IBlockState s = world.getBlockState(p);
            if (!(s.getBlock() instanceof BlockLog)) continue;
            Material below = world.getBlockState(p.down()).getMaterial();
            if (below != Material.GRASS && below != Material.GROUND) continue;
            double d = p.distanceSq(from);
            if (d >= bestDist) continue;
            BlockPos base = p.toImmutable();
            if (insideAnyHall(base) || collectTree(base) == null) continue;
            best = base;
            bestDist = d;
        }
        return best;
    }

    /** The connected trunk of a natural tree (leaves on top, nothing man-made touching it), or null. */
    private List<BlockPos> collectTree(BlockPos base) {
        IBlockState baseState = world.getBlockState(base);
        if (!isLog(baseState)) return null;
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(base);
        queue.add(base);
        int leaves = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            out.add(p);
            if (out.size() > MAX_LOGS) return null;
            for (BlockPos n : BlockPos.getAllInBoxMutable(p.add(-1, 0, -1), p.add(1, 1, 1))) {
                if (seen.contains(n)) continue;
                IBlockState s = world.getBlockState(n);
                if (s.getMaterial() == Material.LEAVES) leaves++;
                if (manMade(s)) return null;
                if (isLog(s) && n.getY() >= base.getY()) {
                    BlockPos im = n.toImmutable();
                    seen.add(im);
                    queue.add(im);
                }
            }
        }
        if (leaves < 3) return null;
        woodType = woodTypeOf(baseState);
        out.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(b.getY(), a.getY())
                : Double.compare(a.distanceSq(base), b.distanceSq(base)));
        return out;
    }

    private static boolean manMade(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.PLANKS || b == Blocks.COBBLESTONE || b == Blocks.BOOKSHELF || b == Blocks.CHEST
                || b == Blocks.CRAFTING_TABLE || b == Blocks.FARMLAND || b == Blocks.GLASS || b == Blocks.GLASS_PANE
                || b instanceof BlockStairs || b instanceof BlockFence || b instanceof BlockFenceGate || b instanceof BlockDoor;
    }

    private static BlockPlanks.EnumType woodTypeOf(IBlockState s) {
        if (s.getBlock() instanceof BlockOldLog) return s.getValue(BlockOldLog.VARIANT);
        if (s.getBlock() instanceof BlockNewLog) return s.getValue(BlockNewLog.VARIANT);
        return null;
    }

    private void replant() {
        BlockPos base = treeBase;
        if (!world.isAirBlock(base)) return;
        IBlockState soil = world.getBlockState(base.down());
        int slot = -1;
        for (int i = 0; i < 27; i++) {
            ItemStack s = v.inventory.getStackInSlot(i);
            if (!(s.getItem() instanceof ItemBlock) || !(((ItemBlock) s.getItem()).getBlock() instanceof BlockSapling)) continue;
            if (slot < 0) slot = i;
            if (woodType != null && s.getMetadata() == woodType.getMetadata()) { slot = i; break; }
        }
        if (slot < 0) return;
        ItemStack sapling = v.inventory.getStackInSlot(slot);
        Block block = ((ItemBlock) sapling.getItem()).getBlock();
        if (!soil.getBlock().canSustainPlant(soil, world, base.down(), EnumFacing.UP, (BlockSapling) block)) return;
        world.setBlockState(base, block.getStateFromMeta(sapling.getMetadata()));
        v.inventory.decrStackSize(slot, 1);
        v.swing();
    }

    @Override
    public void pause() {
        super.pause();
        v.setPickupRadius(1);
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        if (treeBase != null) t.setTag("Tree", NBTUtil.createPosTag(treeBase));
        t.setInteger("Search", searchDistance);
        return t;
    }

    @Override
    public void read(NBTTagCompound t) {
        treeBase = t.hasKey("Tree") ? NBTUtil.getPosFromTag(t.getCompoundTag("Tree")) : null;
        searchDistance = Math.max(10, t.getInteger("Search"));
    }
}
