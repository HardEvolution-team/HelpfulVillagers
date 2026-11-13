package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockMelon;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockStem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.IPlantable;

import java.util.ArrayList;
import java.util.List;

/** Harvests ripe crops on the village fields and replants them with seeds from the harvest. */
public class FarmerJob extends WorkerJob {

    private final List<BlockPos> targets = new ArrayList<>();
    private int waitTicks;
    private int harvestTicks;
    private BlockPos current;

    FarmerJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    protected int keep(ItemStack stack) {
        return stack.getItem() instanceof IPlantable ? 32 : 0;
    }

    @Override
    protected void work() {
        if (targets.isEmpty()) {
            if (waitTicks > 0) {
                waitTicks--;
                if (v.hall() != null) rest(v.hall());
                return;
            }
            scanFields();
            if (targets.isEmpty()) {
                waitTicks = 600;
                if (!v.inventory.isMainEmpty()) storeRequested = true;
                return;
            }
        }
        BlockPos p = targets.get(0);
        if (!isRipe(p)) {
            targets.remove(0);
            current = null;
            return;
        }
        if (!goTo(p, 2.5)) {
            if (isStuck()) targets.remove(0);
            return;
        }
        v.getLookHelper().setLookPosition(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 30F, 30F);
        if (!p.equals(current)) {
            current = p;
            harvestTicks = 0;
        }
        if (harvestTicks++ % 5 == 0) v.swing();
        if (harvestTicks < harvestTime()) return;
        reap(p);
        targets.remove(0);
        current = null;
    }

    private int harvestTime() {
        ItemStack hoe = v.getHeldItemMainhand();
        if (!(hoe.getItem() instanceof ItemHoe)) return 30;
        Item.ToolMaterial m;
        try {
            m = Item.ToolMaterial.valueOf(((ItemHoe) hoe.getItem()).getMaterialName());
        } catch (IllegalArgumentException e) {
            return 20;
        }
        return Math.max(6, Math.round(40F / (1F + m.getEfficiency() / 2F)));
    }

    private void scanFields() {
        VillageRecord village = v.village();
        if (village == null) return;
        BlockPos c = village.center;
        int r = village.radius;
        BlockPos.MutableBlockPos from = new BlockPos.MutableBlockPos();
        for (BlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -8, -r), c.add(r, 10, r))) {
            if (isRipe(p)) targets.add(p.toImmutable());
            if (targets.size() >= 96) break;
        }
        from.setPos(v.posX, v.posY, v.posZ);
        // nearest-neighbour ordering so the farmer walks along the rows
        List<BlockPos> ordered = new ArrayList<>();
        BlockPos at = from.toImmutable();
        while (!targets.isEmpty()) {
            int bi = 0;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < targets.size(); i++) {
                double d = targets.get(i).distanceSq(at);
                if (d < bd) { bd = d; bi = i; }
            }
            at = targets.remove(bi);
            ordered.add(at);
        }
        targets.addAll(ordered);
    }

    private boolean isRipe(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        Block b = s.getBlock();
        if (b instanceof BlockCrops) return ((BlockCrops) b).isMaxAge(s);
        if (b instanceof BlockNetherWart) return s.getValue(BlockNetherWart.AGE) >= 3;
        if (b instanceof BlockCocoa) return s.getValue(BlockCocoa.AGE) >= 2;
        if (b instanceof BlockReed) return world.getBlockState(p.down()).getBlock() instanceof BlockReed
                && !(world.getBlockState(p.down(2)).getBlock() instanceof BlockReed);
        if (b instanceof BlockPumpkin || b instanceof BlockMelon) {
            for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
                if (world.getBlockState(p.offset(f)).getBlock() instanceof BlockStem) return true;
            }
        }
        return false;
    }

    private void reap(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        Block b = s.getBlock();
        if (b instanceof BlockReed) {
            // cut everything above the bottom cane so it grows back
            BlockPos top = p;
            while (world.getBlockState(top.up()).getBlock() instanceof BlockReed) top = top.up();
            for (BlockPos q = top; q.getY() >= p.getY(); q = q.down()) harvest(q);
            return;
        }
        boolean replant = b instanceof BlockCrops || b instanceof BlockNetherWart || b instanceof BlockCocoa;
        ItemStack seed = replant ? b.getItem(world, p, s) : ItemStack.EMPTY;
        IBlockState cocoa = b instanceof BlockCocoa ? b.getDefaultState().withProperty(BlockCocoa.FACING, s.getValue(BlockCocoa.FACING)) : null;
        harvest(p);
        if (!replant || seed.isEmpty() || !world.isAirBlock(p)) return;
        int slot = v.inventory.find(st -> st.getItem() == seed.getItem() && st.getMetadata() == seed.getMetadata());
        if (slot < 0) return;
        if (cocoa != null) {
            world.setBlockState(p, cocoa);
        } else if (seed.getItem() instanceof IPlantable) {
            IPlantable plant = (IPlantable) seed.getItem();
            IBlockState soil = world.getBlockState(p.down());
            if (!soil.getBlock().canSustainPlant(soil, world, p.down(), EnumFacing.UP, plant)) return;
            world.setBlockState(p, plant.getPlant(world, p));
        } else {
            return;
        }
        v.inventory.decrStackSize(slot, 1);
    }

    @Override
    public void onNewDay() {
        targets.clear();
        waitTicks = 0;
    }
}
