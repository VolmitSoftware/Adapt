package com.volmit.adapt.nms.advancements.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.PacketPlayOutAdvancements;
import net.minecraft.resources.MinecraftKey;

/**
 * Represents an Advancements Packet
 * 
 * @author Axel
 *
 */
public class AdvancementsPacket {
	
	private final Player player;
	private final boolean reset;
	private final List<Advancement> advancements;
	private final List<NameKey> removedAdvancements;
	
	/**
	 * Constructor for creating Advancement Packets
	 * 
	 * @param player The target Player
	 * @param reset Whether the Client will clear the Advancement Screen before adding the Advancements
	 * @param advancements A list of advancements that should be added to the Advancement Screen
	 * @param removedAdvancements A list of NameKeys which should be removed from the Advancement Screen
	 */
	public AdvancementsPacket(Player player, boolean reset, List<Advancement> advancements, List<NameKey> removedAdvancements) {
		this.player = player;
		this.reset = reset;
		this.advancements = advancements == null ? new ArrayList<>() : new ArrayList<>(advancements);
		this.removedAdvancements = removedAdvancements == null ? new ArrayList<>() : new ArrayList<>(removedAdvancements);
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
	 * Gets whether the Client will clear the Advancement Screen before adding the Advancements
	 * 
	 * @return Whether the Screen should reset
	 */
	public boolean isReset() {
		return reset;
	}
	
	/**
	 * Gets a copy of the list of the added Advancements
	 * 
	 * @return The list containing the added Advancements
	 */
	public List<Advancement> getAdvancements() {
		return new ArrayList<>(advancements);
	}
	
	/**
	 * Gets a copy of the list of the removed Advancement's NameKeys
	 * 
	 * @return The list containing the removed Advancement's NameKeys
	 */
	public List<NameKey> getRemovedAdvancements() {
		return new ArrayList<>(removedAdvancements);
	}
	
	/**
	 * Builds a packet that can be sent to a Player
	 * 
	 * @return The Packet
	 */
	public PacketPlayOutAdvancements build() {
		//Create Lists
		List<net.minecraft.advancements.AdvancementHolder> advancements = new ArrayList<>();
		Set<MinecraftKey> removedAdvancements = new HashSet<>();
		Map<MinecraftKey, AdvancementProgress> progress = new HashMap<>();
		
		//Populate Lists
		for(Advancement advancement : this.advancements) {
			net.minecraft.advancements.Advancement nmsAdvancement = convertAdvancement(advancement);
			advancements.add(new AdvancementHolder(advancement.getName().getMinecraftKey(), nmsAdvancement));
			progress.put(advancement.getName().getMinecraftKey(), advancement.getProgress(getPlayer()).getNmsProgress());
		}
		for(NameKey removed : this.removedAdvancements) {
			removedAdvancements.add(removed.getMinecraftKey());
		}
		
		//Create Packet
		PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(isReset(), advancements, removedAdvancements, progress);
		return packet;
	}
	
	protected net.minecraft.advancements.Advancement convertAdvancement(Advancement advancement) {
		return PacketConverter.toNmsAdvancement(advancement);
	}
	
	/**
	 * Sends the Packet to the target Player
	 * 
	 */
	public void send() {
		PacketPlayOutAdvancements packet = build();
		((CraftPlayer) getPlayer()).getHandle().c.a(packet, null);
	}
	
	
}