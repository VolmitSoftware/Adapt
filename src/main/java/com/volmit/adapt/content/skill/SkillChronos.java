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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class SkillChronos extends SimpleSkill<SkillChronos.Config> {
    public SkillChronos() {
        super("chronos", Localizer.dLocalize("skill", "chronos", "icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setInterval(600000);
        setDescription(Localizer.dLocalize("skill", "chronos", "description"));
        setDisplayName(Localizer.dLocalize("skill", "chronos", "name"));
        setInterval(getConfig().setInterval);
        setIcon(Material.CLOCK);
    }


    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player p : Adapt.instance.getAdaptServer().getAdaptPlayers()) {
            if (shouldReturnForPlayer(p)) {
                return;
            }
            getPlayer(p).getData().addStat("minutes.online", 10);
            checkStatTrackers(getPlayer(p));
            if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                return;
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        public long setInterval = 5050;
        boolean enabled = false;
    }
}
