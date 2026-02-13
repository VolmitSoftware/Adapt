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

import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.CustomModel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Material;

import java.util.List;

@Builder(toBuilder = true)
@Data
public class AdvancementSpec {
    private String key;
    private String title;
    private String description;
    @Builder.Default
    private Material icon = Material.EMERALD;
    @Builder.Default
    private CustomModel model = null;
    @Builder.Default
    private AdaptAdvancementFrame frame = AdaptAdvancementFrame.TASK;
    @Builder.Default
    private AdvancementVisibility visibility = AdvancementVisibility.PARENT_GRANTED;
    @Builder.Default
    private boolean toast = false;
    @Builder.Default
    private boolean announce = false;
    @Singular
    private List<AdvancementSpec> children;

    public static AdvancementSpec challenge(String key, Material icon, String title, String description) {
        return AdvancementSpec.builder()
                .key(key)
                .icon(icon)
                .title(title)
                .description(description)
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build();
    }

    public AdvancementSpec withChild(AdvancementSpec child) {
        if (child == null) {
            return this;
        }

        return toBuilder().child(child).build();
    }

    public AdaptAdvancement toAdvancement() {
        AdaptAdvancement.AdaptAdvancementBuilder builder = AdaptAdvancement.builder()
                .key(key)
                .title(title)
                .description(description)
                .icon(icon)
                .model(model)
                .frame(frame)
                .toast(toast)
                .announce(announce)
                .visibility(visibility);

        if (children != null) {
            for (AdvancementSpec child : children) {
                if (child == null) {
                    continue;
                }
                builder.child(child.toAdvancement());
            }
        }

        return builder.build();
    }

    public AdaptStatTracker statTracker(String stat, double goal, double reward) {
        return AdaptStatTracker.builder()
                .stat(stat)
                .goal(goal)
                .reward(reward)
                .advancement(key)
                .build();
    }
}
