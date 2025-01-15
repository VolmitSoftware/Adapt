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


import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.CustomModel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Builder
@Data
public class AdaptAdvancement {
    private String background;
    @Builder.Default
    private Material icon;
    @Builder.Default
    private CustomModel model;
    @Builder.Default
    private String title;
    @Builder.Default
    private String description;
    @Builder.Default
    private AdvancementFrameType frame;
    @Builder.Default
    private boolean toast;
    @Builder.Default
    private boolean announce;
    @Builder.Default
    private AdvancementVisibility visibility;
    @Builder.Default
    private String key;
    @Singular
    private List<AdaptAdvancement> children;

    private Advancement toAdvancement(Advancement parent, int index, int depth) {
        if (children == null) {
            children = new ArrayList<>();
        }

        var icon = getModel() != null ?
                getModel().toItemStack() :
                new ItemStack(getIcon());
        AdvancementDisplay d = new AdvancementDisplay.Builder(icon, getTitle())
                .description(getDescription())
                .frame(getFrame())
                .showToast(toast)
                .x(1f + depth)
                .y(1f + index)
                .build();

        if (parent == null) {
            if (background == null)
                throw new IllegalArgumentException("Background cannot be null");

            return new MainAdvancement(Adapt.instance.getManager().createAdvancementTab(getKey()), getKey(), d, background);
        }

        return new SubAdvancement(getKey(), d, parent, getVisibility());
    }

    public List<Advancement> toAdvancements() {
        return toAdvancements(null, 0, 0);
    }

    private List<Advancement> toAdvancements(Advancement p, int index, int depth) {
        List<Advancement> aa = new ArrayList<>();
        Advancement a = toAdvancement(p, index, depth);
        if (children != null && !children.isEmpty()) {
            for (AdaptAdvancement i : children) {
                aa.addAll(i.toAdvancements(a, aa.size(), depth + 1));
            }
        }

        aa.add(a);

        return aa;
    }

    private static class MainAdvancement extends RootAdvancement {

        public MainAdvancement(@NotNull AdvancementTab advancementTab, @NotNull String key, @NotNull AdvancementDisplay display, @NotNull String backgroundTexture) {
            super(advancementTab, key, display, backgroundTexture);
        }

        @Override
        public void grant(@NotNull Player player, boolean giveRewards) {
            super.grant(player, giveRewards);
            getAdvancementTab().showTab(player);
        }

        @Override
        public void revoke(@NotNull Player player) {
            super.revoke(player);
            getAdvancementTab().hideTab(player);
        }
    }

    private static class SubAdvancement extends BaseAdvancement {
        private final AdvancementVisibility visibility;

        public SubAdvancement(@NotNull String key,
                              @NotNull AdvancementDisplay display,
                              @NotNull Advancement parent,
                              @NotNull AdvancementVisibility visibility) {
            super(key, display, parent);
            this.visibility = visibility;
        }

        @Override
        public boolean isVisible(@NotNull TeamProgression progression) {
            return visibility.isVisible(this, progression);
        }
    }
}
