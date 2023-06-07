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

package com.volmit.adapt.api.protection;

import com.volmit.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface Protector {

    default boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
        return true;
    }

    default boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
        return true;
    }

    default boolean canPVP(Player player, Location victimLocation, Adaptation<?> adaptation) {
        return true;
    }

    default boolean canPVE(Player player, Location victimLocation, Adaptation<?> adaptation) {
        return true;
    }

    default boolean canInteract(Player player, Location targetLocation, Adaptation<?> adaptation) {
        return true;
    }

    default boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
        return true;
    }

    default boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
        return true;
    }


    String getName();

    boolean isEnabledByDefault();

    default void unregister() {
    }
}
