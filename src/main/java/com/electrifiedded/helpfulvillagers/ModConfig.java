package com.electrifiedded.helpfulvillagers;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = HelpfulVillagers.MODID)
@Mod.EventBusSubscriber(modid = HelpfulVillagers.MODID)
public final class ModConfig {

    @Config.Comment("Replace every vanilla villager that spawns with a Helpful Villager (vanilla trading is lost, like in the original mod).")
    public static boolean replaceVanillaVillagers = true;

    @Config.Comment("Archers shoot without consuming arrows.")
    public static boolean infiniteArrows = false;

    @Config.Comment("Default death message mode for players who never used /villagermessages: 0 off, 1 on, 2 verbose (with coordinates).")
    @Config.RangeInt(min = 0, max = 2)
    public static int defaultDeathMessages = 1;

    @Config.Comment("Default birth message mode: 0 off, 1 on, 2 verbose (with coordinates).")
    @Config.RangeInt(min = 0, max = 2)
    public static int defaultBirthMessages = 1;

    @Config.Comment("How many credits one emerald is worth at a Merchant.")
    @Config.RangeInt(min = 1, max = 10000)
    public static int emeraldValue = 1;

    @Config.Comment("Ranchers keep at most this many adult animals of one kind in their pasture; extra adults are slaughtered.")
    @Config.RangeInt(min = 2, max = 64)
    public static int maxHerdPerSpecies = 6;

    @Config.Comment("Miners dig their shafts down to this Y level.")
    @Config.RangeInt(min = 5, max = 60)
    public static int minerTargetY = 12;

    @Config.Comment("Items sold to a Merchant are put into the Merchant guild hall chests when there is room.")
    public static boolean merchantStoresSoldItems = true;

    @SubscribeEvent
    public static void onChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if (HelpfulVillagers.MODID.equals(e.getModID())) ConfigManager.sync(HelpfulVillagers.MODID, Config.Type.INSTANCE);
    }

    private ModConfig() {}
}
