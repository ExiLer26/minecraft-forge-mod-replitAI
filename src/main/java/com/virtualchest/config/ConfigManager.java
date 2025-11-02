package com.virtualchest.config;

import com.virtualchest.VirtualChestMod;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private Configuration config;
    public File configFile;
    
    // Default configuration values
    private int maxChests = 100;
    private int defaultRows = 3;
    private int defaultColumns = 9;
    private Map<Integer, Integer> customChestSizes = new HashMap<>();
    
    public ConfigManager(File configDir) {
        this.configFile = new File(configDir, "virtualchest.cfg");
    }
    
    /**
     * Load or create the configuration file
     */
    public void loadConfig() {
        try {
            config = new Configuration(configFile);
            config.load();
            
            // Read configuration values
            maxChests = config.getInt("maxChests", "settings", 100, 1, 100, "Maximum number of chests a player can have (1-100)");
            defaultRows = config.getInt("defaultRows", "settings", 3, 1, 6, "Default number of rows for a chest");
            defaultColumns = config.getInt("defaultColumns", "settings", 9, 9, 9, "Default number of columns for a chest (always 9)");
            
            // Load custom chest sizes
            customChestSizes.clear();
            String[] customSizeKeys = config.getStringList("customSizeKeys", "custom-sizes", new String[]{}, "Custom chest size keys");
            int[] customSizeValues = config.get("custom-sizes", "customSizeValues", new int[]{}).getIntList();
            
            // If we have saved custom sizes, load them
            if (customSizeKeys.length == customSizeValues.length) {
                for (int i = 0; i < customSizeKeys.length; i++) {
                    try {
                        int chestNumber = Integer.parseInt(customSizeKeys[i]);
                        customChestSizes.put(chestNumber, customSizeValues[i]);
                    } catch (NumberFormatException e) {
                        VirtualChestMod.getLogger().warn("Invalid chest number in config: " + customSizeKeys[i]);
                    }
                }
            } else {
                // Set some default custom sizes
                customChestSizes.put(1, 27);  // 3 rows
                customChestSizes.put(2, 54);  // 6 rows
                customChestSizes.put(3, 18);  // 2 rows
            }
            
            // Save the config to make sure defaults are written
            saveConfig();
            
            VirtualChestMod.getLogger().info("Configuration loaded successfully.");
        } catch (Exception e) {
            VirtualChestMod.getLogger().error("Failed to load configuration", e);
        }
    }
    
    /**
     * Save the configuration to file
     */
    public void saveConfig() {
        try {
            if (config == null) return;
            
            // Update config values
            config.get("settings", "maxChests", maxChests).set(maxChests);
            config.get("settings", "defaultRows", defaultRows).set(defaultRows);
            config.get("settings", "defaultColumns", defaultColumns).set(defaultColumns);
            
            // Save custom chest sizes
            String[] keys = new String[customChestSizes.size()];
            int[] values = new int[customChestSizes.size()];
            int i = 0;
            
            for (Map.Entry<Integer, Integer> entry : customChestSizes.entrySet()) {
                keys[i] = entry.getKey().toString();
                values[i] = entry.getValue();
                i++;
            }
            
            config.get("custom-sizes", "customSizeKeys", new String[]{}).set(keys);
            config.get("custom-sizes", "customSizeValues", new int[]{}).set(values);
            
            // Save the configuration
            config.save();
            VirtualChestMod.getLogger().info("Configuration saved successfully.");
        } catch (Exception e) {
            VirtualChestMod.getLogger().error("Failed to save configuration", e);
        }
    }
    
    /**
     * Get the maximum number of chests a player can have
     * @return The maximum number of chests
     */
    public int getMaxChests() {
        return maxChests;
    }
    
    /**
     * Get the default number of rows for a chest
     * @return The default number of rows
     */
    public int getDefaultRows() {
        return defaultRows;
    }
    
    /**
     * Get the default number of columns for a chest
     * @return The default number of columns
     */
    public int getDefaultColumns() {
        return defaultColumns;
    }
    
    /**
     * Get the default number of slots for a chest
     * @return The default number of slots
     */
    public int getDefaultSlots() {
        return defaultRows * defaultColumns;
    }
    
    /**
     * Get the number of slots for a specific chest
     * @param chestNumber The chest number
     * @return The number of slots
     */
    public int getChestSlots(int chestNumber) {
        // First check if there's a specific configuration for this chest number
        if (customChestSizes.containsKey(chestNumber)) {
            return customChestSizes.get(chestNumber);
        }
        
        // Otherwise return the default slots
        return getDefaultSlots();
    }
    
    /**
     * Set a custom size for a chest
     * @param chestNumber The chest number
     * @param slots The number of slots
     */
    public void setChestSize(int chestNumber, int slots) {
        customChestSizes.put(chestNumber, slots);
        saveConfig();
    }
    
    /**
     * Remove a custom size for a chest
     * @param chestNumber The chest number
     */
    public void removeCustomChestSize(int chestNumber) {
        customChestSizes.remove(chestNumber);
        saveConfig();
    }
}