package com.electrifiedded.helpfulvillagers.village;

import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A detected guild hall: the room behind a wooden door that has an item frame with a job symbol next to it.
 * Halls are cheap to rebuild and are re-scanned periodically, so they are never saved.
 */
public class GuildHall {

    public final Profession profession;
    public final BlockPos door;
    /** walkable position just outside the door */
    public final BlockPos entrance;
    /** walkable air cells inside the room */
    public final List<BlockPos> inside;
    public final List<BlockPos> chests;
    public final List<BlockPos> furnaces;
    public final List<BlockPos> workbenches;
    /** Ranch halls only: walkable cells of the fenced pasture next to the hall. */
    public final List<BlockPos> pasture;
    public final AxisAlignedBB bounds;
    public final AxisAlignedBB pastureBounds;

    GuildHall(Profession profession, BlockPos door, BlockPos entrance, Set<BlockPos> inside, List<BlockPos> chests,
              List<BlockPos> furnaces, List<BlockPos> workbenches, List<BlockPos> pasture) {
        this.profession = profession;
        this.door = door;
        this.entrance = entrance;
        this.inside = Collections.unmodifiableList(new ArrayList<>(inside));
        this.chests = Collections.unmodifiableList(chests);
        this.furnaces = Collections.unmodifiableList(furnaces);
        this.workbenches = Collections.unmodifiableList(workbenches);
        this.pasture = Collections.unmodifiableList(pasture);
        this.bounds = box(inside).grow(1);
        this.pastureBounds = pasture.isEmpty() ? null : box(pasture).grow(0.5, 2, 0.5);
    }

    private static AxisAlignedBB box(Iterable<BlockPos> cells) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : cells) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        if (x0 == Integer.MAX_VALUE) return new AxisAlignedBB(0, 0, 0, 0, 0, 0);
        return new AxisAlignedBB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
    }

    public boolean contains(double x, double y, double z) {
        return bounds.contains(new net.minecraft.util.math.Vec3d(x, y, z));
    }

    public boolean isNear(double x, double y, double z) {
        return bounds.grow(3).contains(new net.minecraft.util.math.Vec3d(x, y, z));
    }

    public BlockPos randomInside(Random rand) {
        return inside.isEmpty() ? entrance : inside.get(rand.nextInt(inside.size()));
    }

    public List<IInventory> chestInventories(World world) {
        List<IInventory> out = new ArrayList<>();
        for (BlockPos p : chests) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileEntityChest) out.add((IInventory) te);
        }
        return out;
    }

    public List<TileEntityFurnace> furnaceTiles(World world) {
        List<TileEntityFurnace> out = new ArrayList<>();
        for (BlockPos p : furnaces) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileEntityFurnace) out.add((TileEntityFurnace) te);
        }
        return out;
    }

    /** First chest that still has a free slot, or null. */
    public BlockPos chestWithRoom(World world) {
        for (BlockPos p : chests) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof IInventory) {
                IInventory inv = (IInventory) te;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    if (inv.getStackInSlot(i).isEmpty()) return p;
                }
            }
        }
        return null;
    }

    /** Puts as much of {@code stack} as fits into the hall chests; returns the rest. */
    public ItemStack store(World world, ItemStack stack) {
        for (IInventory inv : chestInventories(world)) {
            stack = InventoryUtil.insert(inv, stack);
            if (stack.isEmpty()) break;
        }
        return stack;
    }
}
