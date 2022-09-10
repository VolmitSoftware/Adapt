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

package com.volmit.adapt.util.advancements.advancement.serialized.message;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.KeybindComponent;
import net.md_5.bungee.api.chat.SelectorComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.List;
import java.util.Locale;

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
        if (getText() != null && !getText().isEmpty()) {
            message = new TextComponent(getText());
        } else if (getSelector() != null && !getSelector().isEmpty()) {
            message = new SelectorComponent(getSelector());
        } else if (getKeybind() != null && !getKeybind().isEmpty()) {
            message = new KeybindComponent(getKeybind());
        }

        if (getColor() != null && !getColor().isEmpty()) {
            message.setColor(ChatColor.of(getColor().toUpperCase(Locale.ROOT)));
        }

        message.setBold(isBold());
        message.setItalic(isItalic());
        message.setUnderlined(isUnderlined());

        if (getHoverEvent() != null) {
            message.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.valueOf(getHoverEvent().getAction().toUpperCase(Locale.ROOT)), new Text(getHoverEvent().getContents())));
        }

        if (getClickEvent() != null) {
            message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.valueOf(getClickEvent().getAction().toUpperCase(Locale.ROOT)), getClickEvent().getValue()));
        }

        if (getExtra() != null) {
            for (SerializedMessage extra : getExtra()) {
                message.addExtra(extra.deserialize());
            }
        }

        return message;
    }

}