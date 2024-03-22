package com.volmit.adapt.nms.advancements.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;

import com.volmit.adapt.nms.advancements.JSONMessage;
import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.nms.advancements.advancement.AdvancementFlag;
import com.volmit.adapt.nms.advancements.advancement.ToastNotification;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.world.item.ItemStack;

public class PacketConverter {

	private static final AdvancementRewards advancementRewards = new AdvancementRewards(0, new ArrayList<>(), new ArrayList<>(), Optional.empty());

	private static HashMap<NameKey, Float> smallestX = new HashMap<>();
	private static HashMap<NameKey, Float> smallestY = new HashMap<>();
	
	public static void setSmallestX(NameKey tab, float smallestX) {
		PacketConverter.smallestX.put(tab, smallestX);
	}
	
	public static float getSmallestX(NameKey key) {
		return smallestX.containsKey(key) ? smallestX.get(key) : 0;
	}
	
	public static void setSmallestY(NameKey tab, float smallestY) {
		PacketConverter.smallestY.put(tab, smallestY);
	}
	
	public static float getSmallestY(NameKey key) {
		return smallestY.containsKey(key) ? smallestY.get(key) : 0;
	}
	
	public static float generateX(NameKey tab, float displayX) {
		return displayX - getSmallestX(tab);
	}
	
	public static float generateY(NameKey tab, float displayY) {
		return displayY - getSmallestY(tab);
	}
	
	/**
	 * Creates an NMS Advancement
	 * 
	 * @param advancement The Advancement to use as a base
	 * @return The NMS Advancement
	 */
	public static net.minecraft.advancements.Advancement toNmsAdvancement(Advancement advancement) {
		AdvancementDisplay display = advancement.getDisplay();
		
		ItemStack icon = CraftItemStack.asNMSCopy(display.getIcon());

		boolean hasBackgroundTexture = display.getBackgroundTexture() != null;
		Optional<MinecraftKey> backgroundTexture = hasBackgroundTexture ? Optional.of(new MinecraftKey(display.getBackgroundTexture())) : Optional.empty();

		float x = generateX(advancement.getTab(), display.generateX());
		float y = generateY(advancement.getTab(), display.generateY());
		
		net.minecraft.advancements.AdvancementDisplay advDisplay = new net.minecraft.advancements.AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), false, false, advancement.hasFlag(AdvancementFlag.SEND_WITH_HIDDEN_BOOLEAN));
		advDisplay.a(x, y);
		
		Optional<MinecraftKey> parent = advancement.getParent() == null ? Optional.empty() : Optional.of(advancement.getParent().getName().getMinecraftKey());
		net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(parent, Optional.of(advDisplay), advancementRewards, advancement.getCriteria().getCriteria(), advancement.getCriteria().getAdvancementRequirements(), false);
		
		return adv;
	}
	
	/**
	 * Creates an NMS Toast Advancement
	 * 
	 * @param notification The Toast Notification to use as a base
	 * @return The NMS Advancement
	 */
	public static net.minecraft.advancements.Advancement toNmsToastAdvancement(ToastNotification notification) {
		ItemStack icon = CraftItemStack.asNMSCopy(notification.getIcon());

		net.minecraft.advancements.AdvancementDisplay advDisplay = new net.minecraft.advancements.AdvancementDisplay(icon, notification.getMessage().getBaseComponent(), new JSONMessage(new TextComponent("Toast Notification")).getBaseComponent(), Optional.empty(), notification.getFrame().getNMS(), true, false, true);
		
		net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(Optional.empty(), Optional.of(advDisplay), advancementRewards, ToastNotification.NOTIFICATION_CRITERIA.getCriteria(), ToastNotification.NOTIFICATION_CRITERIA.getAdvancementRequirements(), false);
		
		return adv;
	}
	
	/**
	 * Creates a Dummy Advancement<br>Internally used to generate temporary parent advancements that need to be referenced in the packet
	 * 
	 * @param name The name of the Advancement
	 * @return the Dummy Advancement
	 * @deprecated No longer required for parent dummies. Might be removed in a future version.
	 */
	@Deprecated(forRemoval = true, since = "2.1.15")
	public static net.minecraft.advancements.Advancement createDummy(NameKey name) {
		net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(Optional.empty(), Optional.empty(), null, new HashMap<>(), new AdvancementRequirements(new ArrayList<>()), false);
		return adv;
	}
	
}