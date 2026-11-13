package com.electrifiedded.helpfulvillagers.village;

import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds guild halls: an item frame with a job symbol within two blocks of a wooden door marks the hall,
 * and the hall is the closed room behind that door (flood fill of walkable cells, which must not leak).
 */
public final class HallScanner {

    private static final int MAX_ROOM_CELLS = 900;
    private static final int MAX_FENCE = 600;

    private HallScanner() {}

    public static List<GuildHall> scan(World world, AxisAlignedBB area) {
        List<GuildHall> halls = new ArrayList<>();
        Set<BlockPos> usedDoors = new HashSet<>();
        for (EntityItemFrame frame : world.getEntitiesWithinAABB(EntityItemFrame.class, area)) {
            Profession prof = Profession.forHallSymbol(frame.getDisplayedItem());
            if (prof == null) continue;
            BlockPos door = findDoor(world, frame);
            if (door == null || !usedDoors.add(door.toImmutable())) continue;
            GuildHall hall = buildHall(world, prof, door, frame.facingDirection);
            if (hall != null) halls.add(hall);
        }
        return halls;
    }

    private static boolean isWoodenDoor(IBlockState s) {
        return s.getBlock() instanceof BlockDoor && s.getMaterial() == Material.WOOD;
    }

    private static BlockPos findDoor(World world, EntityItemFrame frame) {
        BlockPos center = frame.getHangingPosition().offset(frame.facingDirection);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.getAllInBoxMutable(center.add(-2, -2, -2), center.add(2, 2, 2))) {
            IBlockState s = world.getBlockState(p);
            if (!isWoodenDoor(s)) continue;
            BlockPos lower = s.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER ? p.toImmutable() : p.down();
            double d = lower.distanceSq(center);
            if (d < bestDist) { bestDist = d; best = lower; }
        }
        return best;
    }

    static boolean walkable(World world, BlockPos p) {
        IBlockState s = world.getBlockState(p);
        if (s.getBlock() instanceof BlockDoor || s.getBlock() instanceof BlockFenceGate) return false;
        return !s.getMaterial().blocksMovement() && !s.getMaterial().isLiquid();
    }

    private static GuildHall buildHall(World world, Profession prof, BlockPos door, EnumFacing frameFacing) {
        IBlockState doorState = world.getBlockState(door);
        if (!isWoodenDoor(doorState)) return null;
        EnumFacing facing = doorState.getValue(BlockDoor.FACING);
        BlockPos a = door.offset(facing), b = door.offset(facing.getOpposite());
        Set<BlockPos> roomA = flood(world, door, a), roomB = flood(world, door, b);
        Set<BlockPos> room;
        BlockPos entrance;
        if (roomA != null && roomB != null) {
            // both sides closed (door between two rooms): the frame hangs outside, so the hall is the other side
            boolean aIsFrameSide = facing == frameFacing;
            room = aIsFrameSide ? roomB : roomA;
            entrance = aIsFrameSide ? a : b;
        } else if (roomA != null) {
            room = roomA; entrance = b;
        } else if (roomB != null) {
            room = roomB; entrance = a;
        } else {
            return null;
        }
        Set<BlockPos> chests = new LinkedHashSet<>(), furnaces = new LinkedHashSet<>(), benches = new LinkedHashSet<>();
        for (BlockPos cell : room) {
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos n = cell.offset(f);
                if (room.contains(n)) continue;
                Block block = world.getBlockState(n).getBlock();
                TileEntity te = world.getTileEntity(n);
                if (te instanceof TileEntityChest) chests.add(n);
                else if (te instanceof TileEntityFurnace) furnaces.add(n);
                else if (block instanceof BlockWorkbench) benches.add(n);
            }
        }
        List<BlockPos> pasture = prof == Profession.RANCHER ? findPasture(world, room, entrance) : new ArrayList<>();
        Set<BlockPos> standable = new LinkedHashSet<>();
        for (BlockPos cell : room) {
            if (!room.contains(cell.down()) && room.contains(cell.up())) standable.add(cell);
        }
        if (standable.isEmpty()) return null;
        return new GuildHall(prof, door, entrance, standable, new ArrayList<>(chests), new ArrayList<>(furnaces),
                new ArrayList<>(benches), pasture);
    }

    /** Flood fill of walkable cells starting at {@code start}; null if the area is open (leaks out). */
    private static Set<BlockPos> flood(World world, BlockPos door, BlockPos start) {
        if (!walkable(world, start)) return null;
        int minY = door.getY() - 1, maxY = door.getY() + 4;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos n = p.offset(f);
                if (n.getY() < minY || n.getY() > maxY || seen.contains(n)) continue;
                if (!world.isBlockLoaded(n)) return null;
                if (!walkable(world, n)) continue;
                // a room that reaches open sky above its top layer is not closed
                if (n.getY() == maxY && world.canSeeSky(n)) return null;
                seen.add(n);
                if (seen.size() > MAX_ROOM_CELLS) return null;
                queue.add(n);
            }
        }
        // keep only cells you can stand in (solid floor below), plus everything for container adjacency
        return seen;
    }

    private static boolean isFence(World world, BlockPos p) {
        Block b = world.getBlockState(p).getBlock();
        return b instanceof BlockFence || b instanceof BlockFenceGate;
    }

    /** Fenced area touching the hall (or right outside its door). */
    private static List<BlockPos> findPasture(World world, Set<BlockPos> room, BlockPos entrance) {
        BlockPos seed = null;
        Set<BlockPos> probe = new LinkedHashSet<>(room);
        probe.add(entrance);
        search:
        for (BlockPos cell : probe) {
            for (BlockPos p : BlockPos.getAllInBoxMutable(cell.add(-3, -1, -3), cell.add(3, 1, 3))) {
                if (isFence(world, p)) { seed = p.toImmutable(); break search; }
            }
        }
        if (seed == null) return new ArrayList<>();
        Set<BlockPos> fence = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        fence.add(seed);
        queue.add(seed);
        while (!queue.isEmpty() && fence.size() < MAX_FENCE) {
            BlockPos p = queue.poll();
            for (BlockPos n : BlockPos.getAllInBoxMutable(p.add(-1, -1, -1), p.add(1, 1, 1))) {
                if (!fence.contains(n) && isFence(world, n)) {
                    BlockPos im = n.toImmutable();
                    fence.add(im);
                    queue.add(im);
                }
            }
        }
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
        for (BlockPos p : fence) {
            x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX());
            z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ());
            top = Math.max(top, p.getY());
        }
        List<BlockPos> cells = new ArrayList<>();
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                for (int y = top + 1; y > top - 6; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (room.contains(p)) break;
                    if (walkable(world, p) && world.getBlockState(p.down()).getMaterial().blocksMovement() && !isFence(world, p.down())) {
                        cells.add(p);
                        break;
                    }
                }
            }
        }
        return cells;
    }
}
