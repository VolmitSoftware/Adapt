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

package com.volmit.adapt.api.advancement;


import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.nms.advancements.advancement.AdvancementVisibility;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

@Builder
@Data
public class AdaptAdvancement {
    private String background;
    @Builder.Default
    private Material icon = Material.EMERALD;
    @Builder.Default
    private String title = "MISSING TITLE";
    @Builder.Default
    private String description = "MISSING DESCRIPTION";
    @Builder.Default
    private AdvancementDisplay.AdvancementFrame frame = AdvancementDisplay.AdvancementFrame.TASK;
    @Builder.Default
    private boolean toast = false;
    @Builder.Default
    private boolean announce = false;
    @Builder.Default
    private AdvancementVisibility visibility = AdvancementVisibility.PARENT_GRANTED;
    @Builder.Default
    private String key = "root";
    @Singular
    private List<AdaptAdvancement> children;

    public Advancement toAdvancement() {
        return toAdvancement(null, 0, 0);
    }

    public Advancement toAdvancement(Advancement parent, int index, int depth) {
        if (children == null) {
            children = new ArrayList<>();
        }

        AdvancementDisplay d = new AdvancementDisplay(getIcon(), getTitle(), getDescription(), getFrame(), getVisibility());

        if (background != null) {
            d.setBackgroundTexture(getBackground());
        }

        d.setX(1f + depth);
        d.setY(1f + index);

        return new Advancement(parent, new NameKey("adapt", getKey()), d);
    }

    public List<Advancement> toAdvancements() {
        return toAdvancements(null, 0, 0);
    }

    public List<Advancement> toAdvancements(Advancement p, int index, int depth) {
        List<Advancement> aa = new ArrayList<>();
        Advancement a = toAdvancement(p, index, depth);
        int ind = 0;
        if (children != null && !children.isEmpty()) {
            for (AdaptAdvancement i : children) {
                aa.addAll(i.toAdvancements(a, aa.size(), depth + 1));
            }
        }

        aa.add(a);

        return aa;
    }
}
