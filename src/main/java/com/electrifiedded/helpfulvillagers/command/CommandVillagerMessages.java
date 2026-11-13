package com.electrifiedded.helpfulvillagers.command;

import com.electrifiedded.helpfulvillagers.Messages;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /villagermessages <death|birth|all> <off|on|verbose> — per-player announcement settings. */
public class CommandVillagerMessages extends CommandBase {

    @Override
    public String getName() {
        return "villagermessages";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("vmsg");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "helpfulvillagers.command.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender instanceof EntityPlayer;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2) throw new WrongUsageException(getUsage(sender));
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        int mode = Arrays.asList("off", "on", "verbose").indexOf(args[1]);
        if (mode < 0) throw new WrongUsageException(getUsage(sender));
        boolean death = args[0].equals("death") || args[0].equals("all");
        boolean birth = args[0].equals("birth") || args[0].equals("all");
        if (!death && !birth) throw new WrongUsageException(getUsage(sender));
        if (death) Messages.setMode(player, Messages.Kind.DEATH, mode);
        if (birth) Messages.setMode(player, Messages.Kind.BIRTH, mode);
        sender.sendMessage(new TextComponentTranslation("helpfulvillagers.command.set", args[0], args[1]));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "death", "birth", "all");
        if (args.length == 2) return getListOfStringsMatchingLastWord(args, "off", "on", "verbose");
        return Collections.emptyList();
    }
}
