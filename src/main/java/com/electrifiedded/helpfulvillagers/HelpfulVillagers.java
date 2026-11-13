package com.electrifiedded.helpfulvillagers;

import com.electrifiedded.archaiclib.original.OriginalModAssets;
import com.electrifiedded.helpfulvillagers.command.CommandVillagerMessages;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.EntityVillagerBobber;
import com.electrifiedded.helpfulvillagers.gui.GuiHandler;
import com.electrifiedded.helpfulvillagers.network.Net;
import com.electrifiedded.helpfulvillagers.proxy.CommonProxy;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION,
        dependencies = "required-after:archaiclib@[1.4.0,)")
public class HelpfulVillagers {

    public static final String MODID = Reference.MOD_ID;
    public static final Logger LOG = LogManager.getLogger(Reference.MOD_NAME);
    /** Where players can get the original 1.7.10 jar the textures come from. */
    public static final String ORIGINAL_URL = "https://www.curseforge.com/minecraft/mc-mods/helpful-villagers";

    @Mod.Instance(MODID)
    public static HelpfulVillagers instance;

    @SidedProxy(clientSide = "com.electrifiedded.helpfulvillagers.proxy.ClientProxy",
            serverSide = "com.electrifiedded.helpfulvillagers.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        OriginalModAssets.require(MODID, "helpfulvillagers", "Helpful Villagers 1.3.1 for 1.7.10 (by WeaselNinja)",
                ORIGINAL_URL, "helpfulvillagers");
        Net.init();
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "villager"), EntityHelpfulVillager.class,
                MODID + ".villager", 0, this, 80, 3, true, 0x5E3A2B, 0x9C7B5B);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "bobber"), EntityVillagerBobber.class,
                MODID + ".bobber", 1, this, 64, 5, true);
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandVillagerMessages());
        event.registerServerCommand(new com.electrifiedded.helpfulvillagers.command.CommandHVDebug());
    }
}
