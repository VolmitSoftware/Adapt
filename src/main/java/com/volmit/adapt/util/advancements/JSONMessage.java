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

package com.volmit.adapt.util.advancements;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatBaseComponent.ChatSerializer;

/**
 * Represents a Message in JSON Format
 * 
 * @author Axel
 *
 */
public class JSONMessage {
	
	private final BaseComponent json;
	
	/**
	 * Constructor for creating a JSON Message
	 * 
	 * @param json A JSON representation of an ingame Message <a href="https://www.spigotmc.org/wiki/the-chat-component-api/">Read More</a>
	 */
	public JSONMessage(BaseComponent json) {
		this.json = json;
	}
	
	/**
	 * Gets the Message as a BaseComponent
	 * 
	 * @return the BaseComponent of an ingame Message
	 */
	public BaseComponent getJson() {
		return json;
	}
	
	/**
	 * Gets an NMS representation of an ingame Message
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