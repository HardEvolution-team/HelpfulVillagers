package com.electrifiedded.helpfulvillagers.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.VillagerInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** The villager's bag (3x9), its tool and armour slots, and the player's inventory. */
public class ContainerVillagerInventory extends Container {

    public final EntityHelpfulVillager villager;
    private static final int VILLAGER_SLOTS = VillagerInventory.SIZE;

    public ContainerVillagerInventory(InventoryPlayer playerInv, EntityHelpfulVillager villager) {
        this.villager = villager;
        VillagerInventory inv = villager.inventory;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, c + r * 9, 8 + c * 18, 9 + r * 18));
        }
        for (int i = 0; i < 5; i++) {
            final int index = VillagerInventory.MAIN + i;
            addSlotToContainer(new Slot(inv, index, 43 + i * 18, 68) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return inventory.isItemValidForSlot(index, stack);
                }

                @Override
                public int getSlotStackLimit() {
                    return index == VillagerInventory.TOOL ? 64 : 1;
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 93 + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(playerInv, c, 8 + c * 18, 151));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return villager.inventory.isUsableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < VILLAGER_SLOTS) {
            if (!mergeItemStack(stack, VILLAGER_SLOTS, inventorySlots.size(), true)) return ItemStack.EMPTY;
        } else {
            // tools and armour go into their equipment slot first
            boolean moved = false;
            for (int i = VillagerInventory.MAIN; i < VILLAGER_SLOTS && !moved; i++) {
                Slot eq = inventorySlots.get(i);
                if (!eq.getHasStack() && eq.isItemValid(stack) && (i != VillagerInventory.TOOL || villager.getJob().isTool(stack))) {
                    moved = mergeItemStack(stack, i, i + 1, false);
                }
            }
            if (!moved && !mergeItemStack(stack, 0, VillagerInventory.MAIN, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        return copy;
    }
}
