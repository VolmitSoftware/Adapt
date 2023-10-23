package com.volmit.adapt.nms.advancements.advancement;

import java.util.Arrays;

import org.bukkit.entity.Player;

/**
 * Represents the conditions under which an Advancement is visible
 * 
 * @author Axel
 *
 */
public abstract class AdvancementVisibility {
	
	/**
	 * Advancements with this Visibility will always be visible
	 */
	public static final AdvancementVisibility ALWAYS = new AdvancementVisibility("ALWAYS") {
		
		@Override
		public boolean isVisible(Player player, Advancement advancement) {
			return true;
		}
	};
	
	/**
	 * Advancements with this Visibility will be visible once their parent or any of their children is granted
	 */
	public static final AdvancementVisibility PARENT_GRANTED = new AdvancementVisibility("PARENT_GRANTED") {
		
		@Override
		public boolean isVisible(Player player, Advancement advancement) {
			if(advancement.isGranted(player)) return true;
			Advancement parent = advancement.getParent();
			
			return parent == null || parent.isGranted(player);
		}
	};
	
	/**
	 * Advancements with this Visibility will be visible once their parent or grandparent or any of their children is granted (Similar to Vanilla behavior)
	 */
	public static final AdvancementVisibility VANILLA = new AdvancementVisibility("VANILLA") {
		
		@Override
		public boolean isVisible(Player player, Advancement advancement) {
			if(advancement.isGranted(player)) return true;
			
			Advancement parent = advancement.getParent();
			
			if(parent != null && !parent.isGranted(player)) {
				Advancement grandParent = parent.getParent();
				
				return grandParent == null || grandParent.getParent() == null || grandParent.isGranted(player);
			}
			
			return true;
		}
	};
	
	/**
	 * Advancements with this Visibility will be visible once they are granted or any of their children is granted (Similar to Vanilla "hidden")
	 */
	public static final AdvancementVisibility HIDDEN = new AdvancementVisibility("HIDDEN") {
		
		@Override
		public boolean isVisible(Player player, Advancement advancement) {
			return advancement.isGranted(player);
		}
	};
	
	private final String name;
	
	/**
	 * Constructor for creating custom Advancement Visibility
	 */
	public AdvancementVisibility() {
		name = "CUSTOM";
	}
	
	private AdvancementVisibility(String name) {
		this.name = name;
	}
	
	/**
	 * Do not call this method directly, use {@link AdvancementVisibility} to get accurate visibility data
	 * 
	 * @param player Player to check
	 * @param advancement Advancement to check
	 * @return true if advancement should be visible
	 */
	public abstract boolean isVisible(Player player, Advancement advancement);
	
	/**
	 * 
	 * @return true if advancement should always be visible if any child is granted, defaults to true
	 */
	public boolean isAlwaysVisibleWhenAnyChildIsGranted() {
		return true;
	}
	
	/**
	 * 
	 * @return Custom Name, only for pre-defined visibilities: {@link #ALWAYS}, {@link #PARENT_GRANTED}, {@link #VANILLA}, {@link #HIDDEN}
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Parses a visibility
	 * 
	 * @param name Visibility Name
	 * @return A visibility with a matching {@link #getName()} or {@link #VANILLA}
	 */
	public static AdvancementVisibility parseVisibility(String name) {
		for(AdvancementVisibility visibility : Arrays.asList(ALWAYS, PARENT_GRANTED, VANILLA, HIDDEN)) {
			if(visibility.getName().equalsIgnoreCase(name)) {
				return visibility;
			}
		}
		return VANILLA;
	}
	
}