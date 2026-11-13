package com.electrifiedded.helpfulvillagers.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Merchant "sell" tab: one slot for the item to sell (emeralds are deposited as credits), one slot that
 * receives withdrawn emeralds, the player's armour and inventory.
 */
public class ContainerBarterSell extends Container {

    public final EntityHelpfulVillager villager;
    public final InventoryBasic sellSlot = new InventoryBasic("sell", false, 1);
    public final InventoryBasic outputSlot = new InventoryBasic("output", false, 1);

    public ContainerBarterSell(InventoryPlayer playerInv, EntityHelpfulVillager villager) {
        this.villager = villager;
        addSlotToContainer(new Slot(sellSlot, 0, 99, 12));
        addSlotToContainer(new Slot(outputSlot, 0, 153, 12) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }
        });
        EntityEquipmentSlot[] armor = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
        int[][] pos = {{9, 6}, {9, 33}, {63, 6}, {63, 33}};
        for (int i = 0; i < 4; i++) {
            final EntityEquipmentSlot type = armor[i];
            final EntityPlayer player = playerInv.player;
            addSlotToContainer(new Slot(playerInv, 39 - i, pos[i][0], pos[i][1]) {
                @Override
                public int getSlotStackLimit() {
                    return 1;
                }

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return stack.getItem().isValidArmor(stack, type, player);
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(playerInv, c + r * 9 + 9, 9 + c * 18, 54 + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(playerInv, c, 9 + c * 18, 112));
    }

    public boolean holdsEmeralds() {
        return sellSlot.getStackInSlot(0).getItem() == Items.EMERALD;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return villager.isEntityAlive() && player.getDistanceSq(villager) < 64;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!player.world.isRemote) {
            clearContainer(player, player.world, sellSlot);
            clearContainer(player, player.world, outputSlot);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < 6) {
            if (!mergeItemStack(stack, 6, inventorySlots.size(), true)) return ItemStack.EMPTY;
        } else if (!mergeItemStack(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        return copy;
    }
}
