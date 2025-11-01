
package com.custommenu.proxy;

import com.custommenu.config.MenuConfig;
import com.custommenu.gui.CustomMenuGui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import com.custommenu.CustomMenuMod;

public class CommonProxy implements IGuiHandler {
    public void init() {
        NetworkRegistry.INSTANCE.registerGuiHandler(CustomMenuMod.instance, this);
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        CustomMenuMod.logger.info("=== Client GUI requested - ID: " + ID + " ===");
        CustomMenuMod.logger.info("Player: " + player.getName() + " World: " + (world.isRemote ? "CLIENT" : "SERVER"));
        
        // ID 0 = default menu (from key press)
        if (ID == 0) {
            MenuConfig.MenuData defaultMenu = MenuConfig.getMenu("default");
            CustomMenuMod.logger.info("Opening default menu, exists: " + (defaultMenu != null));
            if (defaultMenu != null) {
                CustomMenuMod.logger.info("Returning CustomMenuGui for default");
                return new CustomMenuGui(player, "default");
            }
            CustomMenuMod.logger.warn("Default menu is null!");
            return null;
        }
        
        // Other IDs = hashCode of menu name
        CustomMenuMod.logger.info("Searching for menu with hash: " + ID);
        for (String menuName : MenuConfig.menus.keySet()) {
            int hash = menuName.hashCode();
            CustomMenuMod.logger.info("  Checking menu: '" + menuName + "' with hash: " + hash + " (match: " + (hash == ID) + ")");
            if (hash == ID) {
                MenuConfig.MenuData menuData = MenuConfig.getMenu(menuName);
                CustomMenuMod.logger.info("  Found matching menu: " + menuName + ", data exists: " + (menuData != null));
                if (menuData != null) {
                    CustomMenuMod.logger.info("  Returning CustomMenuGui for " + menuName);
                    return new CustomMenuGui(player, menuName);
                }
            }
        }
        
        CustomMenuMod.logger.warn("=== No GUI found for ID: " + ID + " ===");
        return null;
    }
}
