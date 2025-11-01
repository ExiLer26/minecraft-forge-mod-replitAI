
package com.custommenu.gui;

import com.custommenu.config.MenuConfig;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

public class CustomMenuGui extends GuiContainer {
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    private final MenuConfig.MenuData menuData;

    public CustomMenuGui(EntityPlayer player, String menuName) {
        super(new CustomMenuContainer(player, menuName));
        this.menuData = MenuConfig.getMenu(menuName);
        this.ySize = getGuiHeight();
    }

    private int getGuiHeight() {
        int rows = menuData.slots / 9;
        return 114 + rows * 18;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(GUI_TEXTURE);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        int rows = menuData.slots / 9;
        
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, rows * 18 + 17);
        this.drawTexturedModalRect(x, y + rows * 18 + 17, 0, 126, this.xSize, 96);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString(menuData.title, 8, 6, 4210752);
        this.fontRenderer.drawString("Envanter", 8, this.ySize - 96 + 2, 4210752);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX + MenuConfig.tooltipOffsetX, mouseY + MenuConfig.tooltipOffsetY);
    }

    @Override
    protected void handleMouseClick(Slot slotIn, int slotId, int mouseButton, ClickType type) {
        if (slotIn instanceof MenuSlot) {
            MenuSlot menuSlot = (MenuSlot) slotIn;
            if (menuSlot.menuItem != null && !menuSlot.menuItem.command.isEmpty()) {
                String command = menuSlot.menuItem.command;
                if (!command.startsWith("/")) {
                    command = "/" + command;
                }
                this.mc.player.sendChatMessage(command);
            }
            return;
        }
        super.handleMouseClick(slotIn, slotId, mouseButton, type);
    }

    public static class CustomMenuContainer extends Container {
        private final EntityPlayer player;
        private final MenuConfig.MenuData menuData;

        public CustomMenuContainer(EntityPlayer player, String menuName) {
            this.player = player;
            this.menuData = MenuConfig.getMenu(menuName);
            
            if (this.menuData == null) {
                return;
            }
            
            int rows = menuData.slots / 9;
            
            for (MenuConfig.MenuItem menuItem : menuData.items) {
                if (menuItem.slot < menuData.slots) {
                    int row = menuItem.slot / 9;
                    int col = menuItem.slot % 9;
                    this.addSlotToContainer(new MenuSlot(menuItem, col * 18 + 8, row * 18 + 18));
                }
            }

            int yOffset = rows * 18 + 31;
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 9; ++j) {
                    this.addSlotToContainer(new Slot(player.inventory, j + i * 9 + 9, 8 + j * 18, yOffset + i * 18));
                }
            }

            for (int i = 0; i < 9; ++i) {
                this.addSlotToContainer(new Slot(player.inventory, i, 8 + i * 18, yOffset + 58));
            }
        }

        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return true;
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
            if (slotId >= 0 && slotId < this.inventorySlots.size()) {
                Slot slot = this.inventorySlots.get(slotId);
                if (slot instanceof MenuSlot) {
                    return ItemStack.EMPTY;
                }
            }
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }
    }

    public static class MenuSlot extends Slot {
        public final MenuConfig.MenuItem menuItem;

        public MenuSlot(MenuConfig.MenuItem menuItem, int xPosition, int yPosition) {
            super(new FakeInventory(), 0, xPosition, yPosition);
            this.menuItem = menuItem;
        }

        @Override
        public ItemStack getStack() {
            Item item = Item.getByNameOrId("minecraft:" + menuItem.itemName);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                
                NBTTagCompound nbt = new NBTTagCompound();
                NBTTagCompound display = new NBTTagCompound();
                
                if (menuItem.displayName != null && !menuItem.displayName.isEmpty()) {
                    display.setString("Name", menuItem.displayName);
                }
                
                NBTTagList lore = new NBTTagList();
                lore.appendTag(new NBTTagString("§7Tıkla: §f" + menuItem.command));
                display.setTag("Lore", lore);
                
                nbt.setTag("display", display);
                stack.setTagCompound(nbt);
                
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void putStack(ItemStack stack) {}
        @Override
        public boolean isItemValid(ItemStack stack) { return false; }
        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) { return ItemStack.EMPTY; }
        @Override
        public boolean canTakeStack(EntityPlayer playerIn) { return false; }
        @Override
        public void onSlotChanged() {}
    }

    public static class FakeInventory implements IInventory {
        @Override public int getSizeInventory() { return 0; }
        @Override public boolean isEmpty() { return true; }
        @Override public ItemStack getStackInSlot(int index) { return ItemStack.EMPTY; }
        @Override public ItemStack decrStackSize(int index, int count) { return ItemStack.EMPTY; }
        @Override public ItemStack removeStackFromSlot(int index) { return ItemStack.EMPTY; }
        @Override public void setInventorySlotContents(int index, ItemStack stack) {}
        @Override public int getInventoryStackLimit() { return 0; }
        @Override public void markDirty() {}
        @Override public boolean isUsableByPlayer(EntityPlayer player) { return true; }
        @Override public void openInventory(EntityPlayer player) {}
        @Override public void closeInventory(EntityPlayer player) {}
        @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return false; }
        @Override public int getField(int id) { return 0; }
        @Override public void setField(int id, int value) {}
        @Override public int getFieldCount() { return 0; }
        @Override public void clear() {}
        @Override public String getName() { return "fake"; }
        @Override public boolean hasCustomName() { return false; }
        @Override public net.minecraft.util.text.ITextComponent getDisplayName() {
            return new TextComponentString(getName());
        }
    }
}
