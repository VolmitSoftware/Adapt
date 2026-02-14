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

package art.arcane.adapt.content.adaptation.stealth.util;

import lombok.Getter;
import org.bukkit.entity.EntityType;

import java.util.List;

public class EntityListing {

    @Getter
    public static List<EntityType> aggroMobs = List.of(
            EntityType.EVOKER,
            EntityType.VINDICATOR,
            EntityType.PILLAGER,
            EntityType.RAVAGER,
            EntityType.VEX,
            EntityType.ENDERMITE,
            EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN,
            EntityType.SKELETON,
            EntityType.SHULKER,
            EntityType.SKELETON_HORSE,
            EntityType.HUSK,
            EntityType.STRAY,
            EntityType.PHANTOM,
            EntityType.BLAZE,
            EntityType.CREEPER,
            EntityType.GHAST,
            EntityType.MAGMA_CUBE,
            EntityType.SILVERFISH,
            EntityType.SLIME,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_HORSE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.DROWNED,
            EntityType.WITHER_SKELETON,
            EntityType.WITCH,
            EntityType.HOGLIN,
            EntityType.ZOGLIN,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.ENDERMAN
    );


}
