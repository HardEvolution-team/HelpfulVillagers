package com.electrifiedded.helpfulvillagers.gui;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/** Container screens. x = villager entity id. */
public class GuiHandler implements IGuiHandler {

    public static final int INVENTORY = 0, TEACH = 1, BARTER_SELL = 2;

    private static EntityHelpfulVillager villager(World world, int id, EntityPlayer player) {
        Entity e = world.getEntityByID(id);
        if (!(e instanceof EntityHelpfulVillager) || player.getDistanceSq(e) > 100) return null;
        return (EntityHelpfulVillager) e;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        EntityHelpfulVillager v = villager(world, x, player);
        if (v == null) return null;
        switch (id) {
            case INVENTORY: return new ContainerVillagerInventory(player.inventory, v);
            case TEACH: return new ContainerTeach(player.inventory, v);
            case BARTER_SELL: return v.getJob() == Profession.MERCHANT ? new ContainerBarterSell(player.inventory, v) : null;
            default: return null;
        }
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        EntityHelpfulVillager v = villager(world, x, player);
        if (v == null) return null;
        switch (id) {
            case INVENTORY: return new com.electrifiedded.helpfulvillagers.client.gui.GuiVillagerInventory(new ContainerVillagerInventory(player.inventory, v));
            case TEACH: return new com.electrifiedded.helpfulvillagers.client.gui.GuiTeach(new ContainerTeach(player.inventory, v));
            case BARTER_SELL: return new com.electrifiedded.helpfulvillagers.client.gui.GuiBarterSell(new ContainerBarterSell(player.inventory, v));
            default: return null;
        }
    }
}
