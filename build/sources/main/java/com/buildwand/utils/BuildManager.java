package com.buildwand.utils;

import com.buildwand.BuildWandPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildManager {
    private final Map<UUID, Boolean> awaitingMaterialInput = new HashMap<>();
    private static final Pattern MATERIAL_PATTERN = Pattern.compile("([a-zA-Z_:]+)(?::(\\d+))?\\s+(\\d+)");
    
    // Structure to store block changes for undo functionality
    private static class BlockChange {
        BlockPos location;
        World world;
        IBlockState originalState;
        
        BlockChange(BlockPos location, World world, IBlockState originalState) {
            this.location = location;
            this.world = world;
            this.originalState = originalState;
        }
    }
    
    // Structure to store build information for undo functionality
    private static class BuildInfo {
        List<BlockChange> changes;
        Item materialType;  // For single material builds (normal building)
        int metadata;  // Metadata for the material (for single material builds)
        int blocksPlaced;
        Map<Item, Integer> usedMaterials;  // For complex paste operations (multiple materials) - deprecated, use usedItemStacks
        List<ItemStack> usedItemStacks;  // For complex paste operations with metadata support
        
        BuildInfo(List<BlockChange> changes, Item materialType, int metadata, int blocksPlaced) {
            this.changes = changes;
            this.materialType = materialType;
            this.metadata = metadata;
            this.blocksPlaced = blocksPlaced;
            this.usedMaterials = new HashMap<>();
            this.usedItemStacks = new ArrayList<>();
        }
    }
    
    // Structure to store copied blocks for copy/paste functionality
    private static class CopiedStructure {
        Map<Vec3i, IBlockState> blocks = new HashMap<>();
        Vec3i dimensions;
        Vec3i origin;
        
        CopiedStructure(Map<Vec3i, IBlockState> blocks, Vec3i dimensions, Vec3i origin) {
            this.blocks = blocks;
            this.dimensions = dimensions;
            this.origin = origin;
        }
        
        // Get a list of materials needed for this structure
        Map<Item, Integer> getMaterialList() {
            Map<Item, Integer> materials = new HashMap<>();
            
            for (IBlockState state : blocks.values()) {
                Block block = state.getBlock();
                
                if (block != Blocks.AIR) {
                    Item item = Item.getItemFromBlock(block);
                    if (item != null) {
                        materials.put(item, materials.getOrDefault(item, 0) + 1);
                    }
                }
            }
            
            return materials;
        }
        
        // Get a list of ItemStacks with metadata for this structure
        List<ItemStack> getMaterialStacks() {
            Map<String, ItemStack> consolidatedStacks = new HashMap<>();
            
            for (IBlockState state : blocks.values()) {
                Block block = state.getBlock();
                
                if (block != Blocks.AIR) {
                    Item item = Item.getItemFromBlock(block);
                    if (item != null) {
                        int metadata = block.getMetaFromState(state);
                        String key = item.getRegistryName() + ":" + metadata;
                        
                        if (consolidatedStacks.containsKey(key)) {
                            consolidatedStacks.get(key).grow(1);
                        } else {
                            consolidatedStacks.put(key, new ItemStack(item, 1, metadata));
                        }
                    }
                }
            }
            
            return new ArrayList<>(consolidatedStacks.values());
        }
        
        // Get total number of blocks (excluding air)
        int getTotalBlocks() {
            int count = 0;
            for (IBlockState state : blocks.values()) {
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
    public boolean copyStructure(EntityPlayer player) {
        UUID playerUUID = player.getUniqueID();
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(playerUUID);
        
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Öncelikle tam bir seçim yapmanız gerekiyor!"));
            return false;
        }
        
        World world = selection.getWorld();
        Map<Vec3i, IBlockState> blocks = new HashMap<>();
        
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
                    IBlockState state = world.getBlockState(pos);
                    
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
        player.sendMessage(new TextComponentString(
            TextFormatting.GREEN + "Boyutları olan bir yapı başarıyla kopyalandı " + 
            TextFormatting.YELLOW + dimensions.getX() + "x" + dimensions.getY() + "x" + dimensions.getZ() +
            TextFormatting.GREEN + " içeren " +
            TextFormatting.YELLOW + totalBlocks +
            TextFormatting.GREEN + " bloklar. "
        ));
        
        // Show material list if not empty
        if (!materials.isEmpty()) {
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Gerekli malzemeler:"));
            
            for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
                String itemName = entry.getKey().getRegistryName() != null ? entry.getKey().getRegistryName().toString() : "unknown";
                player.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + "• " + 
                    TextFormatting.GOLD + entry.getValue() + "x " +
                    TextFormatting.WHITE + itemName
                ));
            }
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Kullanmak " +
                TextFormatting.YELLOW + "/wand paste" +
                TextFormatting.GREEN + " Bu yapıyı yerleştirmek için."
            ));
        }
        
        return true;
    }
    
    /**
     * Undoes the last building operation for a player and returns materials
     * @param player The player requesting the undo
     * @return true if undo was successful, false if there was nothing to undo
     */
    public boolean undoLastBuild(EntityPlayer player) {
        UUID playerUUID = player.getUniqueID();
        BuildInfo buildInfo = lastBuilds.get(playerUUID);
        
        if (buildInfo == null || buildInfo.changes.isEmpty()) {
            return false;
        }
        
        int blocksReverted = 0;
        int blocksStillPlaced = 0;
        
        // First, check which blocks are still placed (not manually broken)
        // and restore each block back to its original state
        for (BlockChange change : buildInfo.changes) {
            // Check if the block is still the one we placed (not air, meaning not broken)
            IBlockState currentState = change.world.getBlockState(change.location);
            
            // If the block is not air, it means it's still placed
            if (currentState.getBlock() != Blocks.AIR) {
                blocksStillPlaced++;
            }
            
            // Restore to original state regardless
            change.world.setBlockState(change.location, change.originalState, 3);
            blocksReverted++;
        }
        
        // Return materials to player's inventory - ONLY for blocks that were still placed
        int returnedBlocks = 0;
        
        // Check if this was a copy-paste operation (multiple materials)
        if (buildInfo.usedItemStacks != null && !buildInfo.usedItemStacks.isEmpty()) {
            // Calculate proportion of blocks still placed
            double proportion = buildInfo.changes.isEmpty() ? 0 : (double) blocksStillPlaced / buildInfo.changes.size();
            
            // Return each ItemStack with correct metadata, proportionally
            for (ItemStack stack : buildInfo.usedItemStacks) {
                Item type = stack.getItem();
                int metadata = stack.getMetadata();
                int originalCount = stack.getCount();
                int countToReturn = (int) Math.round(originalCount * proportion);
                
                if (countToReturn > 0) {
                    returnMaterialsToInventory(player, type, metadata, countToReturn);
                    returnedBlocks += countToReturn;
                }
            }
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " blokları orijinal hallerine geri döndürür. " +
                TextFormatting.GOLD + returnedBlocks +
                TextFormatting.GREEN + " Çeşitli türdeki malzemeler envanterinize geri döndü. "
            ));
        }
        // Fallback to old usedMaterials map (for backward compatibility)
        else if (buildInfo.usedMaterials != null && !buildInfo.usedMaterials.isEmpty()) {
            // Calculate proportion of blocks still placed
            double proportion = buildInfo.changes.isEmpty() ? 0 : (double) blocksStillPlaced / buildInfo.changes.size();
            
            // Return each type of material (without metadata), proportionally
            Map<Item, Integer> materials = buildInfo.usedMaterials;
            
            for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
                Item type = entry.getKey();
                int originalCount = entry.getValue();
                int countToReturn = (int) Math.round(originalCount * proportion);
                
                if (countToReturn > 0) {
                    returnMaterialsToInventory(player, type, countToReturn);
                    returnedBlocks += countToReturn;
                }
            }
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " blokları orijinal hallerine geri döndürür. " +
                TextFormatting.GOLD + returnedBlocks +
                TextFormatting.GREEN + " Çeşitli türdeki malzemeler envanterinize geri döndü."
            ));
        }
        // Regular build (single material)
        else if (buildInfo.materialType != null && buildInfo.blocksPlaced > 0) {
            // Only return materials for blocks that are still placed
            returnMaterialsToInventory(player, buildInfo.materialType, buildInfo.metadata, blocksStillPlaced);
            
            String itemName = buildInfo.materialType.getRegistryName() != null ? buildInfo.materialType.getRegistryName().toString() : "unknown";
            String fullName = buildInfo.metadata > 0 ? itemName + ":" + buildInfo.metadata : itemName;
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " bloklar orijinal durumlarına geri döner. " +
                TextFormatting.GOLD + blocksStillPlaced +
                TextFormatting.GREEN + " malzemeleri " +
                TextFormatting.YELLOW + fullName +
                TextFormatting.GREEN + " envanterinize iade edildi."
            ));
        }
        // No materials to return
        else {
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Başarıyla geri alındı " +
                TextFormatting.YELLOW + blocksReverted +
                TextFormatting.GREEN + " bloklar orijinal durumlarına geri döner."
            ));
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
    public boolean pasteStructure(EntityPlayer player, boolean checkMaterials) {
        UUID playerUUID = player.getUniqueID();
        CopiedStructure copiedStructure = playerCopies.get(playerUUID);
        
        if (copiedStructure == null) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Önce bir yapıyı kopyalamanız gerekiyor!"));
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
            
            // Check each material with metadata
            for (ItemStack requiredStack : requiredStacks) {
                Item itemType = requiredStack.getItem();
                int metadata = requiredStack.getMetadata();
                int required = requiredStack.getCount();
                int available = countPlayerItems(player, itemType, metadata);
                
                if (available < required) {
                    missingStacks.add(new ItemStack(itemType, required - available, metadata));
                }
            }
            
            // If anything is missing, tell the player
            if (!missingStacks.isEmpty()) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "Yeterli materyaliniz yok:"));
                
                for (ItemStack missingStack : missingStacks) {
                    Item item = missingStack.getItem();
                    int metadata = missingStack.getMetadata();
                    String itemName = item.getRegistryName() != null ? item.getRegistryName().toString() : "bilinmeyen";
                    String fullName = metadata > 0 ? itemName + ":" + metadata : itemName;
                    player.sendMessage(new TextComponentString(
                        TextFormatting.YELLOW + "• İhtiyaç " + 
                        TextFormatting.GOLD + missingStack.getCount() + 
                        TextFormatting.YELLOW + " Daha " + 
                        TextFormatting.WHITE + fullName
                    ));
                }
                
                return false;
            }
            
            // Remove materials from player inventory with metadata support
            for (ItemStack requiredStack : requiredStacks) {
                Item itemType = requiredStack.getItem();
                int metadata = requiredStack.getMetadata();
                int count = requiredStack.getCount();
                
                // Remove and track what was actually removed
                removeItemTypeFromInventory(player, itemType, metadata, count);
                actuallyRemoved.add(new ItemStack(itemType, count, metadata));
            }
        }
        
        int blocksPlaced = 0;
        List<BlockChange> changes = new ArrayList<>();
        
        // Paste each block
        for (Map.Entry<Vec3i, IBlockState> entry : copiedStructure.blocks.entrySet()) {
            Vec3i relPos = entry.getKey();
            IBlockState state = entry.getValue();
            
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
            IBlockState originalState = world.getBlockState(pos);
            changes.add(new BlockChange(pos, world, originalState));
            
            // Place the block
            world.setBlockState(pos, state, 3);
            blocksPlaced++;
        }
        
        // Save changes for undo
        if (blocksPlaced > 0) {
            if (checkMaterials) {
                // Store actually removed materials for undo to return them (with metadata support)
                // Use actuallyRemoved instead of getMaterialStacks() to ensure we return exactly what was taken
                
                // Create a BuildInfo with all materials used
                BuildInfo buildInfo = new BuildInfo(changes, null, 0, 0);
                buildInfo.usedItemStacks = actuallyRemoved;
                lastBuilds.put(playerUUID, buildInfo);
            } else {
                // Free mode - no materials to return on undo
                lastBuilds.put(playerUUID, new BuildInfo(changes, null, 0, 0));
            }
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Yapı başarıyla yapıştırıldı " +
                TextFormatting.YELLOW + blocksPlaced +
                TextFormatting.GREEN + " bloklar."
            ));
            
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "Tip " +
                TextFormatting.YELLOW + "/wand undo" +
                TextFormatting.GRAY + " bu yapıştırmayı geri almak için."
            ));
        } else {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Hiçbir blok yerleştirilmedi (yapı yalnızca hava blokları içerebilir)."));
        }
        
        return true;
    }
    
    /**
     * Removes a specific item type from player's inventory
     */
    private void removeItemTypeFromInventory(EntityPlayer player, Item itemType, int amount) {
        removeItemTypeFromInventory(player, itemType, -1, amount);
    }
    
    private void removeItemTypeFromInventory(EntityPlayer player, Item itemType, int metadata, int amount) {
        int remaining = amount;
        InventoryPlayer inventory = player.inventory;
        
        // Remove items from inventory
        for (int i = 0; i < inventory.getSizeInventory() && remaining > 0; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            
            if (!stack.isEmpty() && stack.getItem() == itemType) {
                // If metadata is -1, remove any variant. Otherwise, only remove matching metadata
                if (metadata == -1 || stack.getMetadata() == metadata) {
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
    }
    
    /**
     * Returns materials to the player's inventory
     * @param player The player to give items to
     * @param itemType The type of item to give
     * @param amount The amount of items to give
     */
    private void returnMaterialsToInventory(EntityPlayer player, Item itemType, int amount) {
        returnMaterialsToInventory(player, itemType, 0, amount);
    }
    
    private void returnMaterialsToInventory(EntityPlayer player, Item itemType, int metadata, int amount) {
        InventoryPlayer inventory = player.inventory;
        
        // Calculate full stacks and remaining items
        int maxStackSize = itemType.getItemStackLimit();
        int fullStacks = amount / maxStackSize;
        int remainingItems = amount % maxStackSize;
        
        // Add full stacks
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = new ItemStack(itemType, maxStackSize, metadata);
            
            if (!inventory.addItemStackToInventory(stack)) {
                // Drop the item if inventory is full
                player.dropItem(stack, false);
            }
        }
        
        // Add remaining items if any
        if (remainingItems > 0) {
            ItemStack stack = new ItemStack(itemType, remainingItems, metadata);
            
            if (!inventory.addItemStackToInventory(stack)) {
                // Drop the item if inventory is full
                player.dropItem(stack, false);
            }
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        EntityPlayer player = event.getPlayer();
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
                String metadataStr = matcher.group(2);
                int metadata = (metadataStr != null) ? Integer.parseInt(metadataStr) : 0;
                int quantity = Integer.parseInt(matcher.group(3));
                
                handleMaterialSpecification(player, materialName, metadata, quantity);
            }
        }
    }

    private void handleMaterialSpecification(EntityPlayer player, String materialName, int metadata, int quantity) {
        // Normalize material name to lowercase and add minecraft: prefix if not present
        materialName = materialName.toLowerCase();
        if (!materialName.contains(":")) {
            materialName = "minecraft:" + materialName;
        }
        
        // Try to find the block from the registry
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(materialName));
        
        if (block == null) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Bilinmeyen malzeme: " + materialName));
            BuildWandPlugin.getInstance().getLogger().warn("Bilinmeyen blok türü: " + materialName);
            return;
        }
        
        // Get the block state from metadata
        IBlockState blockState;
        try {
            blockState = block.getStateFromMeta(metadata);
        } catch (Exception e) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Geçersiz meta veri: " + metadata + " for block: " + materialName));
            BuildWandPlugin.getInstance().getLogger().warn("Geçersiz meta veri " + metadata + " blok için: " + materialName);
            return;
        }
        
        // Get the corresponding item
        Item item = Item.getItemFromBlock(block);
        
        if (item == null) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Blok için karşılık gelen öğe bulunamadı: " + materialName));
            BuildWandPlugin.getInstance().getLogger().warn("Blok için öğe türü bulunamadı: " + materialName);
            return;
        }
        
        // Check if player has enough of the material with the correct metadata
        int playerHasAmount = countPlayerItems(player, item, metadata);
        
        if (playerHasAmount < quantity) {
            String blockName = block.getRegistryName() != null ? block.getRegistryName().toString() : "bilinmeyen";
            String fullName = metadata > 0 ? blockName + ":" + metadata : blockName;
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Yeterince sahip değilsin " + 
                                      TextFormatting.GOLD + fullName + 
                                      TextFormatting.RED + ". İhtiyaç " + 
                                      TextFormatting.YELLOW + quantity + 
                                      TextFormatting.RED + " ama sadece sahip olduğun " + 
                                      TextFormatting.YELLOW + playerHasAmount + 
                                      TextFormatting.RED + "."));
            return;
        }
        
        // Get the player's selection
        AreaSelection selection = BuildWandPlugin.getInstance().getPlayerSelections().get(player.getUniqueID());
        
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Tam bir seçiminiz yok."));
            return;
        }
        
        // Check if specified quantity is enough for the area
        int blocksInArea = selection.getBlockCount();
        
        if (quantity < blocksInArea) {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Uyarı: Belirttiğiniz " + 
                                      TextFormatting.GOLD + quantity + 
                                      TextFormatting.YELLOW + " bloklar ancak alan içerir " + 
                                      TextFormatting.GOLD + blocksInArea + 
                                      TextFormatting.YELLOW + " bloklar. Bazı bloklar doldurulamayabilir."));
        }
        
        // Perform the build operation
        buildArea(player, selection, blockState, item, metadata, quantity);
    }

    private int countPlayerItems(EntityPlayer player, Item itemType) {
        return countPlayerItems(player, itemType, -1);
    }
    
    private int countPlayerItems(EntityPlayer player, Item itemType, int metadata) {
        int count = 0;
        InventoryPlayer inventory = player.inventory;
        
        // Iterate through the player's inventory to count items
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == itemType) {
                // If metadata is -1, count all variants. Otherwise, only count matching metadata
                if (metadata == -1 || stack.getMetadata() == metadata) {
                    count += stack.getCount();
                }
            }
        }
        
        return count;
    }

    private void buildArea(EntityPlayer player, AreaSelection selection, IBlockState blockState, Item item, int metadata, int maxBlocks) {
        World world = selection.getWorld();
        
        int blocksPlaced = 0;
        List<BlockChange> changes = new ArrayList<>();
        
        // First, count how many blocks we can actually place
        int blocksToPlace = 0;
        for (int x = selection.getMinX(); x <= selection.getMaxX() && blocksToPlace < maxBlocks; x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY() && blocksToPlace < maxBlocks; y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ() && blocksToPlace < maxBlocks; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    
                    // Skip non-air blocks (only replace air)
                    if (world.getBlockState(pos).getBlock() != Blocks.AIR) {
                        continue;
                    }
                    
                    blocksToPlace++;
                }
            }
        }
        
        // Remove materials from inventory BEFORE placing blocks
        removeItemTypeFromInventory(player, item, metadata, blocksToPlace);
        
        // Now place the blocks
        for (int x = selection.getMinX(); x <= selection.getMaxX() && blocksPlaced < blocksToPlace; x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY() && blocksPlaced < blocksToPlace; y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ() && blocksPlaced < blocksToPlace; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    
                    // Skip non-air blocks (only replace air)
                    if (world.getBlockState(pos).getBlock() != Blocks.AIR) {
                        continue;
                    }
                    
                    // Store the original state for undo functionality
                    IBlockState originalState = world.getBlockState(pos);
                    changes.add(new BlockChange(pos, world, originalState));
                    
                    // Place the block
                    world.setBlockState(pos, blockState, 3);
                    blocksPlaced++;
                }
            }
        }
        
        // Store the build information for this player (for undo)
        lastBuilds.put(player.getUniqueID(), new BuildInfo(changes, item, metadata, blocksPlaced));
        
        Block blockType = blockState.getBlock();
        String blockName = blockType.getRegistryName() != null ? blockType.getRegistryName().toString() : "bilinmeyen";
        String fullName = metadata > 0 ? blockName + ":" + metadata : blockName;
        
        player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Başarıyla yerleştirildi " + 
                                  TextFormatting.YELLOW + blocksPlaced + 
                                  TextFormatting.GREEN + " blokları " + 
                                  TextFormatting.GOLD + fullName + 
                                  TextFormatting.GREEN + "."));
        
        if (blocksPlaced > 0) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "Tip " +
                TextFormatting.YELLOW + "/wand undo" +
                TextFormatting.GRAY + " Bu yapıyı geri almak için."
            ));
        }
    }

    // This method is now redundant since we already have removeItemTypeFromInventory
    // Keep it for backward compatibility if needed
    private void removeItemsFromInventory(EntityPlayer player, String blockTypeId, int amount) {
        // Get the item corresponding to the block
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockTypeId));
        
        if (block == null) {
            BuildWandPlugin.getInstance().getLogger().warn("Blok türü bulunamadı: " + blockTypeId);
            return;
        }
        
        Item item = Item.getItemFromBlock(block);
        
        if (item == null) {
            BuildWandPlugin.getInstance().getLogger().warn("Blok için öğe türü bulunamadı: " + blockTypeId);
            return;
        }
        
        // Use the existing method
        removeItemTypeFromInventory(player, item, amount);
    }
}
