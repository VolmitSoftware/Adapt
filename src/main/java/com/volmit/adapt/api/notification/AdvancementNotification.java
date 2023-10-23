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

package com.volmit.adapt.api.notification;

import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.RNG;
import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.nms.advancements.advancement.AdvancementVisibility;
import lombok.Builder;
import lombok.Data;
import org.bukkit.Material;

@Data
@Builder
public class AdvancementNotification implements Notification {
    @Builder.Default
    private final Material icon = Material.DIAMOND;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String description = " ";
    @Builder.Default
    private final AdvancementDisplay.AdvancementFrame frameType = AdvancementDisplay.AdvancementFrame.TASK;
    @Builder.Default
    private final String group = "default";

    @Override
    public long getTotalDuration() {
        return 100; // TODO: Actually calculate
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        AdvancementDisplay d = new AdvancementDisplay(icon, buildTitle(), description, frameType, AdvancementVisibility.ALWAYS);
        Advancement a = new Advancement(null, new NameKey("adapt-notifications", "n" + p.getId() + RNG.r.lmax()), d);
        if (p.getPlayer() != null) {
            a.displayToast(p.getPlayer());
        }
    }

    public String buildTitle() {
        if (description.trim().isEmpty()) {
            return title;
        }

        return title + "\n" + description;
    }
}
