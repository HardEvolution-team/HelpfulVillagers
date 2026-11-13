package com.electrifiedded.helpfulvillagers.client.gui;

import net.minecraft.util.ResourceLocation;

/** Textures served from the player's copy of the original mod jar (see ArchaicLib OriginalModAssets). */
public final class Tex {
    private static final String BASE = "textures/gui/";

    public static final ResourceLocation DIALOG = rl("dialog_background.png");
    public static final ResourceLocation INVENTORY = rl("villager_trade.png");
    public static final ResourceLocation CRAFT_MENU = rl("craft_menu.png");
    public static final ResourceLocation CRAFT_DETAILS = rl("craft_details.png");
    public static final ResourceLocation BARTER_TABS = rl("barter_inventory/tabs.png");
    public static final ResourceLocation BARTER_SEARCH = rl("barter_inventory/tab_item_search.png");
    public static final ResourceLocation BARTER_INVENTORY = rl("barter_inventory/tab_inventory.png");
    public static final ResourceLocation CRAFTING_TABLE = new ResourceLocation("textures/gui/container/crafting_table.png");

    private static ResourceLocation rl(String path) {
        return new ResourceLocation("helpfulvillagers", BASE + path);
    }

    private Tex() {}
}
