
package com.custommenu.network;

import com.custommenu.CustomMenuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketOpenMenu implements IMessage {
    private String menuName;

    public PacketOpenMenu() {}

    public PacketOpenMenu(String menuName) {
        this.menuName = menuName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.menuName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, menuName);
    }

    public static class Handler implements IMessageHandler<PacketOpenMenu, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketOpenMenu message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                EntityPlayer player = mc.player;
                if (player != null) {
                    CustomMenuMod.logger.info("Packet alındı, menu açılıyor: " + message.menuName);
                    int guiId = message.menuName.equals("default") ? 0 : message.menuName.hashCode();
                    player.openGui(CustomMenuMod.instance, guiId, player.world, 
                        (int) player.posX, (int) player.posY, (int) player.posZ);
                }
            });
            return null;
        }
    }
}
