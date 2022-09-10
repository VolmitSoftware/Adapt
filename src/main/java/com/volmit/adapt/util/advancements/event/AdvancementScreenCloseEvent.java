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

package com.volmit.adapt.util.advancements.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a Player closes their Advancement Screen
 *
 * @author Axel
 */
public class AdvancementScreenCloseEvent extends Event {

    public static final HandlerList handlers = new HandlerList();
    private final Player player;

    /**
     * Constructor for instantiating this Event
     *
     * @param player The Player closing their Advancement Screen
     */
    public AdvancementScreenCloseEvent(Player player) {
        super(true);
        this.player = player;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Gets the Player closing their Advancement Screen
     *
     * @return The Player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets an informative String for debugging purposes
     *
     * @return Information about this event
     */
    public String getInformationString() {
        return "tab_action=close;player=" + player.getName();
    }

}