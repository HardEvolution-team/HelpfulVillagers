package com.electrifiedded.helpfulvillagers.craft;

import com.electrifiedded.helpfulvillagers.village.InventoryUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Works out how to make an item from what is stored in the village chests. Intermediate items that are
 * not in stock are crafted or smelted recursively (bounded depth, cycle safe), and leftovers of a step
 * become stock for later steps.
 */
public final class CraftPlanner {

    private static final int MAX_DEPTH = 6;

    private final List<ItemStack> pool;
    private final CraftPlan plan = new CraftPlan();
    private final Set<String> inProgress = new HashSet<>();

    private CraftPlanner(List<ItemStack> stock) {
        pool = new ArrayList<>();
        for (ItemStack s : stock) if (!s.isEmpty()) pool.add(s.copy());
    }

    /** @param stock snapshot of everything in the village chests (not modified) */
    public static CraftPlan plan(ItemStack target, int amount, List<ItemStack> stock) {
        CraftPlanner p = new CraftPlanner(stock);
        if (!p.produce(target, amount, 0)) {
            // report raw shortfall based on the first recipe level, so the player sees what to bring
            CraftPlanner report = new CraftPlanner(stock);
            report.collectMissing(target, amount);
            p.plan.missing.clear();
            p.plan.missing.addAll(report.plan.missing.isEmpty() ? single(target, amount) : report.plan.missing);
        }
        return p.plan;
    }

    private static List<ItemStack> single(ItemStack s, int n) {
        List<ItemStack> l = new ArrayList<>();
        ItemStack c = s.copy();
        c.setCount(n);
        l.add(c);
        return l;
    }

    // --- snapshot handling for backtracking --------------------------------------------------------

    private static final class State {
        final List<ItemStack> pool;
        final int materials;
        final int steps;
        final int[] materialAmounts;

        State(CraftPlanner p) {
            pool = new ArrayList<>();
            for (ItemStack s : p.pool) pool.add(s.copy());
            materials = p.plan.materials.size();
            steps = p.plan.steps.size();
            materialAmounts = new int[materials];
            for (int i = 0; i < materials; i++) materialAmounts[i] = p.plan.materials.get(i).amount;
        }

        void restore(CraftPlanner p) {
            p.pool.clear();
            p.pool.addAll(pool);
            while (p.plan.materials.size() > materials) p.plan.materials.remove(p.plan.materials.size() - 1);
            while (p.plan.steps.size() > steps) p.plan.steps.remove(p.plan.steps.size() - 1);
            for (int i = 0; i < materials; i++) p.plan.materials.get(i).amount = materialAmounts[i];
        }
    }

    // --- planning -----------------------------------------------------------------------------------

    /** Take up to n items from stock; returns how many were taken. */
    private int take(Predicate<ItemStack> filter, ItemStack display, int n, boolean fromChests) {
        int got = 0;
        for (ItemStack s : pool) {
            if (got >= n) break;
            if (s.isEmpty() || !filter.test(s)) continue;
            int t = Math.min(n - got, s.getCount());
            s.shrink(t);
            got += t;
            if (fromChests) addMaterial(s.isEmpty() ? display : s, t);
        }
        pool.removeIf(ItemStack::isEmpty);
        return got;
    }

    private void addMaterial(ItemStack like, int amount) {
        for (CraftPlan.Material m : plan.materials) {
            if (InventoryUtil.same(m.display, like)) { m.amount += amount; return; }
        }
        ItemStack d = like.copy();
        d.setCount(1);
        plan.materials.add(new CraftPlan.Material(InventoryUtil.matching(d), d, amount));
    }

    /** Make {@code n} of {@code target} available (from stock or by crafting). */
    private boolean produce(ItemStack target, int n, int depth) {
        Predicate<ItemStack> filter = InventoryUtil.matching(target);
        n -= takeVirtual(filter, n);
        if (n <= 0) return true;
        n -= take(filter, target, n, true);
        if (n <= 0) return true;
        return make(target, n, depth);
    }

    /** Items produced earlier in this plan (surplus) live in the pool too, flagged by not being chest stock. */
    private final List<ItemStack> surplus = new ArrayList<>();

    private int takeVirtual(Predicate<ItemStack> filter, int n) {
        int got = 0;
        for (ItemStack s : surplus) {
            if (got >= n) break;
            if (s.isEmpty() || !filter.test(s)) continue;
            int t = Math.min(n - got, s.getCount());
            s.shrink(t);
            got += t;
        }
        surplus.removeIf(ItemStack::isEmpty);
        return got;
    }

    private boolean make(ItemStack target, int n, int depth) {
        if (depth >= MAX_DEPTH) return false;
        String key = RecipeIndex.key(target);
        if (!inProgress.add(key)) return false;
        try {
            for (IRecipe r : RecipeIndex.craftingFor(target)) {
                State st = new State(this);
                List<ItemStack> surplusBackup = copy(surplus);
                int per = Math.max(1, r.getRecipeOutput().getCount());
                int times = (n + per - 1) / per;
                if (provideIngredients(r.getIngredients(), times, depth)) {
                    plan.steps.add(new CraftPlan.Step(r, null, times));
                    addSurplus(r.getRecipeOutput(), times * per - n);
                    return true;
                }
                st.restore(this);
                surplus.clear();
                surplus.addAll(surplusBackup);
            }
            for (ItemStack[] sr : RecipeIndex.smeltingFor(target)) {
                State st = new State(this);
                List<ItemStack> surplusBackup = copy(surplus);
                int per = Math.max(1, sr[1].getCount());
                int times = (n + per - 1) / per;
                if (produce(sr[0], times, depth + 1)) {
                    ItemStack in = sr[0].copy();
                    in.setCount(1);
                    plan.steps.add(new CraftPlan.Step(null, in, times));
                    addSurplus(sr[1], times * per - n);
                    return true;
                }
                st.restore(this);
                surplus.clear();
                surplus.addAll(surplusBackup);
            }
            return false;
        } finally {
            inProgress.remove(key);
        }
    }

    private void addSurplus(ItemStack like, int amount) {
        if (amount <= 0) return;
        ItemStack s = like.copy();
        s.setCount(amount);
        surplus.add(s);
    }

    private static List<ItemStack> copy(List<ItemStack> l) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : l) out.add(s.copy());
        return out;
    }

    private boolean provideIngredients(List<Ingredient> ingredients, int times, int depth) {
        for (Ingredient ing : ingredients) {
            if (ing == Ingredient.EMPTY || ing.getMatchingStacks().length == 0) continue;
            if (!provide(ing, times, depth)) return false;
        }
        return true;
    }

    private boolean provide(Ingredient ing, int n, int depth) {
        Predicate<ItemStack> filter = InventoryUtil.matching(ing);
        n -= takeVirtual(filter, n);
        if (n <= 0) return true;
        ItemStack display = displayOf(ing);
        n -= take(filter, display, n, true);
        if (n <= 0) return true;
        for (ItemStack option : ing.getMatchingStacks()) {
            ItemStack o = option.copy();
            if (o.getMetadata() == OreDictionary.WILDCARD_VALUE) o.setItemDamage(0);
            State st = new State(this);
            List<ItemStack> surplusBackup = copy(surplus);
            if (make(o, n, depth + 1)) return true; // make() hands exactly n to us, the rest is surplus
            st.restore(this);
            surplus.clear();
            surplus.addAll(surplusBackup);
        }
        return false;
    }

    private static ItemStack displayOf(Ingredient ing) {
        ItemStack[] m = ing.getMatchingStacks();
        ItemStack d = m.length > 0 ? m[0].copy() : ItemStack.EMPTY;
        if (!d.isEmpty() && d.getMetadata() == OreDictionary.WILDCARD_VALUE) d.setItemDamage(0);
        return d;
    }

    // --- shortfall report ---------------------------------------------------------------------------

    private void collectMissing(ItemStack target, int n) {
        n -= take(InventoryUtil.matching(target), target, n, false);
        if (n <= 0) return;
        List<IRecipe> recipes = RecipeIndex.craftingFor(target);
        if (!recipes.isEmpty()) {
            IRecipe r = recipes.get(0);
            int per = Math.max(1, r.getRecipeOutput().getCount());
            int times = (n + per - 1) / per;
            for (Ingredient ing : r.getIngredients()) {
                if (ing == Ingredient.EMPTY || ing.getMatchingStacks().length == 0) continue;
                int lack = times - take(InventoryUtil.matching(ing), displayOf(ing), times, false);
                if (lack > 0) addMissing(displayOf(ing), lack);
            }
            return;
        }
        List<ItemStack[]> smelt = RecipeIndex.smeltingFor(target);
        if (!smelt.isEmpty()) {
            ItemStack in = smelt.get(0)[0];
            int lack = n - take(InventoryUtil.matching(in), in, n, false);
            if (lack > 0) addMissing(in, lack);
            return;
        }
        addMissing(target, n);
    }

    private void addMissing(ItemStack like, int n) {
        for (ItemStack m : plan.missing) {
            if (InventoryUtil.same(m, like)) { m.grow(n); return; }
        }
        ItemStack c = like.copy();
        c.setCount(n);
        plan.missing.add(c);
    }
}
