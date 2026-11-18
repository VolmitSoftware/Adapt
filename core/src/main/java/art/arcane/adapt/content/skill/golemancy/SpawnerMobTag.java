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
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.value.Id;

public final class SpawnerMobTag {
  public static final DataKey<Byte> KEY = DataKey.of(Id.of("adapt", "golemancy_spawner_mob"), DataCodecs.BYTE);

  private SpawnerMobTag() {
  }

  public static boolean isSpawnerReason(CreatureSpawnEvent.Reason reason) {
    if (reason == null) {
      return false;
    }
    return switch (reason) {
      case SPAWNER, TRIAL_SPAWNER -> true;
      default -> false;
    };
  }

  public static void mark(EntityRef entity) {
    entity.data().set(KEY, (byte) 1);
  }

  public static boolean isTagged(EntityRef entity) {
    return entity.data().has(KEY);
  }
}
