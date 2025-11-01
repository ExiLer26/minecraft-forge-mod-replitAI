
package com.custommenu;

import com.custommenu.commands.MenuCommand;
import com.custommenu.config.MenuConfig;
import com.custommenu.handlers.KeyHandler;
import com.custommenu.network.PacketOpenMenu;
import com.custommenu.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(modid = CustomMenuMod.MODID, name = CustomMenuMod.NAME, version = CustomMenuMod.VERSION)
public class CustomMenuMod {
    public static final String MODID = "custommenu";
    public static final String NAME = "Custom Menu Mod";
    public static final String VERSION = "1.0";

    public static Logger logger;
    public static SimpleNetworkWrapper network;

    @Mod.Instance
    public static CustomMenuMod instance;

    private CommonProxy proxy = new CommonProxy();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        MenuConfig.init(event.getSuggestedConfigurationFile());
        KeyHandler.init();
        
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(PacketOpenMenu.Handler.class, PacketOpenMenu.class, 0, Side.CLIENT);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new MenuCommand());
    }
}
