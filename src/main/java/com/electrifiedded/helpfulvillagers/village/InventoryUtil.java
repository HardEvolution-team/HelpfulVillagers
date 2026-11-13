package com.electrifiedded.helpfulvillagers.village;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import java.util.function.Predicate;

/** Small helpers for moving stacks between plain IInventories. */
public final class InventoryUtil {

    private InventoryUtil() {}

    /** Inserts into an inventory (merging first, then empty slots). Returns what did not fit. */
    public static ItemStack insert(IInventory inv, ItemStack stack) {
        return insert(inv, stack, 0, inv.getSizeInventory());
    }

    public static ItemStack insert(IInventory inv, ItemStack stack, int from, int to) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        stack = stack.copy();
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            ItemStack cur = inv.getStackInSlot(i);
            if (!cur.isEmpty() && canMerge(cur, stack)) {
                int limit = Math.min(cur.getMaxStackSize(), inv.getInventoryStackLimit());
                int move = Math.min(limit - cur.getCount(), stack.getCount());
                if (move > 0) {
                    cur.grow(move);
                    stack.shrink(move);
                    inv.markDirty();
                }
            }
        }
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            if (inv.getStackInSlot(i).isEmpty() && inv.isItemValidForSlot(i, stack)) {
                int move = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit()));
                inv.setInventorySlotContents(i, stack.splitStack(move));
            }
        }
        return stack;
    }

    public static boolean canMerge(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata() && ItemStack.areItemStackTagsEqual(a, b)
                && a.isStackable();
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.getItem() == b.getItem()
                && (!a.getHasSubtypes() || a.getMetadata() == b.getMetadata());
    }

    public static int count(IInventory inv, Predicate<ItemStack> filter) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && filter.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Removes up to {@code amount} matching items and returns them merged into one stack (first match wins the type). */
    public static ItemStack extract(IInventory inv, Predicate<ItemStack> filter, int amount) {
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSizeInventory() && amount > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !filter.test(s)) continue;
            if (!out.isEmpty() && !canMerge(out, s)) continue;
            int take = Math.min(amount, s.getCount());
            ItemStack part = inv.decrStackSize(i, take);
            if (out.isEmpty()) out = part;
            else out.grow(part.getCount());
            amount -= part.getCount();
        }
        return out;
    }

    public static Predicate<ItemStack> matching(ItemStack like) {
        return s -> same(s, like);
    }

    public static Predicate<ItemStack> matching(Ingredient ing) {
        return ing::apply;
    }

    public static boolean isEmpty(IInventory inv, int from, int to) {
        for (int i = from; i < to; i++) if (!inv.getStackInSlot(i).isEmpty()) return false;
        return true;
    }
}
