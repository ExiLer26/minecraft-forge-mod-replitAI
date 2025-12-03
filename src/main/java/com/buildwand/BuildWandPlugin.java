
package com.buildwand;

import com.buildwand.commands.WandCommand;
import com.buildwand.listeners.SelectionListener;
import com.buildwand.utils.AreaSelection;
import com.buildwand.utils.BuildManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod("buildwand")
public class BuildWandPlugin {

    public static final String MODID = "buildwand";
    private static final Logger logger = LogManager.getLogger(MODID);

    private static BuildWandPlugin instance;
    private Map<UUID, AreaSelection> playerSelections;
    private BuildManager buildManager;

    public BuildWandPlugin() {
        instance = this;
        
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        playerSelections = new HashMap<>();
        buildManager = new BuildManager();

        // Register event listeners
        MinecraftForge.EVENT_BUS.register(new SelectionListener());

        logger.info("Build Wand mod initialized!");
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // Register commands
        WandCommand.register(event.getCommandDispatcher());
        
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
