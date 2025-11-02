package com.virtualchest.storage;

import com.virtualchest.VirtualChestMod;
import com.virtualchest.utils.InventoryUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChestStorage {

    private final VirtualChestMod mod;
    private final File storageDir;
    
    // Cache of open inventories: playerUUID -> chestNumber -> inventory
    private final Map<UUID, Map<Integer, IInventory>> openInventories = new ConcurrentHashMap<>();
    
    // Cache of chest contents: playerUUID -> chestNumber -> NBT data
    private final Map<UUID, Map<Integer, NBTTagCompound>> chestContents = new ConcurrentHashMap<>();
    
    public ChestStorage(VirtualChestMod mod) {
        this.mod = mod;
        this.storageDir = new File(mod.getConfigManager().configFile.getParentFile(), "storage");
        
        // Create the storage directory if it doesn't exist
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }
    
    /**
     * Open a virtual chest for a player
     * @param player The player
     * @param chestNumber The chest number
     */
    public void openChest(EntityPlayer player, int chestNumber) {
        UUID playerUUID = player.getUniqueID();
        
        // Load chest data if not loaded
        if (!chestContents.containsKey(playerUUID) || !chestContents.get(playerUUID).containsKey(chestNumber)) {
            loadChestData(playerUUID, chestNumber);
        }
        
        // Get or create the inventory
        IInventory inventory = getOrCreateInventory(player, chestNumber);
        
        // Open the inventory
        if (player instanceof EntityPlayerMP) {
            player.displayGUIChest(inventory);
        }
    }
    
    /**
     * Open another player's virtual chest for admin viewing
     * @param admin The admin player viewing the chest
     * @param targetUUID The target player's UUID
     * @param targetName The target player's name
     * @param chestNumber The chest number
     */
    public void openOtherPlayerChest(EntityPlayer admin, UUID targetUUID, String targetName, int chestNumber) {
        // Load chest data if not loaded
        if (!chestContents.containsKey(targetUUID) || !chestContents.get(targetUUID).containsKey(chestNumber)) {
            loadChestData(targetUUID, chestNumber);
        }
        
        // Get the number of slots for this chest
        int slots = mod.getConfigManager().getChestSlots(chestNumber);
        
        // Create the inventory with target player's name in the title
        IInventory inventory = new InventoryBasic(targetName + "'s Virtual Chest #" + chestNumber, false, slots);
        
        // Load items into inventory (read-only view)
        NBTTagCompound chestData = chestContents
            .getOrDefault(targetUUID, new HashMap<>())
            .get(chestNumber);
        
        if (chestData != null) {
            InventoryUtil.loadItemsFromNBT(inventory, chestData);
        }
        
        // Open the inventory for the admin
        if (admin instanceof EntityPlayerMP) {
            admin.displayGUIChest(inventory);
        }
        
        // Note: This inventory view is read-only since we don't store it in openInventories map
        // and don't handle saving changes to it
    }
    
    /**
     * Get or create an inventory for a player's chest
     * @param player The player
     * @param chestNumber The chest number
     * @return The inventory
     */
    private IInventory getOrCreateInventory(EntityPlayer player, int chestNumber) {
        UUID playerUUID = player.getUniqueID();
        
        // Check if the inventory is already open
        if (openInventories.containsKey(playerUUID) && openInventories.get(playerUUID).containsKey(chestNumber)) {
            return openInventories.get(playerUUID).get(chestNumber);
        }
        
        // Get the number of slots for this chest
        int slots = mod.getConfigManager().getChestSlots(chestNumber);
        
        // Create the inventory
        IInventory inventory = new InventoryBasic("Virtual Chest #" + chestNumber, false, slots);
        
        // Load items into inventory
        NBTTagCompound chestData = chestContents
            .getOrDefault(playerUUID, new HashMap<>())
            .get(chestNumber);
        
        if (chestData != null) {
            InventoryUtil.loadItemsFromNBT(inventory, chestData);
        }
        
        // Store the open inventory
        openInventories.computeIfAbsent(playerUUID, k -> new HashMap<>())
            .put(chestNumber, inventory);
        
        return inventory;
    }
    
    /**
     * Save the contents of a player's chest
     * @param playerUUID The player UUID
     * @param chestNumber The chest number
     */
    public void saveChestData(UUID playerUUID, int chestNumber) {
        // Check if the player has open inventories
        if (!openInventories.containsKey(playerUUID) || !openInventories.get(playerUUID).containsKey(chestNumber)) {
            return;
        }
        
        // Get the inventory
        IInventory inventory = openInventories.get(playerUUID).get(chestNumber);
        
        // Convert inventory to NBT
        NBTTagCompound chestData = InventoryUtil.saveItemsToNBT(inventory);
        
        // Store the data in the cache
        chestContents.computeIfAbsent(playerUUID, k -> new HashMap<>())
            .put(chestNumber, chestData);
        
        // Save to file
        saveChestDataToFile(playerUUID, chestNumber, chestData);
    }
    
    /**
     * Save chest data to file
     * @param playerUUID The player UUID
     * @param chestNumber The chest number
     * @param chestData The chest data
     */
    private void saveChestDataToFile(UUID playerUUID, int chestNumber, NBTTagCompound chestData) {
        File playerDir = new File(storageDir, playerUUID.toString());
        File chestFile = new File(playerDir, chestNumber + ".dat");
        
        try {
            // Create player directory if it doesn't exist
            if (!playerDir.exists()) {
                playerDir.mkdirs();
            }
            
            // Write NBT data to file
            try (FileOutputStream fos = new FileOutputStream(chestFile)) {
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(chestData, fos);
            }
        } catch (IOException e) {
            VirtualChestMod.getLogger().error("Failed to save chest data for player " + playerUUID + ", chest " + chestNumber, e);
        }
    }
    
    /**
     * Load chest data for a player
     * @param playerUUID The player UUID
     * @param chestNumber The chest number
     */
    public void loadChestData(UUID playerUUID, int chestNumber) {
        File playerDir = new File(storageDir, playerUUID.toString());
        File chestFile = new File(playerDir, chestNumber + ".dat");
        
        NBTTagCompound chestData = new NBTTagCompound();
        
        // Load from file if it exists
        if (chestFile.exists()) {
            try (FileInputStream fis = new FileInputStream(chestFile)) {
                chestData = net.minecraft.nbt.CompressedStreamTools.readCompressed(fis);
            } catch (IOException e) {
                VirtualChestMod.getLogger().error("Failed to load chest data for player " + playerUUID + ", chest " + chestNumber, e);
                chestData = new NBTTagCompound(); // Use empty data if loading fails
            }
        }
        
        // Store in cache
        chestContents.computeIfAbsent(playerUUID, k -> new HashMap<>())
            .put(chestNumber, chestData);
    }
    
    /**
     * Save all open chests for a player
     * @param playerUUID The player UUID
     */
    public void savePlayerData(UUID playerUUID) {
        // Check if the player has open inventories
        if (!openInventories.containsKey(playerUUID)) {
            return;
        }
        
        // Save each chest
        for (Integer chestNumber : openInventories.get(playerUUID).keySet()) {
            saveChestData(playerUUID, chestNumber);
        }
        
        // Remove from open inventories
        openInventories.remove(playerUUID);
    }
    
    /**
     * Save all player data
     */
    public void saveAllData() {
        for (UUID playerUUID : new HashMap<>(openInventories).keySet()) {
            savePlayerData(playerUUID);
        }
    }
    
    /**
     * Reload the storage
     */
    public void reloadStorage() {
        // Save all data first
        saveAllData();
        
        // Clear caches
        openInventories.clear();
        chestContents.clear();
    }
    
    /**
     * Close a player's inventory
     * @param player The player
     * @param chestNumber The chest number
     */
    public void closeChest(EntityPlayer player, int chestNumber) {
        UUID playerUUID = player.getUniqueID();
        
        // Save the chest data
        saveChestData(playerUUID, chestNumber);
        
        // Remove from open inventories
        if (openInventories.containsKey(playerUUID)) {
            openInventories.get(playerUUID).remove(chestNumber);
            
            // If player has no more open inventories, remove player from map
            if (openInventories.get(playerUUID).isEmpty()) {
                openInventories.remove(playerUUID);
            }
        }
    }
    
    /**
     * Handle player disconnect event
     * @param event The event
     */
    @SubscribeEvent
    public void onPlayerDisconnect(PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        savePlayerData(player.getUniqueID());
    }
}