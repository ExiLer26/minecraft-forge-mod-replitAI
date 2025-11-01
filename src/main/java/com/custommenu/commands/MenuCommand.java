
package com.custommenu.commands;

import com.custommenu.CustomMenuMod;
import com.custommenu.config.MenuConfig;
import com.custommenu.network.PacketOpenMenu;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class MenuCommand extends CommandBase {
    @Override
    public String getName() {
        return "menu";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/menu <create|open|add|remove|delete|list|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 1;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("§cBu komut sadece oyuncular tarafından kullanılabilir!"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("§cKullanım: " + getUsage(sender)));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("§cKullanım: /menu create <isim> [slot sayısı] [başlık]"));
                    return;
                }
                String menuName = args[1];
                int slots = args.length >= 3 ? parseSlots(args[2]) : 27;
                StringBuilder titleBuilder = new StringBuilder();
                if (args.length >= 4) {
                    for (int i = 3; i < args.length; i++) {
                        titleBuilder.append(args[i]).append(" ");
                    }
                } else {
                    titleBuilder.append("Custom Menu");
                }
                String title = titleBuilder.toString().trim();
                
                if (MenuConfig.createMenu(menuName, slots, title)) {
                    sender.sendMessage(new TextComponentString("§aMenu '§e" + menuName + "§a' oluşturuldu!"));
                } else {
                    sender.sendMessage(new TextComponentString("§cMenu oluşturulamadı! (Limit: " + MenuConfig.maxMenus + " veya isim zaten var)"));
                }
                break;

            case "open":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("§cKullanım: /menu open <isim>"));
                    return;
                }
                String openMenuName = args[1];
                if (MenuConfig.getMenu(openMenuName) == null) {
                    sender.sendMessage(new TextComponentString("§cMenu bulunamadı: " + openMenuName));
                    return;
                }
                
                // Client'a packet gönder
                CustomMenuMod.logger.info("Packet gönderiliyor - Menu: " + openMenuName + " Player: " + player.getName());
                try {
                    CustomMenuMod.network.sendTo(new PacketOpenMenu(openMenuName), player);
                    CustomMenuMod.logger.info("Packet başarıyla gönderildi!");
                    sender.sendMessage(new TextComponentString("§aMenu '§e" + openMenuName + "§a' açılıyor..."));
                } catch (Exception e) {
                    CustomMenuMod.logger.error("Packet gönderme hatası!", e);
                    sender.sendMessage(new TextComponentString("§cMenu açılırken hata oluştu!"));
                }
                break;

            case "add":
                if (args.length < 6) {
                    sender.sendMessage(new TextComponentString("§cKullanım: /menu add <menu_ismi> <slot> <item> <isim> <komut>"));
                    return;
                }
                try {
                    String targetMenu = args[1];
                    int slot = Integer.parseInt(args[2]);
                    String item = args[3];
                    String name = args[4];
                    StringBuilder command = new StringBuilder();
                    for (int i = 5; i < args.length; i++) {
                        command.append(args[i]).append(" ");
                    }
                    
                    if (MenuConfig.getMenu(targetMenu) == null) {
                        sender.sendMessage(new TextComponentString("§cMenu bulunamadı: " + targetMenu));
                        return;
                    }
                    
                    MenuConfig.addMenuItem(targetMenu, slot, item, name, command.toString().trim());
                    sender.sendMessage(new TextComponentString("§aItem eklendi!"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(new TextComponentString("§cGeçersiz slot numarası!"));
                }
                break;

            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(new TextComponentString("§cKullanım: /menu remove <menu_ismi> <slot>"));
                    return;
                }
                try {
                    String targetMenu = args[1];
                    int slot = Integer.parseInt(args[2]);
                    
                    if (MenuConfig.getMenu(targetMenu) == null) {
                        sender.sendMessage(new TextComponentString("§cMenu bulunamadı: " + targetMenu));
                        return;
                    }
                    
                    MenuConfig.removeMenuItem(targetMenu, slot);
                    sender.sendMessage(new TextComponentString("§aItem kaldırıldı!"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(new TextComponentString("§cGeçersiz slot numarası!"));
                }
                break;

            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("§cKullanım: /menu delete <isim>"));
                    return;
                }
                String deleteMenuName = args[1];
                if (MenuConfig.deleteMenu(deleteMenuName)) {
                    sender.sendMessage(new TextComponentString("§aMenu '§e" + deleteMenuName + "§a' silindi!"));
                } else {
                    sender.sendMessage(new TextComponentString("§cMenu bulunamadı: " + deleteMenuName));
                }
                break;

            case "list":
                sender.sendMessage(new TextComponentString("§aMevcut Menüler:"));
                for (String name : MenuConfig.menus.keySet()) {
                    MenuConfig.MenuData menu = MenuConfig.getMenu(name);
                    sender.sendMessage(new TextComponentString("§7- §e" + name + " §7(" + menu.title + ", " + menu.slots + " slot)"));
                }
                break;

            case "reload":
                MenuConfig.reloadConfig();
                sender.sendMessage(new TextComponentString("§aConfig yeniden yüklendi!"));
                break;

            default:
                sender.sendMessage(new TextComponentString("§cBilinmeyen komut: " + args[0]));
                break;
        }
    }

    private int parseSlots(String input) {
        try {
            int slots = Integer.parseInt(input);
            if (slots < 9) return 9;
            if (slots > 54) return 54;
            return (slots / 9) * 9;
        } catch (NumberFormatException e) {
            return 27;
        }
    }
}
