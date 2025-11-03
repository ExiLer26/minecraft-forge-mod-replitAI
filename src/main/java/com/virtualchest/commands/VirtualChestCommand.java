package com.virtualchest.commands;

import com.virtualchest.VirtualChestMod;
import com.virtualchest.config.ConfigManager;
import com.virtualchest.storage.ChestStorage;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VirtualChestCommand extends CommandBase {

    private final VirtualChestMod mod;

    public VirtualChestCommand(VirtualChestMod mod) {
        this.mod = mod;
    }

    @Override
    public String getName() {
        return "pv";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/pv [number|reload|<player> <number>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return -1; // No permission required at all
    }
    
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true; // Always allow execution
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "This command can only be executed by a player!"));
            return;
        }

        EntityPlayer player = (EntityPlayer) sender;

        // Handle reload command
        if (args.length == 1 && "reload".equals(args[0])) {
            executeReload(player);
            return;
        }

        // Handle view other player's chest: /pv <player> <number>
        if (args.length == 2) {
            executeViewOthersChest(player, args[0], args[1]);
            return;
        }

        // Handle normal chest access: /pv [number]
        int chestNumber = 1;
        if (args.length == 1) {
            try {
                chestNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid chest number: " + args[0]));
                return;
            }
        }

        // Check if the chest number is within allowed range
        ConfigManager config = mod.getConfigManager();
        int maxChests = config.getMaxChests();

        if (chestNumber < 1 || chestNumber > maxChests) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Chest number must be between 1 and " + maxChests + "!"));
            return;
        }

        // Check permissions for this chest number
        if (!checkPermission(player, chestNumber)) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "You don't have permission to access chest #" + chestNumber + "!"));
            return;
        }

        try {
            // Open the virtual chest
            ChestStorage chestStorage = mod.getChestStorage();
            chestStorage.openChest(player, chestNumber);

            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Opening virtual chest #" + chestNumber));
        } catch (Exception e) {
            VirtualChestMod.getLogger().error("Error opening virtual chest for player " + player.getName(), e);
            player.sendMessage(new TextComponentString(TextFormatting.RED + "An error occurred while opening the virtual chest: " + e.getMessage()));
        }
    }

    /**
     * Execute the reload subcommand
     * @param player The player
     */
    private void executeReload(EntityPlayer player) {
        // Check permission (OP level 2 or higher)
        if (!player.canUseCommand(2, "pv.reload")) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "You don't have permission to reload the plugin!"));
            return;
        }

        try {
            // Save current data
            mod.getChestStorage().saveAllData();

            // Reload configuration
            mod.getConfigManager().loadConfig();

            // Reload storage
            mod.getChestStorage().reloadStorage();

            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Virtual Chest mod reloaded successfully."));
        } catch (Exception e) {
            VirtualChestMod.getLogger().error("Error reloading mod", e);
            player.sendMessage(new TextComponentString(TextFormatting.RED + "An error occurred while reloading the mod: " + e.getMessage()));
        }
    }

    /**
     * Execute the view other player's chest command
     * @param player The player executing the command
     * @param targetPlayerName The target player name
     * @param chestNumberStr The chest number as string
     */
    private void executeViewOthersChest(EntityPlayer player, String targetPlayerName, String chestNumberStr) {
        // Check permission (OP level 2 or higher)
        if (!player.canUseCommand(2, "pv.admin")) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "You don't have permission to view other player's chests!"));
            return;
        }

        int chestNumber;
        try {
            chestNumber = Integer.parseInt(chestNumberStr);
        } catch (NumberFormatException e) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid chest number: " + chestNumberStr));
            return;
        }

        // Check if the chest number is within allowed range
        ConfigManager config = mod.getConfigManager();
        int maxChests = config.getMaxChests();

        if (chestNumber < 1 || chestNumber > maxChests) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Chest number must be between 1 and " + maxChests + "!"));
            return;
        }

        // Try to find the target player
        EntityPlayerMP targetPlayer = player.getServer().getPlayerList().getPlayerByUsername(targetPlayerName);
        UUID targetUUID;

        if (targetPlayer != null) {
            // Player is online
            targetUUID = targetPlayer.getUniqueID();
        } else {
            // Try to find the offline player
            try {
                targetUUID = player.getServer().getPlayerProfileCache().getGameProfileForUsername(targetPlayerName).getId();
                if (targetUUID == null) {
                    player.sendMessage(new TextComponentString(TextFormatting.RED + "Player not found: " + targetPlayerName));
                    return;
                }
            } catch (Exception e) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Player not found: " + targetPlayerName));
                return;
            }
        }

        try {
            // Open the target player's virtual chest
            ChestStorage chestStorage = mod.getChestStorage();
            chestStorage.openOtherPlayerChest(player, targetUUID, targetPlayerName, chestNumber);

            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Opening " + targetPlayerName + "'s virtual chest #" + chestNumber));
        } catch (Exception e) {
            VirtualChestMod.getLogger().error("Error opening other player's virtual chest", e);
            player.sendMessage(new TextComponentString(TextFormatting.RED + "An error occurred while opening the virtual chest: " + e.getMessage()));
        }
    }

    /**
     * Check if a player has permission to access a specific chest number
     * @param player The player
     * @param chestNumber The chest number
     * @return True if the player has permission
     */
    private boolean checkPermission(EntityPlayer player, int chestNumber) {
        // All players can access their own chests
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: number, reload, or player name
            completions.add("reload");
            for (int i = 1; i <= 10; i++) {
                completions.add(String.valueOf(i));
            }
            
            // Add online player names for admin users
            if (sender instanceof EntityPlayer && ((EntityPlayer) sender).canUseCommand(2, "pv.admin")) {
                for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Second argument: chest number when first arg is player name
            for (int i = 1; i <= 10; i++) {
                completions.add(String.valueOf(i));
            }
        }

        return getListOfStringsMatchingLastWord(args, completions);
    }
}
