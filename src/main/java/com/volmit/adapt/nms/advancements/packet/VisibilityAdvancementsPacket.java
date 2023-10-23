package com.volmit.adapt.nms.advancements.packet;

import java.util.Iterator;
import java.util.List;

import org.bukkit.entity.Player;

import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay;

/**
 * Represents an Advancement Packet which respects Advancement Visibility
 * 
 * @author Axel
 *
 */
public class VisibilityAdvancementsPacket extends AdvancementsPacket {
	
	private static List<Advancement> stripInvisibleAdvancements(Player player, List<Advancement> advancements) {
		Iterator<Advancement> advancementsIterator = advancements.iterator();
		
		while(advancementsIterator.hasNext()) {
			Advancement advancement = advancementsIterator.next();
			AdvancementDisplay display = advancement.getDisplay();
			
			boolean visible = display.isVisible(player, advancement);
			advancement.saveVisibilityStatus(player, visible);
			if(!visible) {
				advancementsIterator.remove();
			}
		}
		
		return advancements;
	}
	
	/**
	 * Constructor for creating Advancement Packets that respect Advancement Visiblity
	 * 
	 * @param player The target Player
	 * @param reset Whether the Client will clear the Advancement Screen before adding the Advancements
	 * @param advancements A list of advancements that should be added to the Advancement Screen
	 * @param removedAdvancements A list of NameKeys which should be removed from the Advancement Screen
	 */
	public VisibilityAdvancementsPacket(Player player, boolean reset, List<Advancement> advancements, List<NameKey> removedAdvancements) {
		super(player, reset, stripInvisibleAdvancements(player, advancements), removedAdvancements);
	}
	
}