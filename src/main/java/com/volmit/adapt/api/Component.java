package com.volmit.adapt.api;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public interface Component
{
    /**
     * Takes a custom amount of the item stack exact type (Ignores the item amount)
     * @param inv the inv
     * @param is the item ignore the amount
     * @param amount the amount to use
     * @return true if taken, false if not (missing)
     */
    default boolean takeAll(Inventory inv, ItemStack is, int amount)
    {
        ItemStack isf = is.clone();
        isf.setAmount(amount);
        return takeAll(inv, is);
    }

    /**
     * Take one of an exact type ignoring the item stack amount
     * @param inv the inv
     * @param is the item ignoring the amount
     * @return true if taken, false if diddnt
     */
    default boolean takeOne(Inventory inv, ItemStack is, int amount)
    {
        return takeAll(inv, is, 1);
    }

    /**
     * Take a specific amount of an EXACT META TYPE from an inventory
     * @param inv the inv
     * @param is uses the amount
     * @return returns false if it couldnt get enough (and none was taken)
     */
    default boolean takeAll(Inventory inv, ItemStack is)
    {
        ItemStack[] items = inv.getStorageContents();

        int take = is.getAmount();

        for(int ii = 0; ii < items.length; ii++)
        {
            ItemStack i = items[ii];

            if(i == null)
            {
                continue;
            }

            if(i.isSimilar(is))
            {
                if(take > i.getAmount())
                {
                    i.setAmount(i.getAmount() - take);
                    items[ii] = i;
                    take = 0;
                    break;
                }

                else
                {
                    items[ii] = null;
                    take -= i.getAmount();
                }
            }
        }

        if(take > 0)
        {
            return false;
        }

        inv.setStorageContents(items);
        return true;
    }
}
