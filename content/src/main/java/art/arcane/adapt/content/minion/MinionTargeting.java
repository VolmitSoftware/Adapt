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

package art.arcane.adapt.content.minion;

import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.UUID;

/**
 * Whom a player's minions may strike, judged from a snapshot taken on the candidate's thread: never the owner, other
 * minions, shielded entities or the owner's own pets, and otherwise what the adaptation's PvP and PvE rules allow.
 */
public final class MinionTargeting {
  private MinionTargeting() {
  }

  public static Candidate captureOwned(UUID ownerId, LivingRef candidate) {
    if (!candidate.isValid() || candidate.isDead()) {
      return null;
    }
    Loc location = candidate.location();
    return new Candidate(candidate, candidate.uuid(), location.world().uid(), location,
        candidate instanceof PlayerRef, isProtectedFriendly(ownerId, candidate));
  }

  public static Judged captureJudgedOwned(Adaptation<?> policy, PlayerRef owner, LivingRef candidate) {
    Candidate captured = captureOwned(owner.uuid(), candidate);
    if (captured == null) {
      return null;
    }
    boolean pveAllowed = !captured.player() && !captured.protectedFriendly() && policy.canPVE(owner, candidate);
    return new Judged(captured, pveAllowed);
  }

  public static boolean canDamageOwned(Adaptation<?> policy, PlayerRef owner, Candidate candidate) {
    if (candidate.protectedFriendly() || owner.uuid().equals(candidate.entityId())) {
      return false;
    }
    return candidate.player()
        ? policy.canPVP(owner, candidate.location())
        : policy.canPVE(owner, candidate.entity());
  }

  public static boolean canDamageJudgedOwned(Adaptation<?> policy, PlayerRef owner, Judged judged) {
    Candidate candidate = judged.candidate();
    if (candidate.protectedFriendly() || owner.uuid().equals(candidate.entityId())) {
      return false;
    }
    return candidate.player() ? policy.canPVP(owner, candidate.location()) : judged.pveAllowed();
  }

  private static boolean isProtectedFriendly(UUID ownerId, LivingRef candidate) {
    if (ownerId.equals(candidate.uuid())) {
      return true;
    }
    if (candidate.isInvulnerable() || Minions.isMinion(candidate) || Integrations.isShielded(candidate)) {
      return true;
    }
    return ownerId.equals(candidate.tameOwner());
  }

  public record Candidate(LivingRef entity, UUID entityId, UUID worldId, Loc location,
                          boolean player, boolean protectedFriendly) {
  }

  public record Judged(Candidate candidate, boolean pveAllowed) {
  }
}
