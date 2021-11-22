package com.volmit.adapt.api;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public interface Component
{
    /**
     * Take a specific amount of an EXACT META TYPE from an inventory
     * @param inv
     * @param is uses the amount
     * @return returns false if it couldnt get enough (and none was taken)
     */
    default boolean takeExactly(Inventory inv, ItemStack is)
    {
        ItemStack[] items = inv.getStorageContents();

        int take = is.getAmount();

        for(ItemStack i : items)
        {
            if(i == null)
            {
                continue;
            }

            if(i.isSimilar(is))
            {
                if(take > i.getAmount())
                {
                    i.setAmount(i.getAmount() - take);
                    take = 0;
                    break;
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
