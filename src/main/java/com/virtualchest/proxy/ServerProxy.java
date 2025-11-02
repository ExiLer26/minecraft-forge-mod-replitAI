package com.virtualchest.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ServerProxy extends CommonProxy {
    
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Server-specific pre-initialization
    }
    
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Server-specific initialization
    }
}