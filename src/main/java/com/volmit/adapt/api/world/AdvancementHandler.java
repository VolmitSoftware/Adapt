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

package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.J;
import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.manager.AdvancementManager;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AdvancementHandler {
    private AdvancementManager manager;
    private AdaptPlayer player;
    private Map<Skill<?>, AdaptAdvancement> roots;
    private Map<String, Advancement> real;
    private boolean ready;

    public AdvancementHandler(AdaptPlayer player) {
        this.player = player;
        this.manager = new AdvancementManager(new NameKey("adapt", player.getPlayer().getUniqueId().toString()), player.getPlayer());
        roots = new HashMap<>();
        real = new HashMap<>();
        ready = false;
    }

    public void activate() {
        if (!AdaptConfig.get().isAdvancements()) {
            return;
        }
        J.s(() -> {
            removeAllAdvancements();

            for (Skill i : player.getServer().getSkillRegistry().getSkills()) {
                AdaptAdvancement aa = i.buildAdvancements();
                roots.put(i, aa);

                for (Advancement j : aa.toAdvancements().reverse()) {
                    real.put(j.getName().getKey(), j);
                    try {
                        getManager().addAdvancement(j);
                    } catch (Throwable e) {
                        Adapt.error("Failed to register advancement " + j.getName().getKey());
                        e.printStackTrace();
                    }
                }

                unlockExisting(aa);

                J.s(() -> ready = true, 40);
            }
        }, 20);
    }

    public void grant(String key, boolean toast) {
        getPlayer().getData().ensureGranted(key);
        try {
            J.s(() -> getManager().grantAdvancement(player.getPlayer(), real.get(key)), 5);
        } catch (Exception e) {
            Adapt.error("Failed to grant advancement " + key);
        }

        if (toast) {
            if (getPlayer() != null && getPlayer().getPlayer() != null) {
                try {
                    real.get(key).displayToast(getPlayer().getPlayer());
                } catch (Exception e) {
                    Adapt.error("Failed to grant advancement " + key + " Reattaching!");
                }
            }
        }
    }

    public void grant(String key) {
        grant(key, true);
    }

    private void unlockExisting(AdaptAdvancement aa) {
        if (aa.getChildren() != null) {
            for (AdaptAdvancement i : aa.getChildren()) {
                unlockExisting(i);
            }
        }

        if (getPlayer().getData().isGranted(aa.getKey())) {
            J.s(() -> grant(aa.getKey(), false), 20);
        }
    }

    public void deactivate() {
        removeAllAdvancements();
    }

    public void removeAllAdvancements() {
        for (Advancement i : getManager().getAdvancements()) {
            getManager().removeAdvancement(i);
        }
    }
}
