package com.virtualchest.utils;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

public class InventoryUtil {

    /**
     * Save inventory contents to NBT
     * @param inventory The inventory
     * @return NBT compound with inventory data
     */
    public static NBTTagCompound saveItemsToNBT(IInventory inventory) {
        NBTTagCompound compound = new NBTTagCompound();
        NBTTagList itemList = new NBTTagList();
        
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound itemCompound = new NBTTagCompound();
                itemCompound.setInteger("Slot", i);
                stack.writeToNBT(itemCompound);
                itemList.appendTag(itemCompound);
            }
        }
        
        compound.setTag("Items", itemList);
        compound.setInteger("Size", inventory.getSizeInventory());
        return compound;
    }
    
    /**
     * Load inventory contents from NBT
     * @param inventory The inventory
     * @param compound The NBT compound
     */
    public static void loadItemsFromNBT(IInventory inventory, NBTTagCompound compound) {
        // Clear the inventory first
        clearInventory(inventory);
        
        if (compound.hasKey("Items", Constants.NBT.TAG_LIST)) {
            NBTTagList itemList = compound.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            
            for (int i = 0; i < itemList.tagCount(); i++) {
                NBTTagCompound itemCompound = itemList.getCompoundTagAt(i);
                int slot = itemCompound.getInteger("Slot");
                
                if (slot >= 0 && slot < inventory.getSizeInventory()) {
                    ItemStack stack = new ItemStack(itemCompound);
                    inventory.setInventorySlotContents(slot, stack);
                }
            }
        }
    }
    
    /**
     * Clear an inventory
     * @param inventory The inventory
     */
    public static void clearInventory(IInventory inventory) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            inventory.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }
    
    /**
     * Get the size of an inventory
     * @param inventory The inventory
     * @return The number of slots
     */
    public static int getInventorySize(IInventory inventory) {
        return inventory.getSizeInventory();
    }
}