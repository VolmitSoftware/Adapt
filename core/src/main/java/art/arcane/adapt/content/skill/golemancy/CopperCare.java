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

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Id;

public final class CopperCare {
  public static final Id COPPER_GOLEM = Id.minecraft("copper_golem");
  public static final DataKey<Byte> WAX_PAID_KEY = DataKey.of(Id.of("adapt", "golem_wax_paid"), DataCodecs.BYTE);
  public static final DataKey<Integer> STAGE_FLOOR_KEY = DataKey.of(Id.of("adapt", "golem_oxidation_floor"), DataCodecs.INT);

  private CopperCare() {
  }

  public static boolean scraped(int before, int after) {
    return before >= 0 && after >= 0 && after < before;
  }

  public static void recordStageFloor(EntityRef golem, int stage) {
    if (COPPER_GOLEM.equals(golem.type())) {
      golem.data().set(STAGE_FLOOR_KEY, Math.max(0, stage));
    }
  }

  public static void restored(EntityRef golem, int stage) {
    if (!COPPER_GOLEM.equals(golem.type())) {
      return;
    }
    recordStageFloor(golem, stage);
    golem.data().set(WAX_PAID_KEY, (byte) 1);
  }

  public static boolean claimScrape(EntityRef golem, int before, int after) {
    if (!scraped(before, after)) {
      return false;
    }
    DataHolder data = golem.data();
    Integer stored = data.get(STAGE_FLOOR_KEY);
    int floor = stored == null ? 0 : stored;
    data.set(STAGE_FLOOR_KEY, Math.min(floor, after));
    return before > floor;
  }

  public static boolean newlyWaxed(boolean waxedBefore, boolean waxedAfter) {
    return !waxedBefore && waxedAfter;
  }

  public static boolean claimWaxPayment(EntityRef golem) {
    DataHolder data = golem.data();
    if (data.has(WAX_PAID_KEY)) {
      return false;
    }
    data.set(WAX_PAID_KEY, (byte) 1);
    return true;
  }
}
