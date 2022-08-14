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

package com.volmit.adapt.util.advancements.advancement.serialized;

import com.volmit.adapt.util.advancements.advancement.serialized.message.SerializedMessage;

public class SerializedAdvancementDisplay {
	
	private final String icon;
	private final SerializedMessage title;
	private final SerializedMessage description;
	private final String frame;
	private final String visibility;
	private final String backgroundTexture;
	private final float x;
	private final float y;
	
	public SerializedAdvancementDisplay(String icon, SerializedMessage title, SerializedMessage description, String frame, String visibility, String backgroundTexture, float x, float y) {
		this.icon = icon;
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.visibility = visibility;
		this.backgroundTexture = backgroundTexture;
		this.x = x;
		this.y = y;
	}
	
	public String getIcon() {
		return icon;
	}
	
	public SerializedMessage getTitle() {
		return title;
	}
	
	public SerializedMessage getDescription() {
		return description;
	}
	
	public String getFrame() {
		return frame;
	}
	
	public String getVisibility() {
		return visibility;
	}
	
	public String getBackgroundTexture() {
		return backgroundTexture;
	}
	
	public float getX() {
		return x;
	}
	
	public float getY() {
		return y;
	}
	
}