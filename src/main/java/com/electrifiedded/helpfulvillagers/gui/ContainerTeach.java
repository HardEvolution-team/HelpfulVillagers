package com.electrifiedded.helpfulvillagers.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.World;

/**
 * "Teach Recipe": the player lays out a recipe on a 3x3 grid; the result is only a preview. Pressing
 * Teach lets the villager remember that item. Ingredients go back to the player when closing.
 */
public class ContainerTeach extends Container {

    public final EntityHelpfulVillager villager;
    public final InventoryCrafting grid = new InventoryCrafting(this, 3, 3);
    private final InventoryCraftResult result = new InventoryCraftResult();
    private final World world;

    public ContainerTeach(InventoryPlayer playerInv, EntityHelpfulVillager villager) {
        this.villager = villager;
        this.world = playerInv.player.world;
        addSlotToContainer(new Slot(result, 0, 124, 35) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }

            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return false;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) addSlotToContainer(new Slot(grid, c + r * 3, 30 + c * 18, 17 + r * 18));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(playerInv, c, 8 + c * 18, 142));
    }

    @Override
    public void onCraftMatrixChanged(IInventory inv) {
        result.setInventorySlotContents(0, CraftingManager.findMatchingResult(grid, world));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return villager.isEntityAlive() && player.getDistanceSq(villager) < 64;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!world.isRemote) clearContainer(player, world, grid);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack() || index == 0) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < 10) {
            if (!mergeItemStack(stack, 10, 46, true)) return ItemStack.EMPTY;
        } else if (!mergeItemStack(stack, 1, 10, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        return copy;
    }
}
