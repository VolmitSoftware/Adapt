package com.volmit.adapt.nms;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NMS_Default implements NMS.Impl {


    @Override
    public void sendCooldown(Player p, Material m, int tick) { }

    @Override
    public <T> T readItemData(ItemStack stack, Class<T> dataType) { return null; }

    @Override
    public <T> ItemStack writeItemData(ItemStack stack, T data) { return null; }
}
