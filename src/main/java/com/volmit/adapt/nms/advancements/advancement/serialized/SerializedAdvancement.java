package com.volmit.adapt.nms.advancements.advancement.serialized;

import java.util.List;

import com.volmit.adapt.nms.advancements.CrazyAdvancementsAPI;
import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.AdvancementFunctionReward;

public class SerializedAdvancement {
	
	private final transient NameKey name;
	private final SerializedAdvancementDisplay display;
	private final SerializedCriteria criteria;
	private final AdvancementFunctionReward reward;
	private final NameKey parent;
	private final List<String> flags;
	
	public SerializedAdvancement(NameKey name, SerializedAdvancementDisplay display, SerializedCriteria criteria, AdvancementFunctionReward reward, NameKey parent, List<String> flags) {
		this.name = name;
		this.display = display;
		this.criteria = criteria;
		this.reward = reward;
		this.parent = parent;
		this.flags = flags;
	}
	
	public NameKey getName() {
		return name;
	}
	
	public SerializedAdvancementDisplay getDisplay() {
		return display;
	}
	
	public SerializedCriteria getCriteria() {
		return criteria;
	}
	
	public AdvancementFunctionReward getReward() {
		return reward;
	}
	
	public NameKey getParent() {
		return parent;
	}
	
	public List<String> getFlags() {
		return flags;
	}
	
	public String toJson() {
		return CrazyAdvancementsAPI.getGson().toJson(this);
	}
	
}