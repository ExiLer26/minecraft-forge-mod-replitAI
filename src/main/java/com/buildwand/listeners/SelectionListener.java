
package com.buildwand.listeners;

import com.buildwand.BuildWandPlugin;
import com.buildwand.utils.AreaSelection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SelectionListener {

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        // Only run on server side
        if (event.getWorld().isClientSide()) return;
        
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        
        if (!isHoldingWand(player) || event.getPos() == null) {
            return;
        }

        // Get player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getOrCreatePlayerSelection(player.getUUID());
        BlockPos pos = event.getPos();
        Level world = event.getWorld();

        // Set first position
        selection.setFirstPosition(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), world);
        player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Pozisyon 1 ayarlandı " + 
                                  ChatFormatting.YELLOW + pos.getX() + ", " + 
                                  pos.getY() + ", " + pos.getZ()), player.getUUID());

        // Check if both positions are set
        checkBothPositionsSet(player, selection);
        
        // Cancel the event to prevent breaking blocks
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        // Only run on server side
        if (event.getWorld().isClientSide()) return;
        
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        
        if (!isHoldingWand(player) || event.getPos() == null) {
            return;
        }

        // Get player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getOrCreatePlayerSelection(player.getUUID());
        BlockPos pos = event.getPos();
        Level world = event.getWorld();

        // Set second position
        selection.setSecondPosition(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), world);
        player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Pozisyon 2 ayarlandı " + 
                                  ChatFormatting.YELLOW + pos.getX() + ", " + 
                                  pos.getY() + ", " + pos.getZ()), player.getUUID());

        // Check if both positions are set
        checkBothPositionsSet(player, selection);
        
        // Cancel the event to prevent interacting with blocks
        event.setCanceled(true);
    }

    private boolean isHoldingWand(ServerPlayer player) {
        ItemStack itemInHand = player.getMainHandItem();
        return !itemInHand.isEmpty() && itemInHand.getItem() == Items.WOODEN_AXE;
    }

    private void checkBothPositionsSet(ServerPlayer player, AreaSelection selection) {
        if (selection.isComplete()) {
            int blockCount = selection.getBlockCount();
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Seçim tamamlandı! Alan şunları içerir: " + 
                ChatFormatting.YELLOW + blockCount + 
                ChatFormatting.GREEN + " bloklar."
            ), player.getUUID());
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Yapı malzemesini belirtmek için sohbete bir malzeme ve miktar yazın " + 
                ChatFormatting.YELLOW + "(e.g., 'cobblestone 100' or 'planks:1 100')"
            ), player.getUUID());
            
            BuildWandPlugin.getInstance().getBuildManager().setAwaitingMaterialInput(player.getUUID(), true);
        }
    }
}
