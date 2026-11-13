package com.electrifiedded.helpfulvillagers.command;

import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** /hvdebug — prints what every helpful villager knows about its village, hall and task (operators only). */
public class CommandHVDebug extends CommandBase {

    @Override
    public String getName() {
        return "hvdebug";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/hvdebug";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        // RCON runs commands on its own thread: do the work on the server thread and wait for it
        try {
            String out = server.callFromMainThread(() -> report(server, args)).get();
            sender.sendMessage(new TextComponentString(out));
        } catch (Exception e) {
            sender.sendMessage(new TextComponentString("hvdebug failed: " + e));
        }
    }

    private static String report(MinecraftServer server, String[] args) {
        StringBuilder all = new StringBuilder();
        if (args.length > 0 && args[0].equals("keepalive")) {
            // dedicated servers stop ticking entities 300 ticks after the last player left; tests need them running
            for (net.minecraft.world.WorldServer w : server.worlds) w.resetUpdateEntityTick();
            return "ok";
        }
        if (args.length >= 4 && args[0].equals("order")) {
            // /hvdebug order <item> <count> <profession>  -> villager order for testing the craft chain
            net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(args[1]);
            com.electrifiedded.helpfulvillagers.entity.Profession prof = com.electrifiedded.helpfulvillagers.entity.Profession.valueOf(args[3].toUpperCase(java.util.Locale.ROOT));
            for (World world : server.worlds) {
                for (Entity e : world.loadedEntityList) {
                    if (e instanceof EntityHelpfulVillager && ((EntityHelpfulVillager) e).village() != null && item != null) {
                        ((EntityHelpfulVillager) e).village().addOrder(new com.electrifiedded.helpfulvillagers.craft.CraftOrder(
                                new net.minecraft.item.ItemStack(item, Integer.parseInt(args[2])), "debug", null, prof));
                        return "queued";
                    }
                }
            }
            return "no village";
        }
        for (World world : server.worlds) {
            java.util.Set<VillageRecord> seen = new java.util.HashSet<>();
            for (Entity e : world.loadedEntityList) {
                if (!(e instanceof EntityHelpfulVillager)) continue;
                EntityHelpfulVillager v = (EntityHelpfulVillager) e;
                VillageRecord rec = v.village();
                GuildHall hall = v.hall();
                StringBuilder sb = new StringBuilder();
                sb.append('#').append(v.getEntityId()).append(' ').append(v.getJob().name)
                        .append(" @").append(v.getPosition().toString())
                        .append(" tool=").append(v.hasTool())
                        .append(" village=").append(rec == null ? "none" : rec.center + " r" + rec.radius + " halls=" + rec.halls().size())
                        .append(" hall=").append(hall == null ? "none" : hall.door + " chests=" + hall.chests.size() + " inside=" + hall.inside.size())
                        .append(" leader=").append(v.hasLeader())
                        .append(" status=").append(v.job().status).append(" nav=").append(!v.getNavigator().noPath())
                        .append(" day=").append(world.isDaytime())
                        .append(" target=").append(v.getAttackTarget() == null ? "-" : v.getAttackTarget().getName() + (v.getAttackTarget().isEntityAlive() ? "" : "(dead)"))
                        .append(" craft=").append(v.craftTask != null)
                        .append(" vanillaVillages=").append(world.getVillageCollection().getVillageList().size());
                all.append(sb).append(" | ");
                if (rec != null && seen.add(rec)) {
                    all.append("QUEUE:");
                    for (com.electrifiedded.helpfulvillagers.craft.CraftOrder o : rec.queue) {
                        all.append(' ').append(o.item.getItem().getRegistryName()).append('x').append(o.item.getCount())
                                .append(o.missing.isEmpty() ? "" : " missing" + o.missing);
                    }
                    all.append(" eco=").append(rec.economy.isReady()).append(" | ");
                }
            }
        }
        return all.toString();
    }
}
