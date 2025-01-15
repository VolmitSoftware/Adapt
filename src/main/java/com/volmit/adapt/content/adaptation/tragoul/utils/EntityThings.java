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

package com.volmit.adapt.content.adaptation.tragoul.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class EntityThings {

    public static LivingEntity findNearestEntity(Entity referenceEntity, double range, Player player) {
        Location location = referenceEntity.getLocation();
        List<Entity> nearbyEntities = referenceEntity.getNearbyEntities(range, range, range);

        // Filter out non-living entities and the player
        List<LivingEntity> livingEntities = nearbyEntities.stream()
                .filter(entity -> entity instanceof LivingEntity && !entity.equals(player))
                .map(entity -> (LivingEntity) entity).sorted(Comparator.comparingDouble(entity -> entity.getLocation().distance(location))).collect(Collectors.toList());

        // Sort by distance to the reference entity

        // Return the nearest living entity, or null if no valid entity is found
        return livingEntities.isEmpty() ? null : livingEntities.get(0);
    }
}
