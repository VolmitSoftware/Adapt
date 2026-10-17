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

package art.arcane.adapt.api.minion;

import art.arcane.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.UUID;

public final class MinionTargeting {
  private MinionTargeting() {
  }

  public static Candidate captureOwned(UUID ownerId, LivingEntity candidate) {
    if (!candidate.isValid() || candidate.isDead()) {
      return null;
    }
    Location location = candidate.getLocation();
    return new Candidate(candidate, candidate.getUniqueId(), location.getWorld().getUID(), location,
        candidate instanceof Player, isProtectedFriendly(ownerId, candidate));
  }

  public static boolean canDamageOwned(Adaptation<?> policy, Player owner, Candidate candidate) {
    if (candidate.protectedFriendly() || owner.getUniqueId().equals(candidate.entityId())) {
      return false;
    }
    return candidate.player()
        ? policy.canPVP(owner, candidate.location())
        : policy.canPVE(owner, candidate.location());
  }

  private static boolean isProtectedFriendly(UUID ownerId, LivingEntity candidate) {
    if (ownerId.equals(candidate.getUniqueId())) {
      return true;
    }
    if (candidate instanceof ArmorStand stand && stand.isMarker()) {
      return true;
    }
    if (candidate.isInvulnerable() || candidate.hasMetadata("NPC") || MinionIdentity.isMinion(candidate)) {
      return true;
    }
    if (!(candidate instanceof Tameable tameable) || !tameable.isTamed()) {
      return false;
    }
    AnimalTamer tamer = tameable.getOwner();
    return tamer != null && ownerId.equals(tamer.getUniqueId());
  }

  public record Candidate(LivingEntity entity, UUID entityId, UUID worldId, Location location,
                          boolean player, boolean protectedFriendly) {
  }
}
