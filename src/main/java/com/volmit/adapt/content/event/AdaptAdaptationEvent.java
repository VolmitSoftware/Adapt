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

package com.volmit.adapt.content.event;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;

public class AdaptAdaptationEvent extends AdaptPlayerEvent {
    private final Skill<?> skill;
    private final PlayerSkillLine playerSkill;
    private final Adaptation<?> adaptation;

    public AdaptAdaptationEvent(boolean async, AdaptPlayer player,  Adaptation<?> adaptation) {
        super(async, player);
        this.adaptation = adaptation;
        this.playerSkill = player.getSkillLine(adaptation.getSkill().getId());
        this.skill = adaptation.getSkill();
    }

    public Skill<?> getSkill() {
        return skill;
    }

    public Adaptation<?> getAdaptation() {
        return adaptation;
    }

    public PlayerSkillLine getPlayerSkill() {
        return playerSkill;
    }
}
