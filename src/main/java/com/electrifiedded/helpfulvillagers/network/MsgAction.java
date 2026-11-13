package com.electrifiedded.helpfulvillagers.network;

import com.electrifiedded.helpfulvillagers.HelpfulVillagers;
import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.craft.CraftOrder;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.GuardAssignments;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import com.electrifiedded.helpfulvillagers.gui.ContainerBarterSell;
import com.electrifiedded.helpfulvillagers.gui.ContainerTeach;
import com.electrifiedded.helpfulvillagers.gui.GuiHandler;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: a button pressed in one of the villager screens. Everything is validated here. */
public class MsgAction implements IMessage {

    public static final int FOLLOW = 0, STOP_FOLLOW = 1, INVENTORY = 2, PROFESSIONS = 3, SET_PROFESSION = 4,
            NICKNAME = 5, GUARD = 6, CRAFT_MENU = 7, QUEUE = 8, REMOVE_ORDER = 9, TEACH = 10, TEACH_RECIPE = 11,
            RESET_RECIPES = 12, BARTER_BUY = 13, BARTER_SELL = 14, BUY = 15, SELL = 16, WITHDRAW = 17, STATS = 18,
            DIALOG = 19;

    int entityId;
    int action;
    int arg;
    String text;
    ItemStack stack;

    public MsgAction() {}

    public MsgAction(int entityId, int action, int arg, String text, ItemStack stack) {
        this.entityId = entityId;
        this.action = action;
        this.arg = arg;
        this.text = text == null ? "" : text;
        this.stack = stack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        action = buf.readByte();
        arg = buf.readInt();
        text = ByteBufUtils.readUTF8String(buf);
        stack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(action);
        buf.writeInt(arg);
        ByteBufUtils.writeUTF8String(buf, text);
        ByteBufUtils.writeItemStack(buf, stack);
    }

    public static class Handler implements IMessageHandler<MsgAction, IMessage> {
        @Override
        public IMessage onMessage(MsgAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(msg, player));
            return null;
        }
    }

    private static void handle(MsgAction m, EntityPlayerMP player) {
        Entity e = player.world.getEntityByID(m.entityId);
        if (!(e instanceof EntityHelpfulVillager) || !e.isEntityAlive() || player.getDistanceSq(e) > 10 * 10) return;
        EntityHelpfulVillager v = (EntityHelpfulVillager) e;
        VillageRecord village = v.village();
        switch (m.action) {
            case DIALOG:
                Net.openDialog(player, v);
                break;
            case FOLLOW:
                v.setLeader(player);
                break;
            case STOP_FOLLOW:
                v.setLeader(null);
                break;
            case INVENTORY:
                player.openGui(HelpfulVillagers.instance, GuiHandler.INVENTORY, player.world, v.getEntityId(), 0, 0);
                break;
            case PROFESSIONS:
                Net.sendScreen(player, v, MsgScreen.PROFESSION, ScreenData.dialog(v));
                break;
            case SET_PROFESSION: {
                Profession p = Profession.byId(m.arg);
                if (p == Profession.VILLAGER || (village != null && village.unlocked().contains(p))) {
                    v.setJob(p);
                    player.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.new_job", v.getDisplayName()));
                } else {
                    player.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.locked"));
                }
                break;
            }
            case NICKNAME: {
                String name = m.text.trim();
                if (name.length() > 32) name = name.substring(0, 32);
                if (name.isEmpty()) {
                    v.setCustomNameTag("");
                    v.setAlwaysRenderNameTag(false);
                } else {
                    v.setCustomNameTag(name);
                }
                break;
            }
            case GUARD:
                if (v.getJob().isGuard()) {
                    GuardAssignments.begin(player, v);
                    v.setLeader(null);
                    player.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.pick_ward", v.getDisplayName()));
                }
                break;
            case CRAFT_MENU:
                Net.sendScreen(player, v, MsgScreen.CRAFT_MENU, ScreenData.craftMenu(v));
                break;
            case STATS:
                Net.sendScreen(player, v, MsgScreen.CRAFT_STATS, ScreenData.stats(v, player));
                break;
            case QUEUE: {
                ItemStack want = m.stack;
                if (village == null || want.isEmpty() || !v.canCraft(want)) break;
                want.setCount(Math.max(1, Math.min(want.getMaxStackSize(), m.arg)));
                village.addOrder(new CraftOrder(want, player.getName(), player.getUniqueID(), null));
                player.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.queued", want.getCount(), want.getTextComponent()));
                Net.sendScreen(player, v, MsgScreen.CRAFT_MENU, ScreenData.craftMenu(v));
                break;
            }
            case REMOVE_ORDER: {
                if (village == null || m.arg < 0 || m.arg >= village.queue.size()) break;
                CraftOrder o = village.queue.get(m.arg);
                boolean mine = o.player != null && o.player.equals(player.getUniqueID());
                if (mine || !o.byPlayer() || player.canUseCommand(2, "")) village.queue.remove(m.arg);
                Net.sendScreen(player, v, MsgScreen.CRAFT_STATS, ScreenData.stats(v, player));
                break;
            }
            case TEACH:
                player.openGui(HelpfulVillagers.instance, GuiHandler.TEACH, player.world, v.getEntityId(), 0, 0);
                break;
            case TEACH_RECIPE: {
                if (!(player.openContainer instanceof ContainerTeach)) break;
                ContainerTeach c = (ContainerTeach) player.openContainer;
                ItemStack result = CraftingManager.findMatchingResult(c.grid, player.world);
                String key;
                if (result.isEmpty()) key = "helpfulvillagers.teach.no_recipe";
                else if (v.canCraft(result)) key = "helpfulvillagers.teach.known";
                else {
                    ItemStack t = result.copy();
                    t.setCount(1);
                    v.taught.add(t);
                    key = "helpfulvillagers.teach.added";
                }
                net.minecraft.nbt.NBTTagCompound d = new net.minecraft.nbt.NBTTagCompound();
                d.setString("Key", key);
                Net.sendScreen(player, v, MsgScreen.TEACH_RESULT, d);
                break;
            }
            case RESET_RECIPES: {
                v.taught.clear();
                net.minecraft.nbt.NBTTagCompound d = new net.minecraft.nbt.NBTTagCompound();
                d.setString("Key", "helpfulvillagers.teach.reset");
                Net.sendScreen(player, v, MsgScreen.TEACH_RESULT, d);
                break;
            }
            case BARTER_BUY:
                if (v.getJob() != Profession.MERCHANT) break;
                if (player.openContainer != player.inventoryContainer) player.closeScreen();
                Net.sendScreen(player, v, MsgScreen.BARTER_BUY, ScreenData.barter(v, player));
                break;
            case BARTER_SELL:
                if (v.getJob() != Profession.MERCHANT) break;
                player.openGui(HelpfulVillagers.instance, GuiHandler.BARTER_SELL, player.world, v.getEntityId(), 0, 0);
                Net.sendScreen(player, v, MsgScreen.BARTER_DATA, ScreenData.barter(v, player));
                break;
            case BUY:
                buy(m, player, v, village);
                break;
            case SELL:
                sell(player, v, village);
                break;
            case WITHDRAW:
                withdraw(m, player, v, village);
                break;
            default:
                break;
        }
    }

    private static void buy(MsgAction m, EntityPlayerMP player, EntityHelpfulVillager v, VillageRecord village) {
        if (village == null || v.getJob() != Profession.MERCHANT || m.stack.isEmpty() || !village.economy.isReady()) return;
        ItemStack want = m.stack.copy();
        int count = Math.max(1, Math.min(want.getMaxStackSize() * 4, m.arg));
        want.setCount(1);
        int price = village.economy.price(want);
        if (price <= 0) return;
        long total = (long) price * count;
        if (total > Integer.MAX_VALUE || !village.withdraw(player.getUniqueID(), (int) total)) {
            player.sendMessage(new TextComponentTranslation("helpfulvillagers.barter.no_credits"));
            return;
        }
        village.economy.onBought(want, count);
        while (count > 0) {
            ItemStack give = want.copy();
            give.setCount(Math.min(count, want.getMaxStackSize()));
            count -= give.getCount();
            if (!player.inventory.addItemStackToInventory(give)) player.dropItem(give, false);
        }
        player.inventoryContainer.detectAndSendChanges();
        Net.sendScreen(player, v, MsgScreen.BARTER_DATA, ScreenData.account(v, player, want));
    }

    private static void sell(EntityPlayerMP player, EntityHelpfulVillager v, VillageRecord village) {
        if (village == null || !(player.openContainer instanceof ContainerBarterSell)) return;
        ContainerBarterSell c = (ContainerBarterSell) player.openContainer;
        ItemStack s = c.sellSlot.getStackInSlot(0);
        if (s.isEmpty()) return;
        if (s.getItem() == Items.EMERALD) {
            village.deposit(player.getUniqueID(), s.getCount() * ModConfig.emeraldValue);
            c.sellSlot.setInventorySlotContents(0, ItemStack.EMPTY);
        } else {
            int price = village.economy.isReady() ? village.economy.price(s) : -1;
            if (price <= 0) return;
            village.deposit(player.getUniqueID(), price * s.getCount());
            village.economy.onSold(s, s.getCount());
            ItemStack sold = s.copy();
            c.sellSlot.setInventorySlotContents(0, ItemStack.EMPTY);
            GuildHall hall = v.hall();
            if (ModConfig.merchantStoresSoldItems && hall != null) hall.store(player.world, sold);
        }
        c.detectAndSendChanges();
        Net.sendScreen(player, v, MsgScreen.BARTER_DATA, ScreenData.account(v, player, s));
    }

    private static void withdraw(MsgAction m, EntityPlayerMP player, EntityHelpfulVillager v, VillageRecord village) {
        if (village == null || !(player.openContainer instanceof ContainerBarterSell)) return;
        ContainerBarterSell c = (ContainerBarterSell) player.openContainer;
        ItemStack out = c.outputSlot.getStackInSlot(0);
        if (!out.isEmpty() && out.getItem() != Items.EMERALD) return;
        int room = 64 - out.getCount();
        int n = Math.max(1, Math.min(room, m.arg));
        int cost = n * ModConfig.emeraldValue;
        if (n <= 0 || !village.withdraw(player.getUniqueID(), cost)) return;
        if (out.isEmpty()) c.outputSlot.setInventorySlotContents(0, new ItemStack(Items.EMERALD, n));
        else out.grow(n);
        c.detectAndSendChanges();
        Net.sendScreen(player, v, MsgScreen.BARTER_DATA, ScreenData.account(v, player, ItemStack.EMPTY));
    }
}
