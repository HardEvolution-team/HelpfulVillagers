package com.electrifiedded.helpfulvillagers.village;

import com.electrifiedded.helpfulvillagers.HelpfulVillagers;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Per-dimension store of {@link VillageRecord}s. Records follow vanilla villages (doors + houses), so a
 * village is wherever Minecraft thinks it is; our data is attached to the nearest record.
 */
public class VillageManager extends WorldSavedData {

    private static final String NAME = HelpfulVillagers.MODID + "_villages";
    /** records not touched by any villager for this long are dropped from ticking (still saved) */
    private static final long IDLE_TICKS = 20 * 60;

    private final List<VillageRecord> records = new ArrayList<>();

    public VillageManager(String name) {
        super(name);
    }

    public static VillageManager get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        VillageManager m = (VillageManager) storage.getOrLoadData(VillageManager.class, NAME);
        if (m == null) {
            m = new VillageManager(NAME);
            storage.setData(NAME, m);
        }
        return m;
    }

    /** The village a position belongs to, creating a record for a new vanilla village. Null outside villages. */
    public VillageRecord find(World world, BlockPos pos) {
        Village v = world.getVillageCollection().getNearestVillage(pos, 32);
        if (v != null) {
            BlockPos c = v.getCenter();
            int r = Math.max(32, v.getVillageRadius());
            VillageRecord best = nearest(c, r + 16);
            if (best == null) {
                best = new VillageRecord(c, r);
                records.add(best);
            } else {
                best.center = c;
                best.radius = r;
            }
            best.lastUsed = world.getTotalWorldTime();
            markDirty();
            return best;
        }
        // vanilla village temporarily gone (night, doors broken): keep using our record if we are inside it
        for (VillageRecord rec : records) {
            if (rec.isInside(pos.getX(), pos.getY(), pos.getZ())) {
                rec.lastUsed = world.getTotalWorldTime();
                return rec;
            }
        }
        return null;
    }

    private VillageRecord nearest(BlockPos c, int maxDist) {
        VillageRecord best = null;
        double bd = (double) maxDist * maxDist;
        for (VillageRecord rec : records) {
            double d = rec.center.distanceSq(c);
            if (d <= bd) { bd = d; best = rec; }
        }
        return best;
    }

    public void tick(World world) {
        long now = world.getTotalWorldTime();
        for (Iterator<VillageRecord> it = records.iterator(); it.hasNext(); ) {
            VillageRecord rec = it.next();
            if (now - rec.lastUsed > IDLE_TICKS) continue;
            rec.tick(world);
        }
        // merge records whose villages grew into each other
        for (int i = 0; i < records.size(); i++) {
            for (int j = records.size() - 1; j > i; j--) {
                VillageRecord a = records.get(i), b = records.get(j);
                if (a.center.distanceSq(b.center) < 16 * 16) {
                    a.queue.addAll(b.queue);
                    b.accounts.forEach((k, v) -> a.accounts.merge(k, v, Integer::sum));
                    records.remove(j);
                    markDirty();
                }
            }
        }
    }

    public void onNewDay() {
        for (VillageRecord rec : records) rec.economy.relax();
        markDirty();
    }

    public void requestScanNear(BlockPos pos) {
        for (VillageRecord rec : records) {
            if (rec.bounds().grow(8).contains(new net.minecraft.util.math.Vec3d(pos))) rec.requestScan();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        records.clear();
        NBTTagList list = nbt.getTagList("Villages", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) records.add(VillageRecord.read(list.getCompoundTagAt(i)));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (VillageRecord rec : records) list.appendTag(rec.write());
        nbt.setTag("Villages", list);
        return nbt;
    }

    /** Economy state changes constantly; always save. */
    @Override
    public boolean isDirty() {
        return true;
    }
}
