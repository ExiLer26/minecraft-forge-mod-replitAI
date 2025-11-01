package com.buildwand;

import com.buildwand.commands.WandCommand;
import com.buildwand.listeners.SelectionListener;
import com.buildwand.utils.AreaSelection;
import com.buildwand.utils.BuildManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//@Mod(modid = BuildWandPlugin.MODID, name = "Build Wand", version = "2.0.0")

@Mod(
    modid = BuildWandPlugin.MODID,
    name = "Build Wand",
    version = "2.0.0",
    acceptedMinecraftVersions = "[1.12,1.12.2]"
)

public class BuildWandPlugin {

    public static final String MODID = "buildwand";
    private static final Logger logger = LogManager.getLogger(MODID);

    private static BuildWandPlugin instance;
    private Map<UUID, AreaSelection> playerSelections;
    private BuildManager buildManager;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        playerSelections = new HashMap<>();
        buildManager = new BuildManager();

        // Register event listeners
        MinecraftForge.EVENT_BUS.register(new SelectionListener());

        logger.info("Build Wand mod initialized!");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Register commands
        event.registerServerCommand(new WandCommand());
        
        logger.info("Build Wand mod started!");
    }

    public static BuildWandPlugin getInstance() {
        return instance;
    }

    public static Logger getLogger() {
        return logger;
    }

    public Map<UUID, AreaSelection> getPlayerSelections() {
        return playerSelections;
    }

    public AreaSelection getOrCreatePlayerSelection(UUID playerUUID) {
        return playerSelections.computeIfAbsent(playerUUID, k -> new AreaSelection());
    }

    public BuildManager getBuildManager() {
        return buildManager;
    }
}
