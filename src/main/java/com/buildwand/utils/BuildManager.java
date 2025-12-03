
package com.buildwand.utils;

import com.buildwand.BuildWandPlugin;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildManager {
    private final Map<UUID, Boolean> awaitingMaterialInput = new HashMap<>();
    private static final Pattern MATERIAL_PATTERN = Pattern.compile("([a-zA-Z_:]+)\\s+(\\d+)");
    
    // Structure to store block changes for undo functionality
    private static class BlockChange {
        BlockPos location;
        Level world;
        BlockState originalState;
        
        BlockChange(BlockPos location, Level world, BlockState originalState) {
            this.location = location;
            this.world = world;
            this.originalState = originalState;
        }
    }
    
    // Structure to store build information for undo functionality
    private static class BuildInfo {
        List<BlockChange> changes;
        Item materialType;
        int blocksPlaced;
        Map<Item, Integer> usedMaterials;
        List<ItemStack> usedItemStacks;
        
        BuildInfo(List<BlockChange> changes, Item materialType, int blocksPlaced) {
            this.changes = changes;
            this.materialType = materialType;
            this.blocksPlaced = blocksPlaced;
            this.usedMaterials = new HashMap<>();
            this.usedItemStacks = new ArrayList<>();
        }
    }
    
    // Structure to store copied blocks for copy/paste functionality
    private static class CopiedStructure {
        Map<Vec3i, BlockState> blocks = new HashMap<>();
        Vec3i dimensions;
        Vec3i origin;
        
        CopiedStructure(Map<Vec3i, BlockState> blocks, Vec3i dimensions, Vec3i origin) {
            this.blocks = blocks;
            this.dimensions = dimensions;
            this.origin = origin;
        }
        
        // Get a list of materials needed for this structure
        Map<Item, Integer> getMaterialList() {
            Map<Item, Integer> materials = new HashMap<>();
            
            for (BlockState state : blocks.values()) {
                Block block = state.getBlock();
                
                if (block != Blocks.AIR) {
                    Item item = block.asItem();
                    if (item != null) {
                        materials.put(item, materials.getOrDefault(item, 0) + 1);
                    }
                }
            }
            
            return materials;
        }
        
        // Get a list of ItemStacks for this structure
        List<ItemStack> getMaterialStacks() {
            Map<String, ItemStack> consolidatedStacks = new HashMap<>();
            
            for (BlockState state : blocks.values()) {
                Block block = state.getBlock();
                
                if (block != Blocks.AIR) {
                    Item item = block.asItem();
                    if (item != null) {
                        String key = item.getRegistryName().toString();
                        
                        if (consolidatedStacks.containsKey(key)) {
                            consolidatedStacks.get(key).grow(1);
                        } else {
                            consolidatedStacks.put(key, new ItemStack(item, 1));
                        }
                    }
                }
            }
            
            return new ArrayList<>(consolidatedStacks.values());
        }
        
        // Get total number of blocks (excluding air)
        int getTotalBlocks() {
            int count = 0;
            for (BlockState state : blocks.values()) {
                if (state.getBlock() != Blocks.AIR) {
                    count++;
                }
            }
            return count;
        }
    }
    
    // Store the last set of changes for each player
    private final Map<UUID, BuildInfo> lastBuilds = new HashMap<>();
    
    // Store copied structures for players
    private final Map<UUID, CopiedStructure> playerCopies = new HashMap<>();

    public BuildManager() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void setAwaitingMaterialInput(UUID playerUUID, boolean awaiting) {
        awaitingMaterialInput.put(playerUUID, awaiting);
    }
    
    /**
     * Copies a structure from an area selection
     * @param player The player requesting the copy
     * @return true if copy was successful, false if selection is incomplete
     */
    public boolean copyStructure(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);
        
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(new TextComponent(ChatFormatting.RED + "Öncelikle tam bir seçim yapmanız gerekiyor!"), playerUUID);
            return false;
        }
        
        Level world = selection.getWorld();
        Map<Vec3i, BlockState> blocks = new HashMap<>();
        
        // Calculate dimensions
        int minX = selection.getMinX();
        int minY = selection.getMinY();
        int minZ = selection.getMinZ();
        int maxX = selection.getMaxX();
        int maxY = selection.getMaxY();
        int maxZ = selection.getMaxZ();
        
        Vec3i dimensions = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        Vec3i origin = new Vec3i(minX, minY, minZ);
        
        // Copy all blocks in the selection
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    // Store relative position from minimum coordinates
                    Vec3i relativePos = new Vec3i(x - minX, y - minY, z - minZ);
                    blocks.put(relativePos, state);
                }
            }
        }
        
        // Create and store the copied structure
        CopiedStructure copiedStructure = new CopiedStructure(blocks, dimensions, origin);
        playerCopies.put(playerUUID, copiedStructure);
        
        // Generate a material list summary
        Map<Item, Integer> materials = copiedStructure.getMaterialList();
        int totalBlocks = copiedStructure.getTotalBlocks();
        
        // Send confirmation message to player
        player.sendMessage(new TextComponent(
            ChatFormatting.GREEN + "Boyutları olan bir yapı başarıyla kopyalandı " + 
            ChatFormatting.YELLOW + dimensions.getX() + "x" + dimensions.getY() + "x" + dimensions.getZ() +
            ChatFormatting.GREEN + " içeren " +
            ChatFormatting.YELLOW + totalBlocks +
            ChatFormatting.GREEN + " bloklar. "
        ), playerUUID);
        
        // Show material list if not empty
        if (!materials.isEmpty()) {
            player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Gerekli malzemeler:"), playerUUID);
            
            for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
                String itemName = entry.getKey().getRegistryName() != null ? entry.getKey().getRegistryName().toString() : "unknown";
                player.sendMessage(new TextComponent(
                    ChatFormatting.YELLOW + "• " + 
                    ChatFormatting.GOLD + entry.getValue() + "x " +
                    ChatFormatting.WHITE + itemName
                ), playerUUID);
            }
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Kullanmak " +
                ChatFormatting.YELLOW + "/wand paste" +
                ChatFormatting.GREEN + " Bu yapıyı yerleştirmek için."
            ), playerUUID);
        }
        
        return true;
    }
    
    /**
     * Undoes the last building operation for a player and returns materials
     * @param player The player requesting the undo
     * @return true if undo was successful, false if there was nothing to undo
     */
    public boolean undoLastBuild(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        BuildInfo buildInfo = lastBuilds.get(playerUUID);
        
        if (buildInfo == null || buildInfo.changes.isEmpty()) {
            return false;
        }
        
        int blocksReverted = 0;
        
        // Restore each block back to its original state
        for (BlockChange change : buildInfo.changes) {
            change.world.setBlock(change.location, change.originalState, 3);
            blocksReverted++;
        }
        
        // Return materials to player's inventory
        int returnedBlocks = 0;
        
        // Check if this was a copy-paste operation (multiple materials)
        if (buildInfo.usedItemStacks != null && !buildInfo.usedItemStacks.isEmpty()) {
            // Return each ItemStack
            for (ItemStack stack : buildInfo.usedItemStacks) {
                Item type = stack.getItem();
                int count = stack.getCount();
                
                if (count > 0) {
                    returnMaterialsToInventory(player, type, count);
                    returnedBlocks += count;
                }
            }
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Başarıyla geri alındı " +
                ChatFormatting.YELLOW + blocksReverted +
                ChatFormatting.GREEN + " blokları orijinal hallerine geri döndürür. " +
                ChatFormatting.GOLD + returnedBlocks +
                ChatFormatting.GREEN + " Çeşitli türdeki malzemeler envanterinize geri döndü. "
            ), playerUUID);
        }
        // Fallback to old usedMaterials map (for backward compatibility)
        else if (buildInfo.usedMaterials != null && !buildInfo.usedMaterials.isEmpty()) {
            // Return each type of material
            Map<Item, Integer> materials = buildInfo.usedMaterials;
            
            for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
                Item type = entry.getKey();
                int count = entry.getValue();
                
                if (count > 0) {
                    returnMaterialsToInventory(player, type, count);
                    returnedBlocks += count;
                }
            }
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Başarıyla geri alındı " +
                ChatFormatting.YELLOW + blocksReverted +
                ChatFormatting.GREEN + " blokları orijinal hallerine geri döndürür. " +
                ChatFormatting.GOLD + returnedBlocks +
                ChatFormatting.GREEN + " Çeşitli türdeki malzemeler envanterinize geri döndü."
            ), playerUUID);
        }
        // Regular build (single material)
        else if (buildInfo.materialType != null && buildInfo.blocksPlaced > 0) {
            returnMaterialsToInventory(player, buildInfo.materialType, buildInfo.blocksPlaced);
            
            String itemName = buildInfo.materialType.getRegistryName() != null ? buildInfo.materialType.getRegistryName().toString() : "unknown";
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Başarıyla geri alındı " +
                ChatFormatting.YELLOW + blocksReverted +
                ChatFormatting.GREEN + " bloklar orijinal durumlarına geri döner. " +
                ChatFormatting.GOLD + buildInfo.blocksPlaced +
                ChatFormatting.GREEN + " malzemeleri " +
                ChatFormatting.YELLOW + itemName +
                ChatFormatting.GREEN + " envanterinize iade edildi."
            ), playerUUID);
        }
        // No materials to return
        else {
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Başarıyla geri alındı " +
                ChatFormatting.YELLOW + blocksReverted +
                ChatFormatting.GREEN + " bloklar orijinal durumlarına geri döner."
            ), playerUUID);
        }
        
        // Clear the build info after undoing
        lastBuilds.remove(playerUUID);
        
        return true;
    }
    
    /**
     * Pastes previously copied structure at player's location
     * @param player The player requesting the paste
     * @param checkMaterials Whether to check if player has required materials
     * @return true if paste was successful, false if no structure was copied
     */
    public boolean pasteStructure(ServerPlayer player, boolean checkMaterials) {
        UUID playerUUID = player.getUUID();
        CopiedStructure copiedStructure = playerCopies.get(playerUUID);
        
        if (copiedStructure == null) {
            player.sendMessage(new TextComponent(ChatFormatting.RED + "Önce bir yapıyı kopyalamanız gerekiyor!"), playerUUID);
            return false;
        }
        
        // Get the player's location to use as the paste origin
        BlockPos playerPos = player.blockPosition();
        Level world = player.level;
        
        // We'll use the block the player is standing on
        int originX = playerPos.getX();
        int originY = playerPos.getY() - 1; // One below the player
        int originZ = playerPos.getZ();
        
        // Track actually removed ItemStacks for accurate undo
        List<ItemStack> actuallyRemoved = new ArrayList<>();
        
        // Check materials if required
        if (checkMaterials) {
            List<ItemStack> requiredStacks = copiedStructure.getMaterialStacks();
            List<ItemStack> missingStacks = new ArrayList<>();
            
            // Check each material
            for (ItemStack requiredStack : requiredStacks) {
                Item itemType = requiredStack.getItem();
                int required = requiredStack.getCount();
                int available = countPlayerItems(player, itemType);
                
                if (available < required) {
                    missingStacks.add(new ItemStack(itemType, required - available));
                }
            }
            
            // If anything is missing, tell the player
            if (!missingStacks.isEmpty()) {
                player.sendMessage(new TextComponent(ChatFormatting.RED + "Yeterli materyaliniz yok:"), playerUUID);
                
                for (ItemStack missingStack : missingStacks) {
                    Item item = missingStack.getItem();
                    String itemName = item.getRegistryName() != null ? item.getRegistryName().toString() : "bilinmeyen";
                    player.sendMessage(new TextComponent(
                        ChatFormatting.YELLOW + "• İhtiyaç " + 
                        ChatFormatting.GOLD + missingStack.getCount() + 
                        ChatFormatting.YELLOW + " Daha " + 
                        ChatFormatting.WHITE + itemName
                    ), playerUUID);
                }
                
                return false;
            }
            
            // Remove materials from player inventory
            for (ItemStack requiredStack : requiredStacks) {
                Item itemType = requiredStack.getItem();
                int count = requiredStack.getCount();
                
                // Remove and track what was actually removed
                removeItemTypeFromInventory(player, itemType, count);
                actuallyRemoved.add(new ItemStack(itemType, count));
            }
        }
        
        int blocksPlaced = 0;
        List<BlockChange> changes = new ArrayList<>();
        
        // Paste each block
        for (Map.Entry<Vec3i, BlockState> entry : copiedStructure.blocks.entrySet()) {
            Vec3i relPos = entry.getKey();
            BlockState state = entry.getValue();
            
            // Skip air blocks
            if (state.getBlock() == Blocks.AIR) {
                continue;
            }
            
            // Calculate world position
            int x = originX + relPos.getX();
            int y = originY + relPos.getY();
            int z = originZ + relPos.getZ();
            
            BlockPos pos = new BlockPos(x, y, z);
            
            // Record original state for undo
            BlockState originalState = world.getBlockState(pos);
            changes.add(new BlockChange(pos, world, originalState));
            
            // Place the block
            world.setBlock(pos, state, 3);
            blocksPlaced++;
        }
        
        // Save changes for undo
        if (blocksPlaced > 0) {
            if (checkMaterials) {
                // Store actually removed materials for undo to return them
                BuildInfo buildInfo = new BuildInfo(changes, null, 0);
                buildInfo.usedItemStacks = actuallyRemoved;
                lastBuilds.put(playerUUID, buildInfo);
            } else {
                // Free mode - no materials to return on undo
                lastBuilds.put(playerUUID, new BuildInfo(changes, null, 0));
            }
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GREEN + "Yapı başarıyla yapıştırıldı " +
                ChatFormatting.YELLOW + blocksPlaced +
                ChatFormatting.GREEN + " bloklar."
            ), playerUUID);
            
            player.sendMessage(new TextComponent(
                ChatFormatting.GRAY + "Tip " +
                ChatFormatting.YELLOW + "/wand undo" +
                ChatFormatting.GRAY + " bu yapıştırmayı geri almak için."
            ), playerUUID);
        } else {
            player.sendMessage(new TextComponent(ChatFormatting.YELLOW + "Hiçbir blok yerleştirilmedi (yapı yalnızca hava blokları içerebilir)."), playerUUID);
        }
        
        return true;
    }
    
    /**
     * Removes a specific item type from player's inventory
     */
    private void removeItemTypeFromInventory(ServerPlayer player, Item itemType, int amount) {
        int remaining = amount;
        Inventory inventory = player.getInventory();
        
        // Remove items from inventory
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            
            if (!stack.isEmpty() && stack.getItem() == itemType) {
                int toRemove = Math.min(remaining, stack.getCount());
                
                if (toRemove == stack.getCount()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                } else {
                    stack.shrink(toRemove);
                }
                
                remaining -= toRemove;
            }
        }
    }
    
    /**
     * Returns materials to the player's inventory
     * @param player The player to give items to
     * @param itemType The type of item to give
     * @param amount The amount of items to give
     */
    private void returnMaterialsToInventory(ServerPlayer player, Item itemType, int amount) {
        Inventory inventory = player.getInventory();
        
        // Calculate full stacks and remaining items
        int maxStackSize = new ItemStack(itemType).getMaxStackSize();
        int fullStacks = amount / maxStackSize;
        int remainingItems = amount % maxStackSize;
        
        // Add full stacks
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(itemType, maxStackSize);
            
            if (!inventory.add(stack)) {
                // Drop the item if inventory is full
                player.drop(stack, false);
            }
        }
        
        // Add remaining items if any
        if (remainingItems > 0) {
            ItemStack stack = new ItemStack(itemType, remainingItems);
            
            if (!inventory.add(stack)) {
                // Drop the item if inventory is full
                player.drop(stack, false);
            }
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID playerUUID = player.getUUID();

        // Check if the player is expected to input a material
        if (awaitingMaterialInput.getOrDefault(playerUUID, false)) {
            String message = event.getMessage();
            
            // Parse the message for material and quantity
            Matcher matcher = MATERIAL_PATTERN.matcher(message);
            if (matcher.matches()) {
                event.setCanceled(true); // Cancel the chat message
                awaitingMaterialInput.put(playerUUID, false); // Reset awaiting state
                
                String materialName = matcher.group(1);
                int quantity = Integer.parseInt(matcher.group(2));
                
                handleMaterialSpecification(player, materialName, quantity);
            }
        }
    }

    private void handleMaterialSpecification(ServerPlayer player, String materialName, int quantity) {
        // Normalize material name to lowercase and add minecraft: prefix if not present
        materialName = materialName.toLowerCase();
        if (!materialName.contains(":")) {
            materialName = "minecraft:" + materialName;
        }
        
        // Try to find the block from the registry
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(materialName));
        
        if (block == null) {
            player.sendMessage(new TextComponent(ChatFormatting.RED + "Bilinmeyen malzeme: " + materialName), player.getUUID());
            BuildWandPlugin.getInstance().getLogger().warn("Bilinmeyen blok türü: " + materialName);
            return;
        }
        
        // Get the default block state
        BlockState blockState = block.defaultBlockState();
        
        // Get the corresponding item
        Item item = block.asItem();
        
        if (item == null) {
            player.sendMessage(new TextComponent(ChatFormatting.RED + "Blok için karşılık gelen öğe bulunamadı: " + materialName), player.getUUID());
            BuildWandPlugin.getInstance().getLogger().warn("Blok için öğe türü bulunamadı: " + materialName);
            return;
        }
        
        // Check if player has enough of the material
        int playerHasAmount = countPlayerItems(player, item);
        
        if (playerHasAmount < quantity) {
            String blockName = block.getRegistryName() != null ? block.getRegistryName().toString() : "bilinmeyen";
            player.sendMessage(new TextComponent(ChatFormatting.RED + "Yeterince sahip değilsin " + 
                                      ChatFormatting.GOLD + blockName + 
                                      ChatFormatting.RED + ". İhtiyaç " + 
                                      ChatFormatting.YELLOW + quantity + 
                                      ChatFormatting.RED + " ama sadece sahip olduğun " + 
                                      ChatFormatting.YELLOW + playerHasAmount + 
                                      ChatFormatting.RED + "."), player.getUUID());
            return;
        }
        
        // Get the player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(player.getUUID());
        
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(new TextComponent(ChatFormatting.RED + "Tam bir seçiminiz yok."), player.getUUID());
            return;
        }
        
        // Check if specified quantity is enough for the area
        int blocksInArea = selection.getBlockCount();
        
        if (quantity < blocksInArea) {
            player.sendMessage(new TextComponent(ChatFormatting.YELLOW + "Uyarı: Belirttiğiniz " + 
                                      ChatFormatting.GOLD + quantity + 
                                      ChatFormatting.YELLOW + " bloklar ancak alan içerir " + 
                                      ChatFormatting.GOLD + blocksInArea + 
                                      ChatFormatting.YELLOW + " bloklar. Bazı bloklar doldurulamayabilir."), player.getUUID());
        }
        
        // Perform the build operation
        buildArea(player, selection, blockState, item, quantity);
    }

    private int countPlayerItems(ServerPlayer player, Item itemType) {
        int count = 0;
        Inventory inventory = player.getInventory();
        
        // Iterate through the player's inventory to count items
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == itemType) {
                count += stack.getCount();
            }
        }
        
        return count;
    }

    private void buildArea(ServerPlayer player, AreaSelection selection, BlockState blockState, Item item, int maxBlocks) {
        Level world = selection.getWorld();
        
        int blocksPlaced = 0;
        List<BlockChange> changes = new ArrayList<>();
        
        // Iterate over the selected area
        for (int x = selection.getMinX(); x <= selection.getMaxX() && blocksPlaced < maxBlocks; x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY() && blocksPlaced < maxBlocks; y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ() && blocksPlaced < maxBlocks; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    
                    // Skip non-air blocks (only replace air)
                    if (world.getBlockState(pos).getBlock() != Blocks.AIR) {
                        continue;
                    }
                    
                    // Store the original state for undo functionality
                    BlockState originalState = world.getBlockState(pos);
                    changes.add(new BlockChange(pos, world, originalState));
                    
                    // Place the block
                    world.setBlock(pos, blockState, 3);
                    blocksPlaced++;
                }
            }
        }
        
        // Store the build information for this player (for undo)
        lastBuilds.put(player.getUUID(), new BuildInfo(changes, item, blocksPlaced));
        
        // Remove the used items from player's inventory
        removeItemTypeFromInventory(player, item, blocksPlaced);
        
        Block blockType = blockState.getBlock();
        String blockName = blockType.getRegistryName() != null ? blockType.getRegistryName().toString() : "bilinmeyen";
        
        player.sendMessage(new TextComponent(ChatFormatting.GREEN + "Başarıyla yerleştirildi " + 
                                  ChatFormatting.YELLOW + blocksPlaced + 
                                  ChatFormatting.GREEN + " blokları " + 
                                  ChatFormatting.GOLD + blockName + 
                                  ChatFormatting.GREEN + "."), player.getUUID());
        
        if (blocksPlaced > 0) {
            player.sendMessage(new TextComponent(
                ChatFormatting.GRAY + "Tip " +
                ChatFormatting.YELLOW + "/wand undo" +
                ChatFormatting.GRAY + " Bu yapıyı geri almak için."
            ), player.getUUID());
        }
    }
}
