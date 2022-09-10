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

package com.volmit.adapt.util.advancements.packet;

import com.volmit.adapt.util.advancements.NameKey;
import com.volmit.adapt.util.advancements.advancement.Advancement;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.List;

/**
 * Represents an Advancement Packet which respects Advancement Visibility
 *
 * @author Axel
 */
public class VisibilityAdvancementsPacket extends AdvancementsPacket {

    /**
     * Constructor for creating Advancement Packets that respect Advancement Visiblity
     *
     * @param player              The target Player
     * @param reset               Whether the Client will clear the Advancement Screen before adding the Advancements
     * @param advancements        A list of advancements that should be added to the Advancement Screen
     * @param removedAdvancements A list of NameKeys which should be removed from the Advancement Screen
     */
    public VisibilityAdvancementsPacket(Player player, boolean reset, List<Advancement> advancements, List<NameKey> removedAdvancements) {
        super(player, reset, stripInvisibleAdvancements(player, advancements), removedAdvancements);
    }

    private static List<Advancement> stripInvisibleAdvancements(Player player, List<Advancement> advancements) {
        Iterator<Advancement> advancementsIterator = advancements.iterator();

        while (advancementsIterator.hasNext()) {
            Advancement advancement = advancementsIterator.next();
            AdvancementDisplay display = advancement.getDisplay();

            boolean visible = display.isVisible(player, advancement);
            advancement.saveVisibilityStatus(player, visible);
            if (!visible) {
                advancementsIterator.remove();
            }
        }

        return advancements;
    }

}