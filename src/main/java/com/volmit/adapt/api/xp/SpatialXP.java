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

package com.volmit.adapt.api.xp;

import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.M;
import lombok.Data;
import org.bukkit.Location;

@Data
public class SpatialXP {
    private Location location;
    private double radius;
    private Skill skill;
    private double xp;
    private long ms;

    public SpatialXP(Location l, Skill s, double xp, double radius, long duration) {
        this.location = l;
        this.skill = s;
        this.xp = xp;
        this.ms = M.ms() + duration;
        this.radius = radius;
    }
}
