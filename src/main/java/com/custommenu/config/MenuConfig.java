
package com.custommenu.config;

import com.custommenu.CustomMenuMod;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuConfig {
    private static Configuration config;
    public static int menuKey = 50; // M tuşu (LWJGL keycode)
    public static int tooltipOffsetX = 0;
    public static int tooltipOffsetY = 0;
    public static int maxMenus = 10;
    public static Map<String, MenuData> menus = new HashMap<>();

    public static void init(File configFile) {
        config = new Configuration(configFile);
        loadConfig();
    }

    public static void loadConfig() {
        try {
            config.load();

            menuKey = config.getInt("menuKey", "general", 50, 0, 256, "Menu açma tuşu (LWJGL keycode)");
            tooltipOffsetX = config.getInt("tooltipOffsetX", "general", 0, -100, 100, "Tooltip X kayması");
            tooltipOffsetY = config.getInt("tooltipOffsetY", "general", 0, -100, 100, "Tooltip Y kayması");
            maxMenus = config.getInt("maxMenus", "general", 10, 1, 50, "Maksimum menü sayısı");

            String[] menuNames = config.getStringList("menuList", "menus", new String[]{"default"}, "Mevcut menü isimleri");

            menus.clear();
            for (String menuName : menuNames) {
                loadMenu(menuName);
            }

            if (menus.isEmpty()) {
                createDefaultMenu();
            }

        } catch (Exception e) {
            CustomMenuMod.logger.error("Config yükleme hatası!", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    private static void loadMenu(String menuName) {
        String category = "menu_" + menuName;
        
        int slots = config.getInt("slots", category, 27, 9, 54, "Slot sayısı");
        String title = config.getString("title", category, "Custom Menu", "Menü başlığı");
        
        String[] itemsData = config.getStringList("items", category, new String[]{}, "Format: slot:item:name:command");

        List<MenuItem> items = new ArrayList<>();
        for (String itemData : itemsData) {
            try {
                String[] parts = itemData.split(":", 4);
                if (parts.length == 4) {
                    int slot = Integer.parseInt(parts[0]);
                    String item = parts[1];
                    String name = parts[2];
                    String command = parts[3];
                    items.add(new MenuItem(slot, item, name, command));
                }
            } catch (Exception e) {
                CustomMenuMod.logger.error("Item parse hatası: " + itemData, e);
            }
        }

        menus.put(menuName, new MenuData(menuName, slots, title, items));
    }

    private static void createDefaultMenu() {
        List<MenuItem> defaultItems = new ArrayList<>();
        defaultItems.add(new MenuItem(0, "diamond", "§bElmas", "give @p minecraft:diamond 1"));
        defaultItems.add(new MenuItem(1, "emerald", "§aZümrüt", "give @p minecraft:emerald 1"));
        
        MenuData defaultMenu = new MenuData("default", 27, "Custom Menu", defaultItems);
        menus.put("default", defaultMenu);
        saveMenu(defaultMenu);
    }

    public static boolean createMenu(String menuName, int slots, String title) {
        if (menus.size() >= maxMenus) {
            return false;
        }
        
        if (menus.containsKey(menuName)) {
            return false;
        }

        MenuData newMenu = new MenuData(menuName, slots, title, new ArrayList<>());
        menus.put(menuName, newMenu);
        saveMenu(newMenu);
        saveMenuList();
        return true;
    }

    public static boolean deleteMenu(String menuName) {
        if (!menus.containsKey(menuName)) {
            return false;
        }
        
        menus.remove(menuName);
        config.removeCategory(config.getCategory("menu_" + menuName));
        saveMenuList();
        config.save();
        return true;
    }

    public static MenuData getMenu(String menuName) {
        return menus.get(menuName);
    }

    public static void addMenuItem(String menuName, int slot, String item, String name, String command) {
        MenuData menu = menus.get(menuName);
        if (menu != null) {
            menu.items.removeIf(i -> i.slot == slot);
            menu.items.add(new MenuItem(slot, item, name, command));
            saveMenu(menu);
        }
    }

    public static void removeMenuItem(String menuName, int slot) {
        MenuData menu = menus.get(menuName);
        if (menu != null) {
            menu.items.removeIf(i -> i.slot == slot);
            saveMenu(menu);
        }
    }

    private static void saveMenu(MenuData menu) {
        String category = "menu_" + menu.name;
        
        config.get(category, "slots", menu.slots).set(menu.slots);
        config.get(category, "title", menu.title).set(menu.title);
        
        String[] itemsData = new String[menu.items.size()];
        for (int i = 0; i < menu.items.size(); i++) {
            MenuItem item = menu.items.get(i);
            itemsData[i] = item.slot + ":" + item.itemName + ":" + item.displayName + ":" + item.command;
        }
        
        config.getCategory(category).remove("items");
        config.get(category, "items", itemsData).set(itemsData);
        config.save();
    }

    private static void saveMenuList() {
        String[] menuNames = menus.keySet().toArray(new String[0]);
        config.getCategory("menus").remove("menuList");
        config.get("menus", "menuList", menuNames).set(menuNames);
        config.save();
    }

    public static void reloadConfig() {
        loadConfig();
        CustomMenuMod.logger.info("Config yeniden yüklendi!");
    }

    public static class MenuData {
        public String name;
        public int slots;
        public String title;
        public List<MenuItem> items;

        public MenuData(String name, int slots, String title, List<MenuItem> items) {
            this.name = name;
            this.slots = slots;
            this.title = title;
            this.items = items;
        }
    }

    public static class MenuItem {
        public int slot;
        public String itemName;
        public String displayName;
        public String command;

        public MenuItem(int slot, String itemName, String displayName, String command) {
            this.slot = slot;
            this.itemName = itemName;
            this.displayName = displayName;
            this.command = command;
        }
    }
}
