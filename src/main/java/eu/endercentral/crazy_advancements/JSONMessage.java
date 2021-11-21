package eu.endercentral.crazy_advancements;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatBaseComponent.ChatSerializer;

public class JSONMessage {
	
	private final BaseComponent json;
	
	/**
	 * 
	 * @param json A JSON representation of an ingame Message {@link <a href="https://github.com/skylinerw/guides/blob/master/java/text%20component.md">Read More</a>}
	 */
	public JSONMessage(BaseComponent json) {
		this.json = json;
	}
	
	/**
	 * 
	 * @return the JSON representation of an ingame Message
	 */
	public BaseComponent getJson() {
		return json;
	}
	
	/**
	 * 
	 * @return An {@link IChatBaseComponent} representation of an ingame Message
	 */
	public IChatBaseComponent getBaseComponent() {
		return ChatSerializer.a(ComponentSerializer.toString(json));
	}
	
	@Override
	public String toString() {
		return json.toPlainText();
	}
	
}