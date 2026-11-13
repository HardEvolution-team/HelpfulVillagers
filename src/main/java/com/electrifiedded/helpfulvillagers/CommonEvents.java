package com.electrifiedded.helpfulvillagers;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.VillageManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class CommonEvents {

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        Entity e = event.getEntity();
        if (ModConfig.replaceVanillaVillagers && e.getClass() == EntityVillager.class) {
            EntityVillager old = (EntityVillager) e;
            EntityHelpfulVillager v = new EntityHelpfulVillager(world);
            v.copyFromVanilla(old);
            event.setCanceled(true);
            // spawning from inside the join event is not allowed while chunks load; defer one tick
            world.getMinecraftServer().addScheduledTask(() -> world.spawnEntity(v));
        } else if (e instanceof EntityItemFrame) {
            VillageManager.get(world).requestScanNear(((EntityItemFrame) e).getHangingPosition());
        }
    }

    /** A frame got a new item, or doors/chests changed: halls need a fresh look. */
    @SubscribeEvent
    public void onFrameUse(PlayerInteractEvent.EntityInteract event) {
        if (!event.getWorld().isRemote && event.getTarget() instanceof EntityItemFrame) {
            VillageManager.get(event.getWorld()).requestScanNear(event.getTarget().getPosition());
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (!event.getWorld().isRemote) VillageManager.get(event.getWorld()).requestScanNear(event.getPos());
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getWorld().isRemote) VillageManager.get(event.getWorld()).requestScanNear(event.getPos());
    }

    private final java.util.Map<Integer, Boolean> wasDay = new java.util.HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        World world = event.world;
        VillageManager vm = VillageManager.get(world);
        vm.tick(world);
        boolean day = world.isDaytime();
        int dim = world.provider.getDimension();
        Boolean before = wasDay.put(dim, day);
        if (before != null && day && !before) vm.onNewDay();
    }
}
