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

package com.volmit.adapt.util.advancements.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.volmit.adapt.util.advancements.advancement.ToastNotification;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.PacketPlayOutAdvancements;
import net.minecraft.resources.MinecraftKey;

/**
 * Represents an Advancements Packet for Toast Notifications
 * 
 * @author Axel
 *
 */
public class ToastPacket {
	
	private final Player player;
	private final boolean add;
	private final ToastNotification notification;
	
	/**
	 * Constructor for creating Toast Packets
	 * 
	 * @param player The target Player
	 * @param add Whether to add or remove the Advancement
	 * @param notification The Notification
	 */
	public ToastPacket(Player player, boolean add, ToastNotification notification) {
		this.player = player;
		this.add = add;
		this.notification = notification;
	}
	
	/**
	 * Gets the target Player
	 * 
	 * @return The target Player
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * Gets whether the Advancement is added or removed
	 * 
	 * @return Whether the Advancement is added or removed
	 */
	public boolean isAdd() {
		return add;
	}
	
	/**
	 * Gets the Notification
	 * 
	 * @return The Notification
	 */
	public ToastNotification getNotification() {
		return notification;
	}
	
	/**
	 * Builds a packet that can be sent to a Player
	 * 
	 * @return The Packet
	 */
	public PacketPlayOutAdvancements build() {
		//Create Lists
		List<net.minecraft.advancements.Advancement> advancements = new ArrayList<>();
		Set<MinecraftKey> removedAdvancements = new HashSet<>();
		Map<MinecraftKey, AdvancementProgress> progress = new HashMap<>();
		
		//Populate Lists
		if(add) {
			advancements.add(PacketConverter.toNmsToastAdvancement(getNotification()));
			progress.put(ToastNotification.NOTIFICATION_NAME.getMinecraftKey(), ToastNotification.NOTIFICATION_PROGRESS.getNmsProgress());
		} else {
			removedAdvancements.add(ToastNotification.NOTIFICATION_NAME.getMinecraftKey());
		}
		
		//Create Packet
		PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advancements, removedAdvancements, progress);
		return packet;
	}
	
	/**
	 * Sends the Packet to the target Player
	 * 
	 */
	public void send() {
		PacketPlayOutAdvancements packet = build();
		((CraftPlayer) getPlayer()).getHandle().b.a(packet);
	}
	
	
	
	
}