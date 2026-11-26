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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public final class RiftPearls {
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

  /** Plain ender pearls anywhere in the player's inventory; bound and taglocked pearls do not count. */
  public static int plainPearls(PlayerRef p) {
    int pearls = 0;
    for (ItemRef stack : p.inventory().contents()) {
      if (isPlainPearl(stack)) {
        pearls += stack.amount();
      }
    }
    return pearls;
  }

  /** Removes one plain ender pearl from the first slot holding one; false when there is none. */
  public static boolean takePlainPearl(PlayerRef p) {
    PlayerInventoryRef inventory = p.inventory();
    List<ItemRef> contents = inventory.contents();
    for (int slot = 0; slot < contents.size(); slot++) {
      ItemRef stack = contents.get(slot);
      if (isPlainPearl(stack)) {
        if (stack.amount() - 1 <= 0) {
          inventory.clear(slot);
        } else {
          inventory.setItem(slot, stack.copyWithAmount(stack.amount() - 1));
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Charges {@code payer}'s ability cost for {@code pearls} plain ender pearls, taken from anywhere in the inventory;
   * false, with nothing taken, when the player holds fewer.
   */
  public static boolean payPlainPearls(Adaptation<?> payer, PlayerRef p, String costKey, int pearls) {
    ItemRef unit = null;
    for (ItemRef stack : p.inventory().contents()) {
      if (isPlainPearl(stack)) {
        unit = stack.copyWithAmount(1);
        break;
      }
    }
    if (unit == null || plainPearls(p) < pearls) {
      return false;
    }
    return payer.payItemCost(p, costKey, unit, pearls, () -> {
      for (int taken = 0; taken < pearls; taken++) {
        if (!takePlainPearl(p)) {
          return false;
        }
      }
      return true;
    });
  }

  public static boolean isPlainPearl(ItemRef stack) {
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
