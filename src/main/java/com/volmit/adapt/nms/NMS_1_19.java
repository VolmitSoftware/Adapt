package com.volmit.adapt.nms;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutSetCooldown;
import net.minecraft.world.item.Item;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;

public class NMS_1_19 implements NMS.Impl {

    @Override
    public void sendCooldown(Player p, Material m, int tick) {
        sendPacket(p, new PacketPlayOutSetCooldown(notchianItem(m), tick));
    }

    private void sendPacket(Player player, Packet<?> packet) {
        ((CraftPlayer)player).getHandle().b.a(packet);
    }

    private Item notchianItem(Material m) {
        return CraftMagicNumbers.getItem(m);
    }
}
