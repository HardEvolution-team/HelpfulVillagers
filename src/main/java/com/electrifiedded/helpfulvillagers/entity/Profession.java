package com.electrifiedded.helpfulvillagers.entity;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemLead;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Jobs a Helpful Villager can have. A job is unlocked in a village by building its guild hall:
 * a closed room with a wooden door and an item frame next to that door showing the job's symbol.
 */
public enum Profession {
    VILLAGER("villager", null, s -> false, list()),
    LUMBERJACK("lumberjack", s -> s.getItem() instanceof ItemAxe, s -> s.getItem() instanceof ItemAxe,
            list(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE),
            Item.getItemFromBlock(Blocks.PLANKS), Item.getItemFromBlock(Blocks.CRAFTING_TABLE), Item.getItemFromBlock(Blocks.CHEST),
            Item.getItemFromBlock(Blocks.LADDER), Item.getItemFromBlock(Blocks.OAK_FENCE), Item.getItemFromBlock(Blocks.WOODEN_SLAB),
            Item.getItemFromBlock(Blocks.BOOKSHELF), Item.getItemFromBlock(Blocks.NOTEBLOCK), Item.getItemFromBlock(Blocks.OAK_STAIRS),
            Item.getItemFromBlock(Blocks.SPRUCE_STAIRS), Item.getItemFromBlock(Blocks.BIRCH_STAIRS), Item.getItemFromBlock(Blocks.JUNGLE_STAIRS),
            Item.getItemFromBlock(Blocks.ACACIA_STAIRS), Item.getItemFromBlock(Blocks.DARK_OAK_STAIRS),
            Item.getItemFromBlock(Blocks.WOODEN_PRESSURE_PLATE), Item.getItemFromBlock(Blocks.WOODEN_BUTTON),
            Item.getItemFromBlock(Blocks.TRAPDOOR), Item.getItemFromBlock(Blocks.OAK_FENCE_GATE), Item.getItemFromBlock(Blocks.JUKEBOX),
            Item.getItemFromBlock(Blocks.DAYLIGHT_DETECTOR), Item.getItemFromBlock(Blocks.TRIPWIRE_HOOK),
            Item.getItemFromBlock(Blocks.TRAPPED_CHEST), Items.STICK, Items.BOAT, Items.SIGN, Items.PAINTING, Items.OAK_DOOR,
            Items.WOODEN_AXE, Items.WOODEN_HOE, Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD, Items.BOW,
            Items.ARROW, Items.BOWL, Items.FISHING_ROD, Items.CARROT_ON_A_STICK, Items.ITEM_FRAME, Items.BED, Items.SHIELD),
    MINER("miner", s -> s.getItem() instanceof ItemPickaxe, s -> s.getItem() instanceof ItemPickaxe,
            list(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE),
            Item.getItemFromBlock(Blocks.FURNACE), Item.getItemFromBlock(Blocks.STONE_SLAB), Item.getItemFromBlock(Blocks.STONE),
            Item.getItemFromBlock(Blocks.STONEBRICK), Item.getItemFromBlock(Blocks.STONE_BRICK_STAIRS), Item.getItemFromBlock(Blocks.STONE_STAIRS),
            Item.getItemFromBlock(Blocks.STONE_BUTTON), Item.getItemFromBlock(Blocks.STONE_PRESSURE_PLATE),
            Item.getItemFromBlock(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE), Item.getItemFromBlock(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE),
            Item.getItemFromBlock(Blocks.LEVER), Item.getItemFromBlock(Blocks.REDSTONE_LAMP), Item.getItemFromBlock(Blocks.REDSTONE_TORCH),
            Item.getItemFromBlock(Blocks.RAIL), Item.getItemFromBlock(Blocks.GOLDEN_RAIL), Item.getItemFromBlock(Blocks.DETECTOR_RAIL),
            Item.getItemFromBlock(Blocks.ACTIVATOR_RAIL), Item.getItemFromBlock(Blocks.COAL_BLOCK), Item.getItemFromBlock(Blocks.IRON_BLOCK),
            Item.getItemFromBlock(Blocks.GOLD_BLOCK), Item.getItemFromBlock(Blocks.REDSTONE_BLOCK), Item.getItemFromBlock(Blocks.LAPIS_BLOCK),
            Item.getItemFromBlock(Blocks.EMERALD_BLOCK), Item.getItemFromBlock(Blocks.DIAMOND_BLOCK), Item.getItemFromBlock(Blocks.COBBLESTONE_WALL),
            Item.getItemFromBlock(Blocks.PISTON), Item.getItemFromBlock(Blocks.STICKY_PISTON), Item.getItemFromBlock(Blocks.DISPENSER),
            Item.getItemFromBlock(Blocks.DROPPER), Item.getItemFromBlock(Blocks.HOPPER), Item.getItemFromBlock(Blocks.IRON_BARS),
            Item.getItemFromBlock(Blocks.ENCHANTING_TABLE), Item.getItemFromBlock(Blocks.ANVIL), Item.getItemFromBlock(Blocks.TORCH),
            Items.IRON_DOOR, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_AXE,
            Items.IRON_HOE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_SWORD, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE,
            Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS, Items.GOLDEN_AXE, Items.GOLDEN_HOE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL,
            Items.GOLDEN_SWORD, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.DIAMOND_AXE, Items.DIAMOND_HOE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.STONE_AXE,
            Items.STONE_HOE, Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.FLINT_AND_STEEL, Items.SHEARS,
            Items.BUCKET, Items.CLOCK, Items.COMPASS, Items.REPEATER, Items.COMPARATOR, Items.MINECART, Items.FURNACE_MINECART,
            Items.CHEST_MINECART, Items.HOPPER_MINECART, Items.TNT_MINECART, Items.CAULDRON, Items.BREWING_STAND,
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_NUGGET, Items.GOLD_NUGGET),
    FARMER("farmer", s -> s.getItem() instanceof ItemHoe, s -> s.getItem() instanceof ItemHoe,
            list(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE),
            Item.getItemFromBlock(Blocks.LIT_PUMPKIN), Item.getItemFromBlock(Blocks.HAY_BLOCK), Item.getItemFromBlock(Blocks.MELON_BLOCK),
            Items.CAKE, Items.BREAD, Items.COOKIE, Items.MUSHROOM_STEW, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.SUGAR,
            Items.PUMPKIN_PIE, Items.PAPER, Items.BOOK, Items.BAKED_POTATO, Items.BEETROOT_SOUP, Items.WHEAT),
    SOLDIER("soldier", s -> s.getItem() instanceof ItemSword, s -> s.getItem() instanceof ItemSword,
            list(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD)),
    ARCHER("archer", s -> s.getItem() instanceof ItemBow, s -> s.getItem() instanceof ItemBow, list(Items.BOW)),
    MERCHANT("merchant", s -> s.getItem() == Items.EMERALD, s -> false, list()),
    FISHERMAN("fisherman", s -> s.getItem() instanceof ItemFishingRod, s -> s.getItem() instanceof ItemFishingRod,
            list(Items.FISHING_ROD), Items.COOKED_FISH),
    RANCHER("rancher", s -> s.getItem() instanceof ItemLead, s -> s.getItem() instanceof ItemLead, list(Items.LEAD),
            Items.LEATHER_BOOTS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET, Items.LEATHER_LEGGINGS, Items.BOOK,
            Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_RABBIT,
            Item.getItemFromBlock(Blocks.WOOL), Items.LEAD, Items.SADDLE);

    private static final Profession[] VALUES = values();

    public final String name;
    private final Predicate<ItemStack> hallSymbol;
    private final Predicate<ItemStack> tool;
    /** Tools this job can use, cheapest first. */
    public final List<Item> tools;
    /** Items this job knows how to craft or smelt without being taught. */
    public final List<Item> craftables;

    Profession(String name, Predicate<ItemStack> hallSymbol, Predicate<ItemStack> tool, List<Item> tools, Item... craftables) {
        this.name = name;
        this.hallSymbol = hallSymbol;
        this.tool = tool;
        this.tools = tools;
        this.craftables = Collections.unmodifiableList(Arrays.asList(craftables));
    }

    private static List<Item> list(Item... items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    public static Profession byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : VILLAGER;
    }

    /** Which guild hall an item frame showing {@code stack} marks, or null. */
    public static Profession forHallSymbol(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Profession p : VALUES) if (p.hallSymbol != null && p.hallSymbol.test(stack)) return p;
        return null;
    }

    public boolean needsHall() {
        return this != VILLAGER;
    }

    public boolean needsTool() {
        return !tools.isEmpty();
    }

    public boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && tool.test(stack);
    }

    public boolean isGuard() {
        return this == SOLDIER || this == ARCHER;
    }

    public boolean isGatherer() {
        return this == LUMBERJACK || this == MINER || this == FARMER || this == FISHERMAN || this == RANCHER;
    }

    public boolean canCraft() {
        return !craftables.isEmpty();
    }

    public String translationKey() {
        return "helpfulvillagers.profession." + name;
    }
}
