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
import com.volmit.adapt.api.world.AdaptPlayer;
import lombok.Getter;
import org.bukkit.Location;

public class AdaptAdaptationTeleportEvent extends AdaptAdaptationEvent {
    @Getter
    Location fromLocation, toLocation;

    public AdaptAdaptationTeleportEvent(boolean async, AdaptPlayer player, Adaptation<?> adaptation, Location fromLocation, Location toLocation) {
        super(async, player, adaptation);
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
    }
}
