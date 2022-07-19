package com.volmit.adapt.nms;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NMS_Default implements NMS.Impl {

    @Override
    public String serializeStack(ItemStack is) {
        return null;
    }

    @Override
    public ItemStack deserializeStack(String s) {
        return null;
    }

    @Override
    public void sendCooldown(Player p, Material m, int tick) {

    }
}
