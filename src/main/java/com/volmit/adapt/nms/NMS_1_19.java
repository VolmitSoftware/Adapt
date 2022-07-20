package com.volmit.adapt.nms;

import art.arcane.nbtson.NBTSon;
import art.arcane.nbtson.tag.CompoundTag;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.volmit.adapt.util.BukkitGson;
import net.minecraft.nbt.MojangsonParser;
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

public class NMS_1_19 implements NMS.Impl {

    public <T> T readItemData(ItemStack stack, Class<T> dataType) {
        NBTTagCompound t = CraftItemStack.asNMSCopy(stack).v();
        if(t.f())
            return null;
        return NBTSon.fromSNBT(t.toString(), dataType);
    }

    public <T> ItemStack writeItemData(ItemStack stack, T data) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        nms.c(usableToNotchian(NBTSon.toNBT(data)));
        return CraftItemStack.asBukkitCopy(nms);
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

    private NBTTagCompound usableToNotchian(CompoundTag tag) {
        try {
            return MojangsonParser.a(tag.valueToString());
        } catch(CommandSyntaxException idgaf) {
            return new NBTTagCompound();
        }
    }
}
