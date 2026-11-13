package com.electrifiedded.helpfulvillagers.client.gui;

import com.electrifiedded.helpfulvillagers.craft.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client copy of a merchant's price list and the player's credits, shared by both barter tabs. */
public final class BarterState {

    public static final List<ItemStack> catalog = new ArrayList<>();
    public static final Map<String, Integer> prices = new HashMap<>();
    public static int credits;
    public static int emeraldValue = 1;
    public static boolean ready;

    private BarterState() {}

    public static void load(NBTTagCompound t) {
        credits = t.getInteger("Credits");
        if (t.hasKey("EmeraldValue")) emeraldValue = Math.max(1, t.getInteger("EmeraldValue"));
        if (t.hasKey("Ready")) ready = t.getBoolean("Ready");
        if (t.hasKey("Prices")) {
            catalog.clear();
            prices.clear();
            NBTTagList l = t.getTagList("Prices", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound e = l.getCompoundTagAt(i);
                ItemStack s = new ItemStack(e);
                if (s.isEmpty()) continue;
                s.setCount(1);
                catalog.add(s);
                prices.put(RecipeIndex.key(s), e.getInteger("Price"));
            }
        }
        if (t.hasKey("Update")) {
            NBTTagCompound e = t.getCompoundTag("Update");
            ItemStack s = new ItemStack(e);
            if (!s.isEmpty()) prices.put(RecipeIndex.key(s), e.getInteger("Price"));
        }
    }

    public static int price(ItemStack s) {
        if (s.isEmpty()) return -1;
        return prices.getOrDefault(RecipeIndex.key(s), -1);
    }

    /** Draws the two tabs (buy, sell) above a 195-wide barter panel. */
    static void drawTabs(Gui gui, Minecraft mc, int left, int top, boolean buySelected, boolean selectedPass) {
        mc.getTextureManager().bindTexture(Tex.BARTER_TABS);
        GlStateManager.color(1F, 1F, 1F, 1F);
        for (int i = 0; i < 2; i++) {
            boolean sel = (i == 0) == buySelected;
            if (sel != selectedPass) continue;
            gui.drawTexturedModalRect(left + i * 29, top - 28, i * 28, sel ? 32 : 0, 28, 32);
        }
        if (selectedPass) {
            mc.getRenderItem().renderItemIntoGUI(new ItemStack(net.minecraft.init.Items.EMERALD), left + 6, top - 19);
            mc.getRenderItem().renderItemIntoGUI(new ItemStack(net.minecraft.init.Blocks.CHEST), left + 35, top - 19);
        }
    }

    static int tabAt(int mx, int my, int left, int top) {
        if (my < top - 28 || my >= top) return -1;
        if (mx >= left && mx < left + 28) return 0;
        if (mx >= left + 29 && mx < left + 57) return 1;
        return -1;
    }

    static String creditsText() {
        return I18n.format("helpfulvillagers.barter.credits", credits);
    }
}
