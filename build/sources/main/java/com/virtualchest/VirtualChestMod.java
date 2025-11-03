package com.virtualchest;

import com.virtualchest.commands.VirtualChestCommand;
import com.virtualchest.config.ConfigManager;
import com.virtualchest.proxy.CommonProxy;
import com.virtualchest.storage.ChestStorage;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = VirtualChestMod.MODID, name = VirtualChestMod.NAME, version = VirtualChestMod.VERSION, acceptedMinecraftVersions = "[1.12,1.13)")
public class VirtualChestMod {
    public static final String MODID = "virtualchest";
    public static final String NAME = "Virtual Chest";
    public static final String VERSION = "1.0.0";

    @SidedProxy(clientSide = "com.virtualchest.proxy.ClientProxy", serverSide = "com.virtualchest.proxy.ServerProxy")
    public static CommonProxy proxy;

    @Mod.Instance(MODID)
    public static VirtualChestMod instance;

    private static Logger logger;
    private ConfigManager configManager;
    private ChestStorage chestStorage;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        
        // Initialize configuration
        this.configManager = new ConfigManager(event.getModConfigurationDirectory());
        this.configManager.loadConfig();
        
        logger.info("Virtual Chest mod pre-initialization completed.");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        
        // Initialize storage
        this.chestStorage = new ChestStorage(this);
        
        // Register event handlers
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this.chestStorage);
        
        logger.info("Virtual Chest mod initialization completed.");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Register commands
        ICommandManager commandManager = event.getServer().getCommandManager();
        ServerCommandManager serverCommandManager = (ServerCommandManager) commandManager;
        serverCommandManager.registerCommand(new VirtualChestCommand(this));
        
        logger.info("Virtual Chest mod server starting completed.");
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        // Save all player data when server stops
        if (this.chestStorage != null) {
            this.chestStorage.saveAllData();
        }
        logger.info("Virtual Chest mod server stopping completed, all data saved.");
    }

    /**
     * Get the mod instance
     * @return The mod instance
     */
    public static VirtualChestMod getInstance() {
        return instance;
    }

    /**
     * Get the mod's logger
     * @return The logger
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * Get the config manager
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Get the chest storage
     * @return The chest storage
     */
    public ChestStorage getChestStorage() {
        return chestStorage;
    }
}