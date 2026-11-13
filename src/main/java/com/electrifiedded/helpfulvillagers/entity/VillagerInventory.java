package com.electrifiedded.helpfulvillagers.entity;

import com.electrifiedded.helpfulvillagers.village.InventoryUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;

import java.util.function.Predicate;

/**
 * 27 storage slots plus 5 equipment slots (tool, helmet, chest, legs, boots). The equipment slots are the
 * entity's real equipment, so held tools and armour render and sync like on any mob.
 */
public class VillagerInventory implements IInventory {

    public static final int MAIN = 27;
    public static final int TOOL = MAIN;
    public static final int SIZE = MAIN + 5;
    public static final EntityEquipmentSlot[] EQUIPMENT = {
            EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};

    private final EntityHelpfulVillager owner;
    private final NonNullList<ItemStack> main = NonNullList.withSize(MAIN, ItemStack.EMPTY);

    public VillagerInventory(EntityHelpfulVillager owner) {
        this.owner = owner;
    }

    public static EntityEquipmentSlot equipmentSlot(int index) {
        return index >= MAIN && index < SIZE ? EQUIPMENT[index - MAIN] : null;
    }

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return InventoryUtil.isEmpty(this, 0, SIZE);
    }

    public boolean isMainEmpty() {
        return InventoryUtil.isEmpty(this, 0, MAIN);
    }

    public boolean isMainFull() {
        for (ItemStack s : main) if (s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < MAIN) return main.get(index);
        EntityEquipmentSlot eq = equipmentSlot(index);
        return eq == null ? ItemStack.EMPTY : owner.getItemStackFromSlot(eq);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack s = getStackInSlot(index);
        if (s.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ItemStack out = s.splitStack(count);
        if (s.isEmpty()) setInventorySlotContents(index, ItemStack.EMPTY);
        markDirty();
        return out;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack s = getStackInSlot(index);
        setInventorySlotContents(index, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < MAIN) {
            main.set(index, stack);
        } else {
            EntityEquipmentSlot eq = equipmentSlot(index);
            if (eq != null) owner.setItemStackToSlot(eq, stack);
        }
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        owner.onInventoryChanged();
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return owner.isEntityAlive() && player.getDistanceSq(owner) < 64;
    }

    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        EntityEquipmentSlot eq = equipmentSlot(index);
        if (eq == null || eq == EntityEquipmentSlot.MAINHAND) return true;
        return stack.getItem().isValidArmor(stack, eq, owner);
    }

    @Override
    public int getField(int id) { return 0; }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount() { return 0; }

    @Override
    public void clear() {
        for (int i = 0; i < SIZE; i++) setInventorySlotContents(i, ItemStack.EMPTY);
    }

    @Override
    public String getName() {
        return "container.helpfulvillagers.villager";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Adds to the storage slots; returns what did not fit. */
    public ItemStack add(ItemStack stack) {
        return InventoryUtil.insert(this, stack, 0, MAIN);
    }

    /** Adds and drops the overflow at the villager's feet. */
    public void addOrDrop(ItemStack stack) {
        ItemStack rest = add(stack);
        if (!rest.isEmpty()) owner.entityDropItem(rest, 0.5F);
    }

    public int count(Predicate<ItemStack> filter) {
        return InventoryUtil.count(this, filter);
    }

    public int find(Predicate<ItemStack> filter) {
        for (int i = 0; i < MAIN; i++) {
            ItemStack s = main.get(i);
            if (!s.isEmpty() && filter.test(s)) return i;
        }
        return -1;
    }

    public ItemStack extract(Predicate<ItemStack> filter, int amount) {
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < MAIN && amount > 0; i++) {
            ItemStack s = main.get(i);
            if (s.isEmpty() || !filter.test(s)) continue;
            if (!out.isEmpty() && !InventoryUtil.canMerge(out, s)) continue;
            ItemStack part = s.splitStack(Math.min(amount, s.getCount()));
            if (out.isEmpty()) out = part;
            else out.grow(part.getCount());
            amount -= part.getCount();
        }
        markDirty();
        return out;
    }

    /** Puts a tool into the hand, moving whatever was there into storage. */
    public void equipTool(int slot) {
        ItemStack tool = main.get(slot);
        ItemStack old = owner.getHeldItemMainhand();
        main.set(slot, old);
        owner.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, tool);
        markDirty();
    }

    /** Wears armour from storage where the slot is empty or the new piece is better. */
    public void equipBestArmor() {
        for (int i = 0; i < MAIN; i++) {
            ItemStack s = main.get(i);
            if (!(s.getItem() instanceof ItemArmor)) continue;
            ItemArmor a = (ItemArmor) s.getItem();
            ItemStack worn = owner.getItemStackFromSlot(a.armorType);
            if (worn.isEmpty() || (worn.getItem() instanceof ItemArmor && ((ItemArmor) worn.getItem()).damageReduceAmount < a.damageReduceAmount)) {
                owner.setItemStackToSlot(a.armorType, s);
                main.set(i, worn);
            }
        }
        markDirty();
    }

    public void dropAll() {
        for (int i = 0; i < MAIN; i++) {
            ItemStack s = main.get(i);
            if (!s.isEmpty()) InventoryHelper.spawnItemStack(owner.world, owner.posX, owner.posY, owner.posZ, s);
            main.set(i, ItemStack.EMPTY);
        }
    }

    public NBTTagList write() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < MAIN; i++) {
            ItemStack s = main.get(i);
            if (s.isEmpty()) continue;
            NBTTagCompound t = s.writeToNBT(new NBTTagCompound());
            t.setByte("Slot", (byte) i);
            list.appendTag(t);
        }
        return list;
    }

    public void read(NBTTagList list) {
        for (int i = 0; i < MAIN; i++) main.set(i, ItemStack.EMPTY);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < MAIN) main.set(slot, new ItemStack(t));
        }
    }

    public static NBTTagList emptyList() {
        return new NBTTagList();
    }

    static int listType() {
        return Constants.NBT.TAG_COMPOUND;
    }
}
