
package com.buildwand.utils;

import com.buildwand.BuildWandPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
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
        World world;
        BlockState originalState;
        
        BlockChange(BlockPos location, World world, BlockState originalState) {
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
        Map<Vector3i, BlockState> blocks = new HashMap<>();
        Vector3i dimensions;
        Vector3i origin;
        
        CopiedStructure(Map<Vector3i, BlockState> blocks, Vector3i dimensions, Vector3i origin) {
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
    public boolean copyStructure(PlayerEntity player) {
        UUID playerUUID = player.getUniqueID();
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);
        
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Öncelikle tam bir seçim yapmanız gerekiyor!"), player.getUniqueID());
            return false;
        }
        
        World world = selection.getWorld();
        Map<Vector3i, BlockState> blocks = new HashMap<>();
        
        // Calculate dimensions
        int minX = selection.getMinX();
        int minY = selection.getMinY();
        int minZ = selection.getMinZ();
        int maxX = selection.getMaxX();
        int maxY = selection.getMaxY();
        int maxZ = selection.getMaxZ();
        
        Vector3i dimensions = new Vector3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        Vector3i origin = new Vector3i(minX, minY, minZ);
        
        // Copy all blocks in the selection
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    // Store relative position from minimum coordinates
                    Vector3i relativePos = new Vector3i(x - minX, y - minY, z - minZ);
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
        player.sendMessage(new StringTextComponent(
            TextFormatting.GREEN + "Boyutları olan bir yapı başarıyla kopyalandı " + 
            TextFormatting.YELLOW + dimensions.getX() + "x" + dimensions.getY() + "x" + dimensions.getZ() +
            TextFormatting.GREEN + " içeren " +
            TextFormatting.YELLOW + totalBlocks +
            TextFormatting.GREEN + " bloklar. "
        ), player.getUniqueID());
        
        // Show material list if not empty
        if (!materials.isEmpty()) {
            player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Gerekli malzemeler:"), player.getUniqueID());
            
            for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
                String itemName = entry.getKey().getRegistryName() != null ? entry.getKey().getRegistryName().toString() : "unknown";
                player.sendMessage(new StringTextComponent(
                    TextFormatting.YELLOW + "• " + 
                    TextFormatting.GOLD + entry.getValue() + "x " +
                    TextFormatting.WHITE + itemName
                ), player.getUniqueID());
            }
            
            player.sendMessage(new StringTextComponent(
                TextFormatting.GREEN + "Kullanmak " +
                TextFormatting.YELLOW + "/wand paste" +
                TextFormatting.GREEN + " Bu yapıyı yerleştirmek için."
            ), player.getUniqueID());
        }
        
        return true;
    }
    
    /**
     * Undoes the last building operation for a player and returns materials
     * @param player The player requesting the undo
     * @return true if undo was successful, false if there was nothing to undo
     */
    public boolean undoLastBuild(PlayerEntity player) {
        UUID playerUUID = player.getUniqueID();
        BuildInfo buildInfo = lastBuilds.get(playerUUID);
        
        if (buildInfo == null || buildInfo.changes.isEmpty()) {
            return false;
        }
        
        int blocksReverted = 0;
        
        // Restore each block back to its original state
        for (BlockChange change : buildInfo.changes) {
            change.world.setBlockState(change.location, change.originalState, 3);
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
            
            player.sendMessage(new StringTextComponent(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " blokları orijinal hallerine geri döndürür. " +
                TextFormatting.GOLD + returnedBlocks +
                TextFormatting.GREEN + " Çeşitli türdeki malzemeler envanterinize geri döndü. "
            ), player.getUniqueID());
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
            
            player.sendMessage(new StringTextComponent(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " blokları orijinal hallerine geri döndürür. " +
                TextFormatting.GOLD + returnedBlocks +
                TextFormatting.GREEN + " Çeşitli türdeki malzemeler envanterinize geri döndü."
            ), player.getUniqueID());
        }
        // Regular build (single material)
        else if (buildInfo.materialType != null && buildInfo.blocksPlaced > 0) {
            returnMaterialsToInventory(player, buildInfo.materialType, buildInfo.blocksPlaced);
            
            String itemName = buildInfo.materialType.getRegistryName() != null ? buildInfo.materialType.getRegistryName().toString() : "unknown";
            player.sendMessage(new StringTextComponent(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " bloklar orijinal durumlarına geri döner. " +
                TextFormatting.GOLD + buildInfo.blocksPlaced +
                TextFormatting.GREEN + " malzemeleri " +
                TextFormatting.YELLOW + itemName +
                TextFormatting.GREEN + " envanterinize iade edildi."
            ), player.getUniqueID());
        }
        // No materials to return
        else {
            player.sendMessage(new StringTextComponent(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " bloklar orijinal durumlarına geri döner."
            ), player.getUniqueID());
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
    public boolean pasteStructure(PlayerEntity player, boolean checkMaterials) {
        UUID playerUUID = player.getUniqueID();
        CopiedStructure copiedStructure = playerCopies.get(playerUUID);
        
        if (copiedStructure == null) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Önce bir yapıyı kopyalamanız gerekiyor!"), player.getUniqueID());
            return false;
        }
        
        // Get the player's location to use as the paste origin
        BlockPos playerPos = player.getPosition();
        World world = player.world;
        
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
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Yeterli materyaliniz yok:"), player.getUniqueID());
                
                for (ItemStack missingStack : missingStacks) {
                    Item item = missingStack.getItem();
                    String itemName = item.getRegistryName() != null ? item.getRegistryName().toString() : "bilinmeyen";
                    player.sendMessage(new StringTextComponent(
                        TextFormatting.YELLOW + "• İhtiyaç " + 
                        TextFormatting.GOLD + missingStack.getCount() + 
                        TextFormatting.YELLOW + " Daha " + 
                        TextFormatting.WHITE + itemName
                    ), player.getUniqueID());
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
        for (Map.Entry<Vector3i, BlockState> entry : copiedStructure.blocks.entrySet()) {
            Vector3i relPos = entry.getKey();
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
            world.setBlockState(pos, state, 3);
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
            
            player.sendMessage(new StringTextComponent(
                TextFormatting.GREEN + "Yapı başarıyla yapıştırıldı " +
                TextFormatting.YELLOW + blocksPlaced +
                TextFormatting.GREEN + " bloklar."
            ), player.getUniqueID());
            
            player.sendMessage(new StringTextComponent(
                TextFormatting.GRAY + "Tip " +
                TextFormatting.YELLOW + "/wand undo" +
                TextFormatting.GRAY + " bu yapıştırmayı geri almak için."
            ), player.getUniqueID());
        } else {
            player.sendMessage(new StringTextComponent(TextFormatting.YELLOW + "Hiçbir blok yerleştirilmedi (yapı yalnızca hava blokları içerebilir)."), player.getUniqueID());
        }
        
        return true;
    }
    
    /**
     * Removes a specific item type from player's inventory
     */
    private void removeItemTypeFromInventory(PlayerEntity player, Item itemType, int amount) {
        int remaining = amount;
        PlayerInventory inventory = player.inventory;
        
        // Remove items from inventory
        for (int i = 0; i < inventory.getSizeInventory() && remaining > 0; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            
            if (!stack.isEmpty() && stack.getItem() == itemType) {
                int toRemove = Math.min(remaining, stack.getCount());
                
                if (toRemove == stack.getCount()) {
                    inventory.setInventorySlotContents(i, ItemStack.EMPTY);
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
    private void returnMaterialsToInventory(PlayerEntity player, Item itemType, int amount) {
        PlayerInventory inventory = player.inventory;
        
        // Calculate full stacks and remaining items
        int maxStackSize = new ItemStack(itemType).getMaxStackSize();
        int fullStacks = amount / maxStackSize;
        int remainingItems = amount % maxStackSize;
        
        // Add full stacks
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(itemType, maxStackSize);
            
            if (!inventory.addItemStackToInventory(stack)) {
                // Drop the item if inventory is full
                player.dropItem(stack, false);
            }
        }
        
        // Add remaining items if any
        if (remainingItems > 0) {
            ItemStack stack = new ItemStack(itemType, remainingItems);
            
            if (!inventory.addItemStackToInventory(stack)) {
                // Drop the item if inventory is full
                player.dropItem(stack, false);
            }
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        PlayerEntity player = event.getPlayer();
        UUID playerUUID = player.getUniqueID();

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

    private void handleMaterialSpecification(PlayerEntity player, String materialName, int quantity) {
        // Normalize material name to lowercase and add minecraft: prefix if not present
        materialName = materialName.toLowerCase();
        if (!materialName.contains(":")) {
            materialName = "minecraft:" + materialName;
        }
        
        // Try to find the block from the registry
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(materialName));
        
        if (block == null) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Bilinmeyen malzeme: " + materialName), player.getUniqueID());
            BuildWandPlugin.getInstance().getLogger().warn("Bilinmeyen blok türü: " + materialName);
            return;
        }
        
        // Get the default block state
        BlockState blockState = block.getDefaultState();
        
        // Get the corresponding item
        Item item = block.asItem();
        
        if (item == null) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Blok için karşılık gelen öğe bulunamadı: " + materialName), player.getUniqueID());
            BuildWandPlugin.getInstance().getLogger().warn("Blok için öğe türü bulunamadı: " + materialName);
            return;
        }
        
        // Check if player has enough of the material
        int playerHasAmount = countPlayerItems(player, item);
        
        if (playerHasAmount < quantity) {
            String blockName = block.getRegistryName() != null ? block.getRegistryName().toString() : "bilinmeyen";
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Yeterince sahip değilsin " + 
                                      TextFormatting.GOLD + blockName + 
                                      TextFormatting.RED + ". İhtiyaç " + 
                                      TextFormatting.YELLOW + quantity + 
                                      TextFormatting.RED + " ama sadece sahip olduğun " + 
                                      TextFormatting.YELLOW + playerHasAmount + 
                                      TextFormatting.RED + "."), player.getUniqueID());
            return;
        }
        
        // Get the player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(player.getUniqueID());
        
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Tam bir seçiminiz yok."), player.getUniqueID());
            return;
        }
        
        // Check if specified quantity is enough for the area
        int blocksInArea = selection.getBlockCount();
        
        if (quantity < blocksInArea) {
            player.sendMessage(new StringTextComponent(TextFormatting.YELLOW + "Uyarı: Belirttiğiniz " + 
                                      TextFormatting.GOLD + quantity + 
                                      TextFormatting.YELLOW + " bloklar ancak alan içerir " + 
                                      TextFormatting.GOLD + blocksInArea + 
                                      TextFormatting.YELLOW + " bloklar. Bazı bloklar doldurulamayabilir."), player.getUniqueID());
        }
        
        // Perform the build operation
        buildArea(player, selection, blockState, item, quantity);
    }

    private int countPlayerItems(PlayerEntity player, Item itemType) {
        int count = 0;
        PlayerInventory inventory = player.inventory;
        
        // Iterate through the player's inventory to count items
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == itemType) {
                count += stack.getCount();
            }
        }
        
        return count;
    }

    private void buildArea(PlayerEntity player, AreaSelection selection, BlockState blockState, Item item, int maxBlocks) {
        World world = selection.getWorld();
        
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
                    world.setBlockState(pos, blockState, 3);
                    blocksPlaced++;
                }
            }
        }
        
        // Store the build information for this player (for undo)
        lastBuilds.put(player.getUniqueID(), new BuildInfo(changes, item, blocksPlaced));
        
        // Remove the used items from player's inventory
        removeItemTypeFromInventory(player, item, blocksPlaced);
        
        Block blockType = blockState.getBlock();
        String blockName = blockType.getRegistryName() != null ? blockType.getRegistryName().toString() : "bilinmeyen";
        
        player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Başarıyla yerleştirildi " + 
                                  TextFormatting.YELLOW + blocksPlaced + 
                                  TextFormatting.GREEN + " blokları " + 
                                  TextFormatting.GOLD + blockName + 
                                  TextFormatting.GREEN + "."), player.getUniqueID());
        
        if (blocksPlaced > 0) {
            player.sendMessage(new StringTextComponent(
                TextFormatting.GRAY + "Tip " +
                TextFormatting.YELLOW + "/wand undo" +
                TextFormatting.GRAY + " Bu yapıyı geri almak için."
            ), player.getUniqueID());
        }
    }
}
