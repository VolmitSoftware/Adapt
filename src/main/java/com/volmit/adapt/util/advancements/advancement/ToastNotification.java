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

import com.volmit.adapt.util.advancements.CrazyAdvancementsAPI;
import com.volmit.adapt.util.advancements.JSONMessage;
import com.volmit.adapt.util.advancements.NameKey;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay.AdvancementFrame;
import com.volmit.adapt.util.advancements.advancement.criteria.Criteria;
import com.volmit.adapt.util.advancements.advancement.progress.AdvancementProgress;
import com.volmit.adapt.util.advancements.packet.ToastPacket;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a Toast Notification
 *
 * @author Axel
 */
public class ToastNotification {

    public static final NameKey NOTIFICATION_NAME = new NameKey(CrazyAdvancementsAPI.API_NAMESPACE, "notification");
    public static final Criteria NOTIFICATION_CRITERIA = new Criteria(1);
    public static final AdvancementProgress NOTIFICATION_PROGRESS = new AdvancementProgress(NOTIFICATION_CRITERIA.getCriteria(), NOTIFICATION_CRITERIA.getRequirements());

    static {
        NOTIFICATION_PROGRESS.setCriteriaProgress(1);
    }

    private final ItemStack icon;
    private final JSONMessage message;
    private final AdvancementFrame frame;

    /**
     * Constructor for creating Toast Notifications
     *
     * @param icon    The displayed Icon
     * @param message The displayed Message
     * @param frame   Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
     */
    public ToastNotification(ItemStack icon, JSONMessage message, AdvancementFrame frame) {
        this.icon = icon;
        this.message = message;
        this.frame = frame;
    }

    /**
     * Constructor for creating Toast Notifications
     *
     * @param icon    The displayed Icon
     * @param message The displayed Message
     * @param frame   Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
     */
    public ToastNotification(ItemStack icon, String message, AdvancementFrame frame) {
        this.icon = icon;
        this.message = new JSONMessage(new TextComponent(message));
        this.frame = frame;
    }

    /**
     * Constructor for creating Toast Notifications
     *
     * @param icon    The displayed Icon
     * @param message The displayed Message
     * @param frame   Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
     */
    public ToastNotification(Material icon, JSONMessage message, AdvancementFrame frame) {
        this.icon = new ItemStack(icon);
        this.message = message;
        this.frame = frame;
    }

    /**
     * Constructor for creating Toast Notifications
     *
     * @param icon    The displayed Icon
     * @param message The displayed Message
     * @param frame   Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
     */
    public ToastNotification(Material icon, String message, AdvancementFrame frame) {
        this.icon = new ItemStack(icon);
        this.message = new JSONMessage(new TextComponent(message));
        this.frame = frame;
    }

    /**
     * Gets the Icon
     *
     * @return The Icon
     */
    public ItemStack getIcon() {
        return icon;
    }

    /**
     * Gets the TItle
     *
     * @return The Title
     */
    public JSONMessage getMessage() {
        return message;
    }

    /**
     * Gets the Frame
     *
     * @return The Frame
     */
    public AdvancementFrame getFrame() {
        return frame;
    }

    /**
     * Sends this Toast Notification to a Player
     *
     * @param player The target Player
     */
    public void send(Player player) {
        ToastPacket addPacket = new ToastPacket(player, true, this);
        ToastPacket removePacket = new ToastPacket(player, false, this);

        addPacket.send();
        removePacket.send();
    }

}