package com.volmit.adapt.nms.advancements.advancement.serialized.message;

import java.util.List;
import java.util.Locale;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.KeybindComponent;
import net.md_5.bungee.api.chat.SelectorComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class SerializedMessage {
	
	private final String text;
	private final String selector;
	private final String keybind;
	private final String color;
	private final boolean bold;
	private final boolean italic;
	private final boolean underlined;
	private final HoverEvent hoverEvent;
	private final ClickEvent clickEvent;
	
	private List<SerializedMessage> extra;
	
	public SerializedMessage(String text, String selector, String keybind, String color, boolean bold, boolean italic, boolean underlined, HoverEvent hoverEvent, ClickEvent clickEvent, List<SerializedMessage> extra) {
		this.text = text;
		this.selector = selector;
		this.keybind = keybind;
		this.color = color;
		this.bold = bold;
		this.italic = italic;
		this.underlined = underlined;
		this.hoverEvent = hoverEvent;
		this.clickEvent = clickEvent;
		this.extra = extra;
	}
	
	public String getText() {
		return text;
	}
	
	public String getSelector() {
		return selector;
	}
	
	public String getKeybind() {
		return keybind;
	}
	
	public String getColor() {
		return color;
	}
	
	public boolean isBold() {
		return bold;
	}
	
	public boolean isItalic() {
		return italic;
	}
	
	public boolean isUnderlined() {
		return underlined;
	}
	
	public HoverEvent getHoverEvent() {
		return hoverEvent;
	}
	
	public ClickEvent getClickEvent() {
		return clickEvent;
	}
	
	public List<SerializedMessage> getExtra() {
		return extra;
	}
	
	public BaseComponent deserialize() {
		BaseComponent message = new TextComponent("");
		if(getText() != null && !getText().isEmpty()) {
			message = new TextComponent(getText());
		} else if(getSelector() != null && !getSelector().isEmpty()) {
			message = new SelectorComponent(getSelector());
		} else if(getKeybind() != null && !getKeybind().isEmpty()) {
			message = new KeybindComponent(getKeybind());
		}
		
		if(getColor() != null && !getColor().isEmpty()) {
			message.setColor(ChatColor.of(getColor().toUpperCase(Locale.ROOT)));
		}
		
		message.setBold(isBold());
		message.setItalic(isItalic());
		message.setUnderlined(isUnderlined());
		
		if(getHoverEvent() != null) {
			message.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.valueOf(getHoverEvent().getAction().toUpperCase(Locale.ROOT)), new Text(getHoverEvent().getContents())));
		}
		
		if(getClickEvent() != null) {
			message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.valueOf(getClickEvent().getAction().toUpperCase(Locale.ROOT)), getClickEvent().getValue()));
		}
		
		if(getExtra() != null) {
			for(SerializedMessage extra : getExtra()) {
				message.addExtra(extra.deserialize());
			}
		}
		
		return message;
	}
	
}