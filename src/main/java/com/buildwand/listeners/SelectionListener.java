package com.buildwand.listeners;

import com.buildwand.BuildWandPlugin;
import com.buildwand.utils.AreaSelection;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SelectionListener {

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        // Only run on server side
        if (event.getWorld().isRemote) return;
        
        EntityPlayer player = event.getEntityPlayer();
        if (!isHoldingWand(player) || event.getPos() == null) {
            return;
        }

        // Get player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getOrCreatePlayerSelection(player.getUniqueID());
        BlockPos pos = event.getPos();
        World world = event.getWorld();

        // Set first position
        selection.setFirstPosition(new net.minecraft.util.math.BlockPos(pos.getX(), pos.getY(), pos.getZ()), world);
        player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Pozisyon 1 ayarlandı " + 
                                  TextFormatting.YELLOW + pos.getX() + ", " + 
                                  pos.getY() + ", " + pos.getZ()));

        // Check if both positions are set
        checkBothPositionsSet(player, selection);
        
        // Cancel the event to prevent breaking blocks
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        // Only run on server side
        if (event.getWorld().isRemote) return;
        
        EntityPlayer player = event.getEntityPlayer();
        if (!isHoldingWand(player) || event.getPos() == null) {
            return;
        }

        // Get player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getOrCreatePlayerSelection(player.getUniqueID());
        BlockPos pos = event.getPos();
        World world = event.getWorld();

        // Set second position
        selection.setSecondPosition(new net.minecraft.util.math.BlockPos(pos.getX(), pos.getY(), pos.getZ()), world);
        player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Pozisyon 2 ayarlandı " + 
                                  TextFormatting.YELLOW + pos.getX() + ", " + 
                                  pos.getY() + ", " + pos.getZ()));

        // Check if both positions are set
        checkBothPositionsSet(player, selection);
        
        // Cancel the event to prevent interacting with blocks
        event.setCanceled(true);
    }

    private boolean isHoldingWand(EntityPlayer player) {
        ItemStack itemInHand = player.getHeldItemMainhand();
        return !itemInHand.isEmpty() && itemInHand.getItem() == Items.WOODEN_AXE;
    }

    private void checkBothPositionsSet(EntityPlayer player, AreaSelection selection) {
        if (selection.isComplete()) {
            int blockCount = selection.getBlockCount();
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Seçim tamamlandı! Alan şunları içerir: " + 
                TextFormatting.YELLOW + blockCount + 
                TextFormatting.GREEN + " bloklar."
            ));
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Yapı malzemesini belirtmek için sohbete bir malzeme ve miktar yazın " + 
                TextFormatting.YELLOW + "(e.g., 'cobblestone 100' or 'planks:1 100')"
            ));
            
            BuildWandPlugin.getInstance().getBuildManager().setAwaitingMaterialInput(player.getUniqueID(), true);
        }
    }
}
