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

package art.arcane.adapt.content.skill.golemancy;

import io.papermc.paper.world.WeatheringCopperState;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class CopperCare {
  public static final NamespacedKey WAX_PAID_KEY = NamespacedKey.fromString("adapt:golem_wax_paid");
  public static final NamespacedKey STAGE_FLOOR_KEY = NamespacedKey.fromString("adapt:golem_oxidation_floor");

  private CopperCare() {
  }

  public static boolean scraped(WeatheringCopperState before, WeatheringCopperState after) {
    return before != null && after != null && after.ordinal() < before.ordinal();
  }

  public static void recordStageFloor(Entity golem, int stage) {
    if (golem instanceof CopperGolem) {
      golem.getPersistentDataContainer().set(STAGE_FLOOR_KEY, PersistentDataType.INTEGER, Math.max(0, stage));
    }
  }

  public static void restored(Entity golem, int stage) {
    if (!(golem instanceof CopperGolem)) {
      return;
    }
    recordStageFloor(golem, stage);
    golem.getPersistentDataContainer().set(WAX_PAID_KEY, PersistentDataType.BYTE, (byte) 1);
  }

  public static boolean claimScrape(Entity golem, WeatheringCopperState before, WeatheringCopperState after) {
    if (!scraped(before, after)) {
      return false;
    }
    PersistentDataContainer data = golem.getPersistentDataContainer();
    Integer stored = data.get(STAGE_FLOOR_KEY, PersistentDataType.INTEGER);
    int floor = stored == null ? 0 : stored;
    data.set(STAGE_FLOOR_KEY, PersistentDataType.INTEGER, Math.min(floor, after.ordinal()));
    return before.ordinal() > floor;
  }

  public static boolean waxed(CopperGolem.Oxidizing oxidizing) {
    return oxidizing instanceof CopperGolem.Oxidizing.Waxed;
  }

  public static boolean newlyWaxed(boolean waxedBefore, boolean waxedAfter) {
    return !waxedBefore && waxedAfter;
  }

  public static boolean claimWaxPayment(Entity golem) {
    PersistentDataContainer data = golem.getPersistentDataContainer();
    if (data.has(WAX_PAID_KEY, PersistentDataType.BYTE)) {
      return false;
    }
    data.set(WAX_PAID_KEY, PersistentDataType.BYTE, (byte) 1);
    return true;
  }
}
