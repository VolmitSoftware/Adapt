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

import net.minecraft.nbt.NBTCompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class NMS_1_20_1 implements NMS.Impl {

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



}
