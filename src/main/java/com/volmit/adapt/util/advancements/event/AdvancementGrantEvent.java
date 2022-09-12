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
import com.volmit.adapt.util.advancements.advancement.AdvancementFlag;
import com.volmit.adapt.util.advancements.manager.AdvancementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AdvancementGrantEvent extends Event {

    public static final HandlerList handlers = new HandlerList();
    private final AdvancementManager manager;
    private final Advancement advancement;
    private final Player player;
    private boolean showToast;
    private boolean displayMessage;

    public AdvancementGrantEvent(AdvancementManager manager, Advancement advancement, Player player) {
        this.manager = manager;
        this.advancement = advancement;
        this.player = player;
        this.showToast = advancement.hasFlag(AdvancementFlag.SHOW_TOAST);
        this.displayMessage = advancement.hasFlag(AdvancementFlag.DISPLAY_MESSAGE);
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

    /**
     * Gets whether a toast will be shown
     *
     * @return true if a toast will be shown
     */
    public boolean isShowToast() {
        return showToast;
    }

    /**
     * Sets if a toast will be shown
     *
     * @param showToast Whether toast should be shown
     */
    public void setShowToast(boolean showToast) {
        this.showToast = showToast;
    }

    /**
     * Gets whether a message will be displayed
     *
     * @return true if a message will be displayed
     */
    public boolean isDisplayMessage() {
        return displayMessage;
    }

    /**
     * Sets if a message will be displayed
     *
     * @param displayMessage Whethere message should be displayed
     */
    public void setDisplayMessage(boolean displayMessage) {
        this.displayMessage = displayMessage;
    }

}