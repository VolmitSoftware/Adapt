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

import com.volmit.adapt.util.advancements.advancement.Advancement;
import com.volmit.adapt.util.advancements.manager.AdvancementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AdvancementRevokeEvent extends Event {

    public static final HandlerList handlers = new HandlerList();
    private final AdvancementManager manager;
    private final Advancement advancement;
    private final Player player;

    public AdvancementRevokeEvent(AdvancementManager manager, Advancement advancement, Player player) {
        this.manager = manager;
        this.advancement = advancement;
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
     * Gets the Manager
     *
     * @return The Manager this event has been fired from
     */
    public AdvancementManager getManager() {
        return manager;
    }

    /**
     * Gets the Advancement
     *
     * @return The Advancement that has been granted
     */
    public Advancement getAdvancement() {
        return advancement;
    }

    /**
     * Gets the Player
     *
     * @return Reciever
     */
    public Player getPlayer() {
        return player;
    }

}