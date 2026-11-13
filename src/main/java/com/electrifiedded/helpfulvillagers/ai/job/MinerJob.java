package com.electrifiedded.helpfulvillagers.ai.job;

import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Digs a 2x2 spiral staircase down to the configured depth outside the village, mines every ore vein
 * it uncovers, then runs a strip tunnel from the bottom. Like the original, the miner climbs the shaft
 * "off screen": it is moved to the top when it heads home and back to its stair when it returns.
 */
public class MinerJob extends WorkerJob {

    private static final EnumFacing[] SPIRAL = {EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST};
    private static final int TUNNEL_LENGTH = 40;

    private BlockPos top;
    private int startDir;
    /** number of stairs already finished */
    private int depth;
    /** blocks of strip tunnel already dug */
    private int tunnel = -1;
    private BlockPos explore;
    private int searchDistance = 10;
    private final List<BlockPos> vein = new ArrayList<>();

    MinerJob(EntityHelpfulVillager v) {
        super(v);
    }

    @Override
    protected int keep(ItemStack stack) {
        return isFiller(stack) ? 64 : (stack.getItem() == Item.getItemFromBlock(Blocks.TORCH) ? 64 : 0);
    }

    private static boolean isFiller(ItemStack s) {
        Item i = s.getItem();
        return i == Item.getItemFromBlock(Blocks.COBBLESTONE) || i == Item.getItemFromBlock(Blocks.DIRT)
                || i == Item.getItemFromBlock(Blocks.STONE) || i == Item.getItemFromBlock(Blocks.NETHERRACK);
    }

    private BlockPos stair(int index) {
        BlockPos p = top;
        for (int i = 0; i < index; i++) p = p.down().offset(SPIRAL[(startDir + i) & 3]);
        return p;
    }

    @Override
    protected void storeAndRest() {
        climbOut();
        super.storeAndRest();
    }

    @Override
    protected void fetchTool() {
        climbOut();
        super.fetchTool();
    }

    @Override
    protected void leaveWorksite() {
        climbOut();
    }

    /** Back to the surface when leaving the shaft. */
    private void climbOut() {
        if (top != null && v.posY < top.getY() - 3 && world.isBlockLoaded(top)) {
            v.getNavigator().clearPath();
            v.setPositionAndUpdate(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
        }
    }

    @Override
    protected void work() {
        if (top == null) {
            findSite();
            return;
        }
        if (!vein.isEmpty()) {
            mineVein();
            return;
        }
        if (tunnel < 0) digStair();
        else digTunnel();
    }

    private void findSite() {
        if (explore == null) explore = exploreTarget(searchDistance);
        if (goTo(explore, 3.0) || isStuck()) {
            BlockPos ground = world.getHeight(v.getPosition()).down();
            if (goodSite(ground)) {
                top = ground;
                startDir = v.getRNG().nextInt(4);
                depth = 0;
                tunnel = -1;
                searchDistance = 10;
            } else {
                searchDistance = Math.min(80, searchDistance + 8);
            }
            explore = null;
        }
    }

    private boolean goodSite(BlockPos ground) {
        if (v.village() != null && v.village().isInside(ground.getX(), ground.getY(), ground.getZ())) return false;
        for (BlockPos p : BlockPos.getAllInBoxMutable(ground.add(-2, -3, -2), ground.add(2, 0, 2))) {
            if (world.getBlockState(p).getMaterial().isLiquid()) return false;
        }
        IBlockState s = world.getBlockState(ground);
        return s.getMaterial().isSolid() && !(s.getBlock() instanceof BlockFalling);
    }

    private boolean hazard(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        return s.getMaterial().isLiquid() || s.getBlock() == Blocks.BEDROCK || s.getBlockHardness(world, p) < 0;
    }

    private void digStair() {
        BlockPos next = stair(depth + 1);
        if (next.getY() <= ModConfig.minerTargetY || hazard(next) || hazard(next.down())) {
            tunnel = 0;
            return;
        }
        BlockPos stand = stair(depth);
        if (!reach(stand.up())) return;
        for (int h = 3; h >= 1; h--) {
            BlockPos head = next.up(h);
            if (hazard(head)) { tunnel = 0; return; }
            if (!world.isAirBlock(head)) { dig(head); return; }
        }
        if (!solidFloor(next)) return;
        depth++;
        scanForOre(next);
        if (depth % 6 == 0) placeTorch(next.up());
    }

    /** Makes sure a stair can be stood on; fills holes with cobble/dirt from the bag. */
    private boolean solidFloor(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        if (s.getMaterial().isSolid() && !(s.getBlock() instanceof BlockFalling)) return true;
        if (s.getBlock() instanceof BlockFalling) { dig(p); return false; }
        if (!placeFiller(p)) {
            // nothing to build with: stop here and use the tunnel instead
            tunnel = 0;
            return false;
        }
        return true;
    }

    private boolean placeFiller(BlockPos p) {
        int slot = v.inventory.find(MinerJob::isFiller);
        if (slot < 0) return false;
        ItemStack fill = v.inventory.getStackInSlot(slot);
        world.setBlockState(p, ((ItemBlock) fill.getItem()).getBlock().getStateFromMeta(fill.getMetadata()));
        v.inventory.decrStackSize(slot, 1);
        v.swing();
        return true;
    }

    /**
     * Gets the miner to its working spot (feet position). The shaft is climbed "off screen": at the shaft
     * but on the wrong level, or wedged for a few seconds, it is simply moved there.
     */
    private boolean reach(BlockPos feet) {
        double dx = v.posX - (top.getX() + 0.5), dz = v.posZ - (top.getZ() + 0.5);
        boolean wrongLevel = Math.abs(v.posY - feet.getY()) > 2.5;
        boolean atShaft = dx * dx + dz * dz < 36;
        if (wrongLevel && !atShaft) {
            // still on the way from the village: walk to the shaft mouth first
            if (!goTo(top.up(), 2.5) && !stuckFor(200)) return false;
            atShaft = true;
        }
        if (!(wrongLevel && atShaft) && goTo(feet, 2.0)) return true;
        if ((wrongLevel && atShaft) || stuckFor(40)) {
            clearMove();
            v.setPositionAndUpdate(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        }
        return false;
    }

    /** Whether the block is part of the walkway (a stair or the tunnel floor) and must stay solid. */
    private boolean isFloor(BlockPos p) {
        BlockPos s = top;
        for (int i = 0; i <= depth + 1; i++) {
            if (s.equals(p)) return true;
            s = s.down().offset(SPIRAL[(startDir + i) & 3]);
        }
        if (tunnel >= 0) {
            BlockPos start = stair(depth);
            EnumFacing dir = SPIRAL[(startDir + depth) & 3];
            for (int t = 1; t <= tunnel + 1; t++) if (start.offset(dir, t).equals(p)) return true;
        }
        return false;
    }

    private void digTunnel() {
        if (tunnel >= TUNNEL_LENGTH) {
            // this mine is done: back up, then look for a new site
            climbOut();
            top = null;
            return;
        }
        BlockPos start = stair(depth);
        EnumFacing dir = SPIRAL[(startDir + depth) & 3];
        BlockPos floor = start.offset(dir, tunnel + 1);
        if (hazard(floor) || hazard(floor.up()) || hazard(floor.up(2)) || hazard(floor.up(3))) {
            tunnel = TUNNEL_LENGTH;
            return;
        }
        BlockPos stand = tunnel == 0 ? start.up() : start.offset(dir, tunnel).up();
        if (!reach(stand)) return;
        for (int h = 2; h >= 1; h--) {
            BlockPos b = floor.up(h);
            if (!world.isAirBlock(b)) { dig(b); return; }
        }
        if (!solidFloor(floor)) return;
        tunnel++;
        scanForOre(floor.up());
        if (tunnel % 8 == 0) placeTorch(floor.up());
    }

    private static boolean isOre(IBlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.AIR) return false;
        Item item = Item.getItemFromBlock(b);
        if (item == Item.getItemFromBlock(Blocks.AIR)) return b == Blocks.LIT_REDSTONE_ORE;
        ItemStack stack = new ItemStack(item, 1, b.damageDropped(s));
        if (stack.isEmpty()) return false;
        for (int id : OreDictionary.getOreIDs(stack)) {
            if (OreDictionary.getOreName(id).startsWith("ore")) return true;
        }
        return false;
    }

    private void scanForOre(BlockPos around) {
        for (BlockPos p : BlockPos.getAllInBoxMutable(around.add(-2, -2, -2), around.add(2, 3, 2))) {
            if (isOre(world.getBlockState(p))) {
                collectVein(p.toImmutable());
                if (!vein.isEmpty()) return;
            }
        }
    }

    private void collectVein(BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && vein.size() < 48) {
            BlockPos p = q.poll();
            vein.add(p);
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos n = p.offset(f);
                if (seen.add(n) && isOre(world.getBlockState(n))) q.add(n);
            }
        }
    }

    private void mineVein() {
        BlockPos p = vein.get(0);
        if (!isOre(world.getBlockState(p))) {
            vein.remove(0);
            return;
        }
        // don't open up lava or water pockets
        for (EnumFacing f : EnumFacing.values()) {
            if (world.getBlockState(p.offset(f)).getMaterial().isLiquid()) { vein.remove(0); return; }
        }
        // ore that is part of the walkway is only taken when the hole can be patched right away
        boolean floor = top != null && isFloor(p);
        if (floor && v.inventory.find(MinerJob::isFiller) < 0) { vein.remove(0); return; }
        if (dig(p)) {
            if (floor) placeFiller(p);
            vein.remove(0);
            v.setPickupRadius(3);
        }
    }

    private void placeTorch(BlockPos air) {
        int slot = v.inventory.find(s -> s.getItem() == Item.getItemFromBlock(Blocks.TORCH));
        if (slot < 0) return;
        for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
            BlockPos wall = air.offset(f);
            if (world.isAirBlock(air) && world.isSideSolid(wall, f.getOpposite())) {
                world.setBlockState(air, Blocks.TORCH.getDefaultState().withProperty(net.minecraft.block.BlockTorch.FACING, f.getOpposite()));
                v.inventory.decrStackSize(slot, 1);
                return;
            }
        }
    }

    @Override
    public void onNewDay() {
        if (tunnel >= TUNNEL_LENGTH) {
            climbOut();
            top = null;
        }
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        if (top != null) t.setTag("Top", NBTUtil.createPosTag(top));
        t.setInteger("Dir", startDir);
        t.setInteger("Depth", depth);
        t.setInteger("Tunnel", tunnel);
        return t;
    }

    @Override
    public void read(NBTTagCompound t) {
        top = t.hasKey("Top") ? NBTUtil.getPosFromTag(t.getCompoundTag("Top")) : null;
        startDir = t.getInteger("Dir") & 3;
        depth = t.getInteger("Depth");
        tunnel = t.hasKey("Tunnel") ? t.getInteger("Tunnel") : -1;
    }
}
