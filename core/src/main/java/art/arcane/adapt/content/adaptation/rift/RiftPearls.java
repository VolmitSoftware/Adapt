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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

final class RiftPearls {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final DataKey<String> TAGLOCK_LOC = DataKey.adapt(RiftConduit.TAGLOCK_LOC_KEY_NAME, DataCodecs.STRING);
  private static final DataKey<String> TAGLOCK_ID = DataKey.adapt(RiftConduit.TAGLOCK_ID_KEY_NAME, DataCodecs.STRING);
  private static final DataKey<String> TAGLOCK_TARGET = DataKey.adapt(RiftEnderTaglock.TARGET_KEY_NAME, DataCodecs.STRING);

  private RiftPearls() {
  }

  static boolean isUnclaimedPearl(ProjectileRef pearl, Id... ownedKeys) {
    return pearl != null && isUnclaimedContainer(pearl.data(), ownedKeys);
  }

  static boolean isUnclaimedContainer(DataHolder data, Id... ownedKeys) {
    return ProjectileCooperativeKeys.isUnclaimed(data, ownedKeys);
  }

  static boolean isPlainPearl(ItemRef stack) {
    if (stack == null || !stack.type().equals(ENDER_PEARL) || stack.amount() <= 0) {
      return false;
    }
    return !isReservedPearl(stack);
  }

  private static boolean isReservedPearl(ItemRef stack) {
    if (BoundEnderPearl.isBindableItem(stack)) {
      return true;
    }
    DataHolder data = stack.data();
    return data.has(TAGLOCK_LOC) || data.has(TAGLOCK_ID) || data.has(TAGLOCK_TARGET);
  }
}
