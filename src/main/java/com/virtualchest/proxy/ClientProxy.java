package com.virtualchest.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Client-specific pre-initialization
    }
    
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Client-specific initialization
    }
}