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

package com.volmit.adapt.util;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * The <code>TAG_List</code> tag.
 *
 * @author Graham Edgecombe
 */
public final class ListTag extends Tag {

    /**
     * The type.
     * -- GETTER --
     *  Gets the type of item in this list.
     *
     * @return The type of item in this list.

     */
    @Getter
    private final Class<? extends Tag> type;

    /**
     * The value.
     */
    private final List<Tag> value;

    /**
     * Creates the tag.
     *
     * @param name  The name.
     * @param type  The type of item in the list.
     * @param value The value.
     */
    public ListTag(String name, Class<? extends Tag> type, List<Tag> value) {
        super(name);
        this.type = type;
        this.value = Collections.unmodifiableList(value);
    }

    @Override
    public List<Tag> getValue() {
        return value;
    }

    @Override
    public String toString() {
        String name = getName();
        String append = "";
        if (name != null && !name.isEmpty()) {
            append = "(\"" + this.getName() + "\")";
        }
        StringBuilder bldr = new StringBuilder();
        bldr.append("TAG_List").append(append).append(": ").append(value.size()).append(" entries of type ").append(NBTUtils.getTypeName(type)).append("\r\n{\r\n");
        for (Tag t : value) {
            bldr.append("   ").append(t.toString().replaceAll("\r\n", "\r\n   ")).append("\r\n");
        }
        bldr.append("}");
        return bldr.toString();
    }

}
