
package com.custommenu.handlers;

import com.custommenu.CustomMenuMod;
import com.custommenu.config.MenuConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class KeyHandler {
    public static KeyBinding menuKey;

    public static void init() {
        menuKey = new KeyBinding("key.custommenu.open", MenuConfig.menuKey, "key.categories.misc");
        ClientRegistry.registerKeyBinding(menuKey);
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (menuKey.isPressed()) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                if (MenuConfig.getMenu("default") != null) {
                    player.openGui(CustomMenuMod.instance, 0, player.world, (int) player.posX, (int) player.posY, (int) player.posZ);
                }
            }
        }
    }
}
