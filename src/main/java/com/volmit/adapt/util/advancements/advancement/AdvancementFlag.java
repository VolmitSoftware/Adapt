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

package com.volmit.adapt.util.advancements.advancement;

/**
 * Alters the behavior of Advancements
 *
 * @author Axel
 */
public enum AdvancementFlag {

    /**
     * Advancements with this Flag will display a Toast upon Completion
     */
    SHOW_TOAST,
    /**
     * Advancements with this Flag will broadcast a Message in Chat upon Completion
     */
    DISPLAY_MESSAGE,
    /**
     * Advancements with this Flag will be sent with the hidden boolean set to true allowing the creation of empty Advancement Tabs or to draw lines
     */
    SEND_WITH_HIDDEN_BOOLEAN,

    ;

    /**
     * Shorthand for combining Toast Notifications and Chat Messages
     */
    public static final AdvancementFlag[] TOAST_AND_MESSAGE = new AdvancementFlag[]{SHOW_TOAST, DISPLAY_MESSAGE};

}