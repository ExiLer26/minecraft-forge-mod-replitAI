package com.buildwand.commands;

import com.buildwand.BuildWandPlugin;
import com.buildwand.utils.AreaSelection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class WandCommand extends CommandBase {

    @Override
    public String getName() {
        return "wand";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/wand [undo|clear|copy|paste|help]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return -1; // Anyone can use this command
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true; // Always allow execution
    }
    
    private void executeUndo(EntityPlayer player) {
        // Try to undo the last build first
        if (BuildWandPlugin.getInstance().getBuildManager().undoLastBuild(player)) {
            return;
        } 
        // If no recent build, try to clear selection
        else {
            UUID playerUUID = player.getUniqueID();
            AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);
            
            if (selection != null && (selection.getFirstPosition() != null || selection.getSecondPosition() != null)) {
                // Clear the selection
                BuildWandPlugin.getInstance().getPlayerSelections().remove(playerUUID);
                player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Alan seçiminiz temizlendi."));
            } else {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Geri alınacak yeni bir yapı veya temizlenecek bir seçim yok."));
            }
        }
    }
    
    private void executeClearSelection(EntityPlayer player) {
        UUID playerUUID = player.getUniqueID();
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);
        
        if (selection != null && (selection.getFirstPosition() != null || selection.getSecondPosition() != null)) {
            // Clear the selection
            BuildWandPlugin.getInstance().getPlayerSelections().remove(playerUUID);
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Your area selection has been cleared."));
        } else {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "You don't have an active selection to clear."));
        }
    }
    
    private void executeCopy(EntityPlayer player) {
        BuildWandPlugin.getInstance().getBuildManager().copyStructure(player);
    }
    
    private void executePaste(EntityPlayer player) {
        BuildWandPlugin.getInstance().getBuildManager().pasteStructure(player, true);
    }
    
    private void showHelp(EntityPlayer player) {
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== BuildWand Commands ==="));
        player.sendMessage(new TextComponentString(
            TextFormatting.YELLOW + "/wand" + TextFormatting.WHITE + " - Bir seçim baltayı al"
        ));
        player.sendMessage(new TextComponentString(
            TextFormatting.YELLOW + "/wand help" + TextFormatting.WHITE + " - Show this help message"
        ));
        player.sendMessage(new TextComponentString(
            TextFormatting.YELLOW + "/wand undo" + TextFormatting.WHITE + " - Son bina işleminizi geri alın veya seçiminizi temizleyin"
        ));
        player.sendMessage(new TextComponentString(
            TextFormatting.YELLOW + "/wand clear" + TextFormatting.WHITE + " - Mevcut seçiminizi temizleyin"
        ));
        player.sendMessage(new TextComponentString(
            TextFormatting.YELLOW + "/wand copy" + TextFormatting.WHITE + " - Seçili yapıyı kopyala (malzeme gereksinimlerini gösterir)"
        ));
        player.sendMessage(new TextComponentString(
            TextFormatting.YELLOW + "/wand paste" + TextFormatting.WHITE + " - Kopyalanan yapıyı bulunduğunuz yere yapıştırın (envanterdeki malzemeleri kullanır)"
        ));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Bu komut yalnızca bir oyuncu tarafından yürütülebilir."));
            return;
        }

        EntityPlayer player = (EntityPlayer) sender;
        
        // Check if the player is using a subcommand
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            
            // Handle different subcommands
            switch (subCommand) {
                case "undo":
                    executeUndo(player);
                    return;
                case "clear":
                    executeClearSelection(player);
                    return;
                case "copy":
                    executeCopy(player);
                    return;
                case "paste":
                    executePaste(player);
                    return;
                case "help":
                    showHelp(player);
                    return;
                default:
                    player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "Bilinmeyen alt komut: " + subCommand +
                        TextFormatting.YELLOW + ". Kullanılabilir komutlar için /wand help komutunu deneyin."
                    ));
                    return;
            }
        }

        // Create a wooden axe
        ItemStack wandItem = new ItemStack(Items.WOODEN_AXE, 1);
        NBTTagCompound nbt = wandItem.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            wandItem.setTagCompound(nbt);
        }
        
        // Set display name
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", TextFormatting.GOLD + "Seçim Baltası");
        
        // Set lore
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString(TextFormatting.GRAY + "İlk köşeyi seçmek için sol tıklayın"));
        lore.appendTag(new NBTTagString(TextFormatting.GRAY + "İkinci köşeyi seçmek için sağ tıklayın"));
        lore.appendTag(new NBTTagString(TextFormatting.YELLOW + "Seçimden sonra malzemeyi ve miktarı belirtin"));
        display.setTag("Lore", lore);
        
        nbt.setTag("display", display);

        // Try to give player the wand or place in hand
        if (!player.getHeldItemMainhand().isEmpty()) {
            // Try to add it to inventory
            if (!player.inventory.addItemStackToInventory(wandItem)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Seçim Baltası size veremedim. Envanteriniz dolu."));
                return;
            } else {
                player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Seçim Baltası envanterinize eklendi."));
            }
        } else {
            // Place in main hand
            player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, wandItem);
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Elinize seçim baltası verildi."));
        }

        // Check if player already has an area selected
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(player.getUniqueID());
        if (selection != null && selection.isComplete()) {
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Zaten bir seçeneğiniz var " + 
                                      TextFormatting.YELLOW + selection.getBlockCount() + 
                                      TextFormatting.GREEN + " blocks."));
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Yapı malzemesini belirtmek için sohbete malzeme ve miktar yazın"
            ));
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Tek malzeme: " + TextFormatting.WHITE + "'cobblestone 100' veya 'planks:2 100'"
            ));
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Çoklu malzeme: " + TextFormatting.WHITE + "'planks 5, planks:3 10, cobblestone 20'"
            ));
            BuildWandPlugin.getInstance().getBuildManager().setAwaitingMaterialInput(player.getUniqueID(), true);
        } else {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + 
                "İlk köşeyi seçmek için sol tıklayın, ikinci köşeyi seçmek için sağ tıklayın."));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return Arrays.asList("undo", "clear", "copy", "paste", "help");
        }
        return Collections.emptyList();
    }
}
