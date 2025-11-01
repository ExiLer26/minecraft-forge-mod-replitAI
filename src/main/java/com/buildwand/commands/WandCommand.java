package com.buildwand.commands;

import com.buildwand.BuildWandPlugin;
import com.buildwand.utils.AreaSelection;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class WandCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("wand")
                .executes(WandCommand::executeWandCommand)
                .then(Commands.literal("undo").executes(WandCommand::executeUndo))
                .then(Commands.literal("clear").executes(WandCommand::executeClearSelection))
                .then(Commands.literal("copy").executes(WandCommand::executeCopy))
                .then(Commands.literal("paste").executes(WandCommand::executePaste))
                .then(Commands.literal("help").executes(WandCommand::showHelp))
        );
    }

    private static int executeWandCommand(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        PlayerEntity player = source.asPlayer();

        // Create a wooden axe
        ItemStack wandItem = new ItemStack(Items.WOODEN_AXE, 1);
        CompoundNBT nbt = wandItem.getOrCreateTag();

        // Set display name (JSON format required in 1.14)
        CompoundNBT display = new CompoundNBT();
        display.putString("Name", "{\"text\":\"Seçim Baltası\",\"color\":\"gold\"}");

        // Set lore
        ListNBT lore = new ListNBT();
        lore.add(StringNBT.valueOf("{\"text\":\"İlk köşeyi seçmek için sol tıklayın\",\"color\":\"gray\"}"));
        lore.add(StringNBT.valueOf("{\"text\":\"İkinci köşeyi seçmek için sağ tıklayın\",\"color\":\"gray\"}"));
        lore.add(StringNBT.valueOf("{\"text\":\"Seçimden sonra malzemeyi ve miktarı belirtin\",\"color\":\"yellow\"}"));
        display.put("Lore", lore);

        nbt.put("display", display);

        // Try to give player the wand or place in hand
        if (!player.getHeldItem(Hand.MAIN_HAND).isEmpty()) {
            // Try to add it to inventory
            if (!player.inventory.addItemStackToInventory(wandItem)) {
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Seçim Baltası size veremedim. Envanteriniz dolu."), player.getUniqueID());
                return 0;
            } else {
                player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Seçim Baltası envanterinize eklendi."), player.getUniqueID());
            }
        } else {
            // Place in main hand
            player.setHeldItem(Hand.MAIN_HAND, wandItem);
            player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Elinize seçim baltası verildi."), player.getUniqueID());
        }

        // Check if player already has an area selected
        AreaSelection selection = BuildWandPlugin.getInstance().getOrCreatePlayerSelection(player.getUniqueID());
        if (selection != null && selection.isComplete()) {
            player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Zaten bir seçeneğiniz var " +
                    TextFormatting.YELLOW + selection.getBlockCount() +
                    TextFormatting.GREEN + " blocks."), player.getUniqueID());

            player.sendMessage(new StringTextComponent(
                    TextFormatting.GREEN + "Yapı malzemesini belirtmek için sohbete bir malzeme ve miktar yazın " +
                            TextFormatting.YELLOW + "(e.g., 'cobblestone 100 veya planks:2 100')"), player.getUniqueID());
            BuildWandPlugin.getInstance().getBuildManager().setAwaitingMaterialInput(player.getUniqueID(), true);
        } else {
            player.sendMessage(new StringTextComponent(TextFormatting.YELLOW +
                    "İlk köşeyi seçmek için sol tıklayın, ikinci köşeyi seçmek için sağ tıklayın."), player.getUniqueID());
        }
        return 1;
    }


    private static int executeUndo(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();

        // Try to undo the last build first
        if (BuildWandPlugin.getInstance().getBuildManager().undoLastBuild(player)) {
            return 1;
        }
        // If no recent build, try to clear selection
        else {
            UUID playerUUID = player.getUniqueID();
            AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);

            if (selection != null && (selection.getFirstPosition() != null || selection.getSecondPosition() != null)) {
                // Clear the selection
                BuildWandPlugin.getInstance().getPlayerSelections().remove(playerUUID);
                player.sendMessage(new StringTextComponent(TextFormatting.YELLOW + "Alan seçiminiz temizlendi."), player.getUniqueID());
            } else {
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Geri alınacak yeni bir yapı veya temizlenecek bir seçim yok."), player.getUniqueID());
            }
        }
        return 0;
    }

    private static int executeClearSelection(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        UUID playerUUID = player.getUniqueID();
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);

        if (selection != null && (selection.getFirstPosition() != null || selection.getSecondPosition() != null)) {
            // Clear the selection
            BuildWandPlugin.getInstance().getPlayerSelections().remove(playerUUID);
            player.sendMessage(new StringTextComponent(TextFormatting.YELLOW + "Your area selection has been cleared."), player.getUniqueID());
        } else {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "You don't have an active selection to clear."), player.getUniqueID());
        }
        return 1;
    }

    private static int executeCopy(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        BuildWandPlugin.getInstance().getBuildManager().copyStructure(player);
        return 1;
    }

    private static int executePaste(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        BuildWandPlugin.getInstance().getBuildManager().pasteStructure(player, true);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        player.sendMessage(new StringTextComponent(TextFormatting.GOLD + "=== BuildWand Commands ==="), player.getUniqueID());
        player.sendMessage(new StringTextComponent(
                TextFormatting.YELLOW + "/wand" + TextFormatting.WHITE + " - Bir seçim baltayı al"
        ), player.getUniqueID());
        player.sendMessage(new StringTextComponent(
                TextFormatting.YELLOW + "/wand help" + TextFormatting.WHITE + " - Show this help message"
        ), player.getUniqueID());
        player.sendMessage(new StringTextComponent(
                TextFormatting.YELLOW + "/wand undo" + TextFormatting.WHITE + " - Son bina işleminizi geri alın veya seçiminizi temizleyin"
        ), player.getUniqueID());
        player.sendMessage(new StringTextComponent(
                TextFormatting.YELLOW + "/wand clear" + TextFormatting.WHITE + " - Mevcut seçiminizi temizleyin"
        ), player.getUniqueID());
        player.sendMessage(new StringTextComponent(
                TextFormatting.YELLOW + "/wand copy" + TextFormatting.WHITE + " - Seçili yapıyı kopyala (malzeme gereksinimlerini gösterir)"
        ), player.getUniqueID());
        player.sendMessage(new StringTextComponent(
                TextFormatting.YELLOW + "/wand paste" + TextFormatting.WHITE + " - Kopyalanan yapıyı bulunduğunuz yere yapıştırın (envanterdeki malzemeleri kullanır)"
        ), player.getUniqueID());
        return 1;
    }

    // The following methods are kept for compatibility or if specific tab completion logic is still needed.
    // For 1.13+, Brigadier handles tab completion more dynamically.

    public List<String> getTabCompletions(MinecraftServer server, CommandSource source, String[] args, @Nullable BlockPos targetPos) {
        // This method is part of the old CommandBase system and might not be directly used with Brigadier.
        // Brigadier's tab completion is handled within the CommandDispatcher registration.
        if (args.length == 1) {
            return Arrays.asList("undo", "clear", "copy", "paste", "help");
        }
        return Collections.emptyList();
    }
}