
package com.buildwand.commands;

import com.buildwand.BuildWandPlugin;
import com.buildwand.utils.AreaSelection;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

public class WandCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wand")
                .executes(WandCommand::executeWandCommand)
                .then(Commands.literal("undo").executes(WandCommand::executeUndo))
                .then(Commands.literal("clear").executes(WandCommand::executeClearSelection))
                .then(Commands.literal("copy").executes(WandCommand::executeCopy))
                .then(Commands.literal("paste").executes(WandCommand::executePaste))
                .then(Commands.literal("help").executes(WandCommand::showHelp))
        );
    }

    private static int executeWandCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        // Create a wooden axe
        ItemStack wandItem = new ItemStack(Items.WOODEN_AXE, 1);
        CompoundTag nbt = wandItem.getOrCreateTag();

        // Set display name (JSON format required in 1.17)
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"Seçim Baltası\",\"color\":\"gold\"}");

        // Set lore
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf("{\"text\":\"İlk köşeyi seçmek için sol tıklayın\",\"color\":\"gray\"}"));
        lore.add(StringTag.valueOf("{\"text\":\"İkinci köşeyi seçmek için sağ tıklayın\",\"color\":\"gray\"}"));
        lore.add(StringTag.valueOf("{\"text\":\"Seçimden sonra malzemeyi ve miktarı belirtin\",\"color\":\"yellow\"}"));
        display.put("Lore", lore);

        nbt.put("display", display);

        // Try to give player the wand or place in hand
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            // Try to add it to inventory
            if (!player.getInventory().add(wandItem)) {
                player.sendMessage(new TextComponent(ChatFormatting.RED + "Seçim Baltası size veremedim. Envanteriniz dolu."), player.getUUID());
                return 0;
            } else {
                player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Seçim Baltası envanterinize eklendi."), player.getUUID());
            }
        } else {
            // Place in main hand
            player.setItemInHand(InteractionHand.MAIN_HAND, wandItem);
            player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Elinize seçim baltası verildi."), player.getUUID());
        }

        // Check if player already has an area selected
        AreaSelection selection = BuildWandPlugin.getInstance().getOrCreatePlayerSelection(player.getUUID());
        if (selection != null && selection.isComplete()) {
            player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Zaten bir seçeneğiniz var " +
                    ChatFormatting.YELLOW + selection.getBlockCount() +
                    ChatFormatting.GREEN + " blocks."), player.getUUID());

            player.sendMessage(new TextComponent(
                    ChatFormatting.GREEN + "Yapı malzemesini belirtmek için sohbete bir malzeme ve miktar yazın " +
                            ChatFormatting.YELLOW + "(e.g., 'cobblestone 100 veya planks:2 100')"), player.getUUID());
            BuildWandPlugin.getInstance().getBuildManager().setAwaitingMaterialInput(player.getUUID(), true);
        } else {
            player.sendMessage(new TextComponent(ChatFormatting.YELLOW +
                    "İlk köşeyi seçmek için sol tıklayın, ikinci köşeyi seçmek için sağ tıklayın."), player.getUUID());
        }
        return 1;
    }


    private static int executeUndo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Try to undo the last build first
        if (BuildWandPlugin.getInstance().getBuildManager().undoLastBuild(player)) {
            return 1;
        }
        // If no recent build, try to clear selection
        else {
            UUID playerUUID = player.getUUID();
            AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);

            if (selection != null && (selection.getFirstPosition() != null || selection.getSecondPosition() != null)) {
                // Clear the selection
                BuildWandPlugin.getInstance().getPlayerSelections().remove(playerUUID);
                player.sendMessage(new TextComponent(ChatFormatting.YELLOW + "Alan seçiminiz temizlendi."), player.getUUID());
            } else {
                player.sendMessage(new TextComponent(ChatFormatting.RED + "Geri alınacak yeni bir yapı veya temizlenecek bir seçim yok."), player.getUUID());
            }
        }
        return 0;
    }

    private static int executeClearSelection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerUUID = player.getUUID();
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);

        if (selection != null && (selection.getFirstPosition() != null || selection.getSecondPosition() != null)) {
            // Clear the selection
            BuildWandPlugin.getInstance().getPlayerSelections().remove(playerUUID);
            player.sendMessage(new TextComponent(ChatFormatting.YELLOW + "Your area selection has been cleared."), player.getUUID());
        } else {
            player.sendMessage(new TextComponent(ChatFormatting.RED + "You don't have an active selection to clear."), player.getUUID());
        }
        return 1;
    }

    private static int executeCopy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BuildWandPlugin.getInstance().getBuildManager().copyStructure(player);
        return 1;
    }

    private static int executePaste(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BuildWandPlugin.getInstance().getBuildManager().pasteStructure(player, true);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendMessage(new TextComponent(ChatFormatting.GOLD + "=== BuildWand Commands ==="), player.getUUID());
        player.sendMessage(new TextComponent(
                ChatFormatting.YELLOW + "/wand" + ChatFormatting.WHITE + " - Bir seçim baltayı al"
        ), player.getUUID());
        player.sendMessage(new TextComponent(
                ChatFormatting.YELLOW + "/wand help" + ChatFormatting.WHITE + " - Show this help message"
        ), player.getUUID());
        player.sendMessage(new TextComponent(
                ChatFormatting.YELLOW + "/wand undo" + ChatFormatting.WHITE + " - Son bina işleminizi geri alın veya seçiminizi temizleyin"
        ), player.getUUID());
        player.sendMessage(new TextComponent(
                ChatFormatting.YELLOW + "/wand clear" + ChatFormatting.WHITE + " - Mevcut seçiminizi temizleyin"
        ), player.getUUID());
        player.sendMessage(new TextComponent(
                ChatFormatting.YELLOW + "/wand copy" + ChatFormatting.WHITE + " - Seçili yapıyı kopyala (malzeme gereksinimlerini gösterir)"
        ), player.getUUID());
        player.sendMessage(new TextComponent(
                ChatFormatting.YELLOW + "/wand paste" + ChatFormatting.WHITE + " - Kopyalanan yapıyı bulunduğunuz yere yapıştırın (envanterdeki malzemeleri kullanır)"
        ), player.getUUID());
        return 1;
    }
}
