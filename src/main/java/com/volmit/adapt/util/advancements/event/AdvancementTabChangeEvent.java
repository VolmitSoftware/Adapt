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

import com.volmit.adapt.util.advancements.NameKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a Player opens their Advancement Screen or changes their Advancement Tab
 *
 * @author Axel
 */
public class AdvancementTabChangeEvent extends Event implements Cancellable {

    public static final HandlerList handlers = new HandlerList();
    private final Player player;
    private NameKey tabAdvancement;
    private boolean cancelled;

    /**
     * Constructor for instantiating this Event
     *
     * @param player         The Player
     * @param tabAdvancement The selected Tab
     */
    public AdvancementTabChangeEvent(Player player, NameKey tabAdvancement) {
        super(true);
        this.player = player;
        this.tabAdvancement = tabAdvancement;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Gets the Player
     *
     * @return The Player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the selected Tab
     *
     * @return The selected Tab
     */
    public NameKey getTabAdvancement() {
        return tabAdvancement;
    }

    /**
     * Changes the tab the player is changing to
     *
     * @param tabAdvancement The new tab the player will change to
     */
    public void setTabAdvancement(NameKey tabAdvancement) {
        this.tabAdvancement = tabAdvancement;
    }


    /**
     * Gets an informative String for debugging purposes
     *
     * @return Information about this event
     */
    public String getInformationString() {
        return "tab_action=change;player=" + player.getName() + ";tab=" + tabAdvancement.toString() + ",cancelled=" + cancelled;
    }

}