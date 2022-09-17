/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.nms;

import art.arcane.curse.Curse;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutSetCooldown;
import net.minecraft.world.item.Item;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

public class NMS_1_19_2 implements NMS.Impl {
    @Override
    public String serializeStack(ItemStack is) {
        try {
            Object t = Curse.on(Class.forName("net.minecraft.nbt.NBTTagCompound")).construct();
            Object nmsStack = Curse.on(Class.forName("org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack"))
                .method("asNMSCopy", ItemStack.class)
                .invoke(t, is);
            Curse.on(nmsStack).method("b", Class.forName("net.minecraft.nbt.NBTTagCompound")).invoke(t);
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            Curse.on(Class.forName("net.minecraft.nbt.NBTCompressedStreamTools")).method("a", Class.forName("net.minecraft.nbt.NBTTagCompound"), OutputStream.class).invoke(t, boas);
            return Base64.getUrlEncoder().encodeToString(boas.toByteArray());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ItemStack deserializeStack(String s) {
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(Base64.getUrlDecoder().decode(s));
            Object t = Curse.on(Class.forName("net.minecraft.nbt.NBTCompressedStreamTools")).method("a", InputStream.class).invoke(bin);
            Object nmsCopy = Curse.on(Class.forName("net.minecraft.world.item.ItemStack")).method("a", Class.forName("net.minecraft.nbt.NBTTagCompound")).invoke(t);
            return Curse.on(Class.forName("org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack")).method("asBukkitCopy", Class.forName("net.minecraft.world.item.ItemStack")).invoke(nmsCopy);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendCooldown(Player p, Material m, int tick) {
        sendPacket(p, new PacketPlayOutSetCooldown(notchianItem(m), tick));
    }

    private void sendPacket(Player player, Packet<?> packet) {
        try {
            Object cp = Curse.on(Class.forName("org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer")).method("getHandle").invoke(player);
            Object f = Curse.on(cp).get("b");
            Curse.on(f).method("a", Class.forName("net.minecraft.network.protocol.Packet")).invoke(packet);
        }

        catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private Item notchianItem(Material m) {
        try {
            return Curse.on(Class.forName("org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers")).method("getItem", Material.class).invoke(m);
        }

        catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
