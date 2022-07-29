package com.volmit.adapt.nms;

import net.minecraft.nbt.NBTCompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutSetCooldown;
import net.minecraft.world.item.Item;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class NMS_1_19_1 implements NMS.Impl {

    @Override
    public String serializeStack(ItemStack is) {
        try {
            NBTTagCompound t = new NBTTagCompound();
            CraftItemStack.asNMSCopy(is).b(t);
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            NBTCompressedStreamTools.a(t, boas);
            return Base64.getUrlEncoder().encodeToString(boas.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ItemStack deserializeStack(String s) {
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(Base64.getUrlDecoder().decode(s));
            NBTTagCompound t = NBTCompressedStreamTools.a(bin);
            return CraftItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.a(t));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendCooldown(Player p, Material m, int tick) {
        sendPacket(p, new PacketPlayOutSetCooldown(notchianItem(m), tick));
    }

    private void sendPacket(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().b.a(packet);
    }

    private Item notchianItem(Material m) {
        return CraftMagicNumbers.getItem(m);
    }
}
