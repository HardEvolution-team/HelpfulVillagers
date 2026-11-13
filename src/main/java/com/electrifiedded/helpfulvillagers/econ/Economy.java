package com.electrifiedded.helpfulvillagers.econ;

import com.electrifiedded.helpfulvillagers.craft.RecipeIndex;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Village prices. Raw materials are priced by how rare they are around the village (1 = everywhere,
 * 100 = rarest), crafted items by the cheapest recipe, and both drift with supply and demand.
 * The block survey runs a slice per tick on the server thread instead of on a background thread.
 */
public class Economy {

    private static final int BLOCKS_PER_TICK = 12000;
    private static final double RELAX = 0.1;

    /** Things that never appear as blocks but should still be tradeable. */
    private static final Map<Item, Integer> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put(Items.LEATHER, 8); DEFAULTS.put(Items.STRING, 4); DEFAULTS.put(Items.BONE, 4);
        DEFAULTS.put(Items.ROTTEN_FLESH, 1); DEFAULTS.put(Items.GUNPOWDER, 10); DEFAULTS.put(Items.ENDER_PEARL, 40);
        DEFAULTS.put(Items.BLAZE_ROD, 50); DEFAULTS.put(Items.FEATHER, 3); DEFAULTS.put(Items.EGG, 2);
        DEFAULTS.put(Items.BEEF, 4); DEFAULTS.put(Items.PORKCHOP, 4); DEFAULTS.put(Items.CHICKEN, 3);
        DEFAULTS.put(Items.MUTTON, 4); DEFAULTS.put(Items.RABBIT, 4); DEFAULTS.put(Items.RABBIT_HIDE, 4);
        DEFAULTS.put(Items.RABBIT_FOOT, 20); DEFAULTS.put(Items.FISH, 4); DEFAULTS.put(Items.SLIME_BALL, 15);
        DEFAULTS.put(Items.SPIDER_EYE, 8); DEFAULTS.put(Items.GHAST_TEAR, 60); DEFAULTS.put(Items.APPLE, 6);
        DEFAULTS.put(Items.MILK_BUCKET, 12); DEFAULTS.put(Items.SADDLE, 40); DEFAULTS.put(Items.NAME_TAG, 40);
        DEFAULTS.put(Items.IRON_HORSE_ARMOR, 60); DEFAULTS.put(Items.GOLDEN_HORSE_ARMOR, 80);
        DEFAULTS.put(Items.DIAMOND_HORSE_ARMOR, 100); DEFAULTS.put(Items.PRISMARINE_SHARD, 10);
        DEFAULTS.put(Items.PRISMARINE_CRYSTALS, 12); DEFAULTS.put(Items.SHULKER_SHELL, 80);
        DEFAULTS.put(Items.NETHER_STAR, 500); DEFAULTS.put(Items.WHEAT, 2); DEFAULTS.put(Items.WHEAT_SEEDS, 1);
        DEFAULTS.put(Items.CARROT, 2); DEFAULTS.put(Items.POTATO, 2); DEFAULTS.put(Items.BEETROOT, 2);
        DEFAULTS.put(Items.REEDS, 2); DEFAULTS.put(Items.DYE, 5); DEFAULTS.put(Item.getItemFromBlock(Blocks.WOOL), 6);
        DEFAULTS.put(Items.DIAMOND, 100); DEFAULTS.put(Items.EMERALD, 60); DEFAULTS.put(Items.COAL, 6);
        DEFAULTS.put(Items.REDSTONE, 8); DEFAULTS.put(Items.QUARTZ, 10); DEFAULTS.put(Item.getItemFromBlock(Blocks.IRON_ORE), 20);
        DEFAULTS.put(Item.getItemFromBlock(Blocks.GOLD_ORE), 35);
    }

    /** raw material price by item key (persisted so the survey runs once) */
    private final Map<String, Integer> basePrices = new HashMap<>();
    private final Map<String, Double> supply = new HashMap<>();
    private final Map<String, Double> demand = new HashMap<>();
    private final transient Map<String, Integer> derived = new HashMap<>();
    private transient List<ItemStack> catalog;
    private boolean ready;

    // survey state
    private transient Map<String, Integer> counts;
    private transient Map<String, ItemStack> samples;
    private transient int sx0, sz0, sx1, sz1, sTop, cx, cy, cz;

    public boolean isReady() {
        return ready;
    }

    /** Starts (or restarts) the block survey of the given area. */
    public void startSurvey(BlockPos center, int radius, World world) {
        counts = new HashMap<>();
        samples = new HashMap<>();
        sx0 = center.getX() - radius; sx1 = center.getX() + radius;
        sz0 = center.getZ() - radius; sz1 = center.getZ() + radius;
        sTop = Math.min(world.getHeight() - 1, center.getY() + 24);
        cx = sx0; cz = sz0; cy = 1;
        ready = false;
    }

    public boolean surveyRunning() {
        return counts != null;
    }

    /** Advances the survey by one slice; call once per tick on the server. */
    public void tickSurvey(World world) {
        if (counts == null) return;
        Set<Item> craftedItems = craftedItems();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        NonNullList<ItemStack> drops = NonNullList.create();
        for (int n = 0; n < BLOCKS_PER_TICK; n++) {
            if (cx > sx1) { finishSurvey(); return; }
            p.setPos(cx, cy, cz);
            if (world.isBlockLoaded(p)) {
                IBlockState s = world.getBlockState(p);
                Block b = s.getBlock();
                if (b != Blocks.AIR && b != Blocks.BEDROCK && !s.getMaterial().isLiquid()) {
                    drops.clear();
                    try {
                        b.getDrops(drops, world, p, s, 0);
                    } catch (RuntimeException ignored) {
                        // some modded blocks need a tile entity or a player; skip them
                    }
                    ItemStack self = new ItemStack(Item.getItemFromBlock(b), 1, b.damageDropped(s));
                    if (!self.isEmpty() && b.canSilkHarvest(world, p, s, null)) drops.add(self);
                    for (ItemStack d : drops) {
                        if (d.isEmpty() || craftedItems.contains(d.getItem())) continue;
                        String k = RecipeIndex.key(d);
                        counts.merge(k, 1, Integer::sum);
                        samples.putIfAbsent(k, d.copy());
                    }
                }
            }
            if (++cy > sTop) { cy = 1; if (++cz > sz1) { cz = sz0; cx++; } }
        }
    }

    private static Set<Item> craftedItems() {
        Set<Item> set = new HashSet<>();
        for (ItemStack s : RecipeIndex.allOutputs()) set.add(s.getItem());
        return set;
    }

    private void finishSurvey() {
        basePrices.clear();
        int lo = Integer.MAX_VALUE, hi = 0;
        for (int c : counts.values()) { lo = Math.min(lo, c); hi = Math.max(hi, c); }
        if (!counts.isEmpty()) {
            // log-log line through (rarest -> 100) and (most common -> 1)
            double lx = Math.log(lo), hx = Math.log(hi);
            double slope = hx == lx ? 0 : (Math.log(1) - Math.log(100)) / (hx - lx);
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                double price = Math.exp(Math.log(100) + slope * (Math.log(e.getValue()) - lx));
                basePrices.put(e.getKey(), (int) Math.max(1, Math.min(100, Math.round(price))));
            }
        }
        counts = null;
        samples = null;
        derived.clear();
        catalog = null;
        ready = true;
    }

    // --- prices -------------------------------------------------------------------------------------

    /** Base (supply/demand neutral) price, or -1 if the village has no idea what this is worth. */
    public int basePrice(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        String k = RecipeIndex.key(stack);
        Integer b = basePrices.get(k);
        if (b != null) return b;
        Integer d = derived.get(k);
        if (d != null) return d;
        int v = derive(stack, new HashSet<>(), 0);
        derived.put(k, v);
        return v;
    }

    private int derive(ItemStack stack, Set<String> visiting, int depth) {
        String k = RecipeIndex.key(stack);
        Integer b = basePrices.get(k);
        if (b != null) return b;
        Integer cached = derived.get(k);
        if (cached != null) return cached;
        Integer def = DEFAULTS.get(stack.getItem());
        if (depth > 8 || !visiting.add(k)) return def != null ? def : -1;
        int best = Integer.MAX_VALUE;
        for (IRecipe r : RecipeIndex.craftingFor(stack)) {
            int sum = 0;
            boolean ok = true;
            for (Ingredient ing : r.getIngredients()) {
                if (ing == Ingredient.EMPTY || ing.getMatchingStacks().length == 0) continue;
                int cheapest = Integer.MAX_VALUE;
                for (ItemStack opt : ing.getMatchingStacks()) {
                    ItemStack o = opt.copy();
                    if (o.getMetadata() == OreDictionary.WILDCARD_VALUE) o.setItemDamage(0);
                    int p = derive(o, visiting, depth + 1);
                    if (p > 0) cheapest = Math.min(cheapest, p);
                }
                if (cheapest == Integer.MAX_VALUE) { ok = false; break; }
                sum += cheapest;
            }
            if (ok && sum > 0) best = Math.min(best, Math.max(1, sum / Math.max(1, r.getRecipeOutput().getCount())));
        }
        for (ItemStack[] sr : RecipeIndex.smeltingFor(stack)) {
            int p = derive(sr[0], visiting, depth + 1);
            if (p > 0) best = Math.min(best, p + 1);
        }
        visiting.remove(k);
        if (best == Integer.MAX_VALUE) return def != null ? def : -1;
        return def != null ? Math.min(best, def) : best;
    }

    /** Current price of one item including supply and demand. -1 if not tradeable. */
    public int price(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == Items.EMERALD) return -1;
        int base = basePrice(stack);
        if (base <= 0) return -1;
        String k = RecipeIndex.key(stack);
        double f = demand.getOrDefault(k, 1.0) / supply.getOrDefault(k, 1.0);
        return Math.max(1, (int) Math.round(base * f));
    }

    private static double unit(ItemStack s, int count) {
        return (double) count / Math.max(1, s.getMaxStackSize());
    }

    public void onBought(ItemStack s, int count) {
        demand.merge(RecipeIndex.key(s), unit(s, count), Double::sum);
    }

    public void onSold(ItemStack s, int count) {
        supply.merge(RecipeIndex.key(s), unit(s, count), Double::sum);
    }

    /** Daily drift of supply and demand back toward neutral. */
    public void relax() {
        relax(supply);
        relax(demand);
    }

    private static void relax(Map<String, Double> m) {
        m.replaceAll((k, v) -> v + (1.0 - v) * RELAX);
        m.values().removeIf(v -> Math.abs(v - 1.0) < 0.01);
    }

    /** All tradeable items, sorted by registry name, computed once per survey. */
    public List<ItemStack> catalog() {
        if (catalog != null) return catalog;
        List<ItemStack> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ItemStack s : RecipeIndex.allOutputs()) addIfPriced(list, seen, s);
        for (Item i : DEFAULTS.keySet()) addIfPriced(list, seen, new ItemStack(i));
        addSurveyed(list);
        list.removeIf(s -> s.getItem() == Items.EMERALD);
        list.sort(Comparator.comparing(RecipeIndex::key));
        catalog = list;
        return list;
    }

    /** Raw materials found in the survey are only known by key; rebuild their stacks lazily. */
    public void addSurveyed(List<ItemStack> list) {
        Set<String> seen = new HashSet<>();
        for (ItemStack s : list) seen.add(RecipeIndex.key(s));
        for (String k : basePrices.keySet()) {
            if (seen.contains(k)) continue;
            ItemStack s = fromKey(k);
            if (!s.isEmpty()) list.add(s);
        }
    }

    private void addIfPriced(List<ItemStack> list, Set<String> seen, ItemStack s) {
        String k = RecipeIndex.key(s);
        if (seen.add(k) && price(s) > 0) {
            ItemStack c = s.copy();
            c.setCount(1);
            list.add(c);
        }
    }

    public static ItemStack fromKey(String k) {
        int at = k.lastIndexOf('@');
        if (at < 0) return ItemStack.EMPTY;
        Item item = Item.getByNameOrId(k.substring(0, at));
        if (item == null) return ItemStack.EMPTY;
        try {
            return new ItemStack(item, 1, Integer.parseInt(k.substring(at + 1)));
        } catch (NumberFormatException e) {
            return ItemStack.EMPTY;
        }
    }

    // --- persistence --------------------------------------------------------------------------------

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound base = new NBTTagCompound();
        basePrices.forEach(base::setInteger);
        t.setTag("Base", base);
        NBTTagCompound sup = new NBTTagCompound();
        supply.forEach(sup::setDouble);
        t.setTag("Supply", sup);
        NBTTagCompound dem = new NBTTagCompound();
        demand.forEach(dem::setDouble);
        t.setTag("Demand", dem);
        t.setBoolean("Ready", ready);
        return t;
    }

    public void read(NBTTagCompound t) {
        NBTTagCompound base = t.getCompoundTag("Base");
        for (String k : base.getKeySet()) basePrices.put(k, base.getInteger(k));
        NBTTagCompound sup = t.getCompoundTag("Supply");
        for (String k : sup.getKeySet()) supply.put(k, sup.getDouble(k));
        NBTTagCompound dem = t.getCompoundTag("Demand");
        for (String k : dem.getKeySet()) demand.put(k, dem.getDouble(k));
        ready = t.getBoolean("Ready") && !basePrices.isEmpty();
    }
}
