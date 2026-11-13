package com.electrifiedded.helpfulvillagers.craft;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lookup of crafting and smelting recipes by output item, built lazily once recipes are final. */
public final class RecipeIndex {

    private static Map<Item, List<IRecipe>> crafting;
    private static Map<Item, List<ItemStack[]>> smelting; // [input, output]
    private static Map<String, ItemStack> outputs;

    private RecipeIndex() {}

    public static synchronized void invalidate() {
        crafting = null;
        smelting = null;
        outputs = null;
    }

    private static synchronized void build() {
        if (crafting != null) return;
        Map<Item, List<IRecipe>> c = new HashMap<>();
        Map<Item, List<ItemStack[]>> s = new HashMap<>();
        Map<String, ItemStack> o = new LinkedHashMap<>();
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            ItemStack out = r.getRecipeOutput();
            if (out.isEmpty() || r.isDynamic() || r.getIngredients().isEmpty()) continue;
            c.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
            o.putIfAbsent(key(out), single(out));
        }
        for (Map.Entry<ItemStack, ItemStack> e : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            ItemStack in = e.getKey(), out = e.getValue();
            if (in.isEmpty() || out.isEmpty()) continue;
            if (in.getMetadata() == OreDictionary.WILDCARD_VALUE) in = new ItemStack(in.getItem(), 1, 0);
            s.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(new ItemStack[]{in.copy(), out.copy()});
            o.putIfAbsent(key(out), single(out));
        }
        crafting = c;
        smelting = s;
        outputs = o;
    }

    private static ItemStack single(ItemStack s) {
        ItemStack c = s.copy();
        c.setCount(1);
        return c;
    }

    public static List<IRecipe> craftingFor(ItemStack out) {
        build();
        List<IRecipe> all = crafting.getOrDefault(out.getItem(), Collections.emptyList());
        if (!out.getHasSubtypes()) return all;
        List<IRecipe> match = new ArrayList<>();
        for (IRecipe r : all) if (r.getRecipeOutput().getMetadata() == out.getMetadata()) match.add(r);
        return match;
    }

    /** Smelting recipes producing {@code out}: pairs [input, output]. */
    public static List<ItemStack[]> smeltingFor(ItemStack out) {
        build();
        List<ItemStack[]> all = smelting.getOrDefault(out.getItem(), Collections.emptyList());
        if (!out.getHasSubtypes()) return all;
        List<ItemStack[]> match = new ArrayList<>();
        for (ItemStack[] r : all) if (r[1].getMetadata() == out.getMetadata()) match.add(r);
        return match;
    }

    /** Every distinct crafting/smelting output stack (count 1). */
    public static Collection<ItemStack> allOutputs() {
        build();
        return outputs.values();
    }

    /** Output variants of one item, e.g. all plank colours. */
    public static List<ItemStack> outputsOf(Item item) {
        build();
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : outputs.values()) if (s.getItem() == item) list.add(s);
        return list;
    }

    public static String key(ItemStack s) {
        ResourceLocation id = s.getItem().getRegistryName();
        return (id == null ? "?" : id.toString()) + "@" + (s.getHasSubtypes() ? s.getMetadata() : 0);
    }
}
