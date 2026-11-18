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

package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import static art.arcane.adapt.api.world.AdaptComponentTypes.vanilla;

public interface AdaptComponent {
  default AdaptServer getServer() {
    return AdaptCore.get().getAdaptServer();
  }

  default AdaptPlayer getPlayer(PlayerRef p) {
    return getServer().getPlayer(p);
  }

  default boolean isItem(ItemRef is) {
    return is != null && !is.type().isAir();
  }

  default boolean isTool(ItemRef is) {
    return isAxe(is) || isPickaxe(is) || isHoe(is) || isShovel(is) || isSword(is) || isTrident(is) || isSpear(is) || isMace(is);
  }

  default boolean isMelee(ItemRef is) {
    return isTool(is);
  }

  default boolean isMace(ItemRef is) {
    return isItem(is) && vanilla(is.type(), "mace");
  }

  default boolean isSpear(ItemRef is) {
    return isItem(is) && vanilla(is.type(), AdaptComponentTypes.SPEARS);
  }

  default boolean isShield(ItemRef is) {
    return isItem(is) && vanilla(is.type(), "shield");
  }

  default boolean isXpBlock(Id material) {
    return vanilla(material, "experience_bottle");
  }

  default boolean isRanged(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.RANGED);
  }

  default boolean isSword(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.SWORDS);
  }

  default boolean isTrident(ItemRef it) {
    return isItem(it) && vanilla(it.type(), "trident");
  }

  default boolean isAxe(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.AXES);
  }

  default boolean isPickaxe(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.PICKAXES);
  }

  default boolean isShovel(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.SHOVELS);
  }

  default boolean isLog(ItemRef it) {
    return isItem(it) && isLog(it.type());
  }

  default boolean isLog(Id type) {
    if (type == null) {
      return false;
    }
    return vanilla(type, AdaptComponentTypes.LOG_EXTRAS) || type.path().endsWith("_log") || type.path().endsWith("_wood");
  }

  default boolean isLeaves(ItemRef it) {
    return isItem(it) && isLeaves(it.type());
  }

  default boolean isLeaves(Id type) {
    if (type == null) {
      return false;
    }
    return vanilla(type, AdaptComponentTypes.LEAF_EXTRAS) || type.path().endsWith("_leaves");
  }

  default boolean isBoots(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.BOOTS);
  }

  default boolean isHelmet(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.HELMETS);
  }

  default boolean isLeggings(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.LEGGINGS);
  }

  default boolean isChestplate(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.CHESTPLATES);
  }

  default boolean isElytra(ItemRef it) {
    return isItem(it) && vanilla(it.type(), "elytra");
  }

  default boolean isHoe(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AdaptComponentTypes.HOES);
  }

  default boolean isOre(BlockStateRef b) {
    return b != null && vanilla(b.type(), AdaptComponentTypes.ORES);
  }

  default boolean isStorage(BlockStateRef b) {
    return b != null && vanilla(b.type(), AdaptComponentTypes.STORAGE);
  }
}
