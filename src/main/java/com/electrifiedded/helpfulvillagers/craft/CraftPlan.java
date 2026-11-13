package com.electrifiedded.helpfulvillagers.craft;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Result of {@link CraftPlanner}: which stored items to collect and which craft/smelt steps to run, in order. */
public class CraftPlan {

    /** A material to pull out of the village chests. */
    public static class Material {
        public final Predicate<ItemStack> filter;
        public final ItemStack display;
        public int amount;

        Material(Predicate<ItemStack> filter, ItemStack display, int amount) {
            this.filter = filter;
            this.display = display;
            this.amount = amount;
        }
    }

    public static class Step {
        /** crafting recipe, or null for a smelting step */
        public final IRecipe recipe;
        public final ItemStack smeltInput;
        public final int times;

        Step(IRecipe recipe, ItemStack smeltInput, int times) {
            this.recipe = recipe;
            this.smeltInput = smeltInput;
            this.times = times;
        }

        public boolean isSmelting() {
            return recipe == null;
        }

        public ItemStack output() {
            if (recipe != null) {
                ItemStack o = recipe.getRecipeOutput().copy();
                o.setCount(o.getCount() * times);
                return o;
            }
            ItemStack o = net.minecraft.item.crafting.FurnaceRecipes.instance().getSmeltingResult(smeltInput).copy();
            o.setCount(o.getCount() * times);
            return o;
        }

        public List<Ingredient> ingredients() {
            List<Ingredient> list = new ArrayList<>();
            if (recipe != null) {
                for (Ingredient i : recipe.getIngredients()) if (i != Ingredient.EMPTY && i.getMatchingStacks().length > 0) list.add(i);
            } else {
                list.add(Ingredient.fromStacks(smeltInput));
            }
            return list;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            if (recipe != null && recipe.getRegistryName() != null) t.setString("Recipe", recipe.getRegistryName().toString());
            if (smeltInput != null) t.setTag("Smelt", smeltInput.writeToNBT(new NBTTagCompound()));
            t.setInteger("Times", times);
            return t;
        }

        static Step read(NBTTagCompound t) {
            int times = t.getInteger("Times");
            if (t.hasKey("Recipe")) {
                IRecipe r = ForgeRegistries.RECIPES.getValue(new ResourceLocation(t.getString("Recipe")));
                return r == null ? null : new Step(r, null, times);
            }
            ItemStack in = new ItemStack(t.getCompoundTag("Smelt"));
            return in.isEmpty() ? null : new Step(null, in, times);
        }
    }

    public final List<Material> materials = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    /** filled when planning failed: what is lacking */
    public final List<ItemStack> missing = new ArrayList<>();

    public boolean ok() {
        return missing.isEmpty();
    }

    public NBTTagList writeSteps() {
        NBTTagList l = new NBTTagList();
        for (Step s : steps) l.appendTag(s.write());
        return l;
    }

    public static List<Step> readSteps(NBTTagList l) {
        List<Step> out = new ArrayList<>();
        for (int i = 0; i < l.tagCount(); i++) {
            Step s = Step.read(l.getCompoundTagAt(i));
            if (s != null) out.add(s);
        }
        return out;
    }
}
