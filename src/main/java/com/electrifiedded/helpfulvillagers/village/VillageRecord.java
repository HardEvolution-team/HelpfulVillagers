package com.electrifiedded.helpfulvillagers.village;

import com.electrifiedded.helpfulvillagers.craft.CraftOrder;
import com.electrifiedded.helpfulvillagers.econ.Economy;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Everything Helpful Villagers remembers about one village: craft queue, economy, player credit accounts. */
public class VillageRecord {

    private static final int RESCAN_TICKS = 400;

    public BlockPos center;
    public int radius;
    public final List<CraftOrder> queue = new ArrayList<>();
    public final Map<UUID, Integer> accounts = new HashMap<>();
    public final Economy economy = new Economy();
    /** orders a villager is working on right now (not saved: a reload puts them back in the queue) */
    public final transient List<CraftOrder> active = new ArrayList<>();

    private transient List<GuildHall> halls = new ArrayList<>();
    private transient long lastScan = Long.MIN_VALUE;
    private transient boolean scanRequested = true;
    public transient EntityLivingBase lastAggressor;
    public transient long lastUsed;

    VillageRecord(BlockPos center, int radius) {
        this.center = center;
        this.radius = radius;
    }

    public AxisAlignedBB bounds() {
        return new AxisAlignedBB(center).grow(radius, Math.max(12, radius / 2), radius);
    }

    public boolean isInside(double x, double y, double z) {
        return bounds().contains(new net.minecraft.util.math.Vec3d(x, y, z));
    }

    public void requestScan() {
        scanRequested = true;
    }

    public void tick(World world) {
        long now = world.getTotalWorldTime();
        if (scanRequested || now - lastScan >= RESCAN_TICKS) {
            halls = HallScanner.scan(world, bounds().grow(8));
            lastScan = now;
            scanRequested = false;
        }
        if (economy.surveyRunning()) economy.tickSurvey(world);
        else if (!economy.isReady()) economy.startSurvey(center, Math.max(32, radius), world);
        if (lastAggressor != null && (!lastAggressor.isEntityAlive() || !isInside(lastAggressor.posX, lastAggressor.posY, lastAggressor.posZ))) {
            lastAggressor = null;
        }
    }

    public List<GuildHall> halls() {
        return halls;
    }

    public List<GuildHall> halls(Profession p) {
        List<GuildHall> out = new ArrayList<>();
        for (GuildHall h : halls) if (h.profession == p) out.add(h);
        return out;
    }

    public GuildHall nearestHall(Profession p, BlockPos from) {
        GuildHall best = null;
        double bd = Double.MAX_VALUE;
        for (GuildHall h : halls) {
            if (h.profession != p) continue;
            double d = h.door.distanceSq(from);
            if (d < bd) { bd = d; best = h; }
        }
        return best;
    }

    /** Hall that contains or is very close to the given position (used to re-find a villager's hall after a rescan). */
    public GuildHall hallAt(Profession p, BlockPos door) {
        for (GuildHall h : halls) if (h.profession == p && h.door.equals(door)) return h;
        return null;
    }

    public Set<Profession> unlocked() {
        Set<Profession> set = EnumSet.of(Profession.VILLAGER);
        for (GuildHall h : halls) set.add(h.profession);
        return set;
    }

    /** All chests of all halls of the village. */
    public List<IInventory> allChests(World world) {
        List<IInventory> out = new ArrayList<>();
        for (GuildHall h : halls) out.addAll(h.chestInventories(world));
        return out;
    }

    public List<ItemStack> stockSnapshot(World world) {
        List<ItemStack> out = new ArrayList<>();
        for (IInventory inv : allChests(world)) {
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty()) out.add(s.copy());
            }
        }
        return out;
    }

    /** Finds a hall chest holding a matching item, preferring the given hall. */
    public BlockPos findInChests(World world, Predicate<ItemStack> filter, GuildHall preferred) {
        if (preferred != null) {
            for (BlockPos p : preferred.chests) if (chestHas(world, p, filter)) return p;
        }
        for (GuildHall h : halls) {
            for (BlockPos p : h.chests) if (chestHas(world, p, filter)) return p;
        }
        return null;
    }

    private static boolean chestHas(World world, BlockPos p, Predicate<ItemStack> filter) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(p);
        return te instanceof IInventory && InventoryUtil.count((IInventory) te, filter) > 0;
    }

    // --- craft queue ----------------------------------------------------------------------------------

    public void addOrder(CraftOrder order) {
        if (order.byPlayer()) {
            int i = 0;
            while (i < queue.size() && queue.get(i).byPlayer()) i++;
            queue.add(i, order);
        } else {
            queue.add(order);
        }
    }

    public boolean hasVillagerOrder(Profession forProf, ItemStack item) {
        for (CraftOrder o : active) {
            if (!o.byPlayer() && o.forProfession == forProf && InventoryUtil.same(o.item, item)) return true;
        }
        for (CraftOrder o : queue) {
            if (!o.byPlayer() && o.forProfession == forProf && InventoryUtil.same(o.item, item)) return true;
        }
        return false;
    }

    // --- accounts -------------------------------------------------------------------------------------

    public int account(UUID player) {
        return accounts.getOrDefault(player, 0);
    }

    public void deposit(UUID player, int amount) {
        accounts.merge(player, amount, Integer::sum);
    }

    public boolean withdraw(UUID player, int amount) {
        int cur = account(player);
        if (amount < 0 || cur < amount) return false;
        accounts.put(player, cur - amount);
        return true;
    }

    // --- persistence ----------------------------------------------------------------------------------

    NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("Center", NBTUtil.createPosTag(center));
        t.setInteger("Radius", radius);
        NBTTagList q = new NBTTagList();
        for (CraftOrder o : queue) q.appendTag(o.write());
        t.setTag("Queue", q);
        NBTTagList acc = new NBTTagList();
        for (Map.Entry<UUID, Integer> e : accounts.entrySet()) {
            NBTTagCompound a = new NBTTagCompound();
            a.setUniqueId("Id", e.getKey());
            a.setInteger("Credits", e.getValue());
            acc.appendTag(a);
        }
        t.setTag("Accounts", acc);
        t.setTag("Economy", economy.write());
        return t;
    }

    static VillageRecord read(NBTTagCompound t) {
        VillageRecord r = new VillageRecord(NBTUtil.getPosFromTag(t.getCompoundTag("Center")), t.getInteger("Radius"));
        NBTTagList q = t.getTagList("Queue", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < q.tagCount(); i++) {
            CraftOrder o = CraftOrder.read(q.getCompoundTagAt(i));
            if (o != null) r.queue.add(o);
        }
        NBTTagList acc = t.getTagList("Accounts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < acc.tagCount(); i++) {
            NBTTagCompound a = acc.getCompoundTagAt(i);
            r.accounts.put(a.getUniqueId("Id"), a.getInteger("Credits"));
        }
        r.economy.read(t.getCompoundTag("Economy"));
        return r;
    }
}
