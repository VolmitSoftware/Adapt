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

package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.internal.AbilityDenial;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;
import java.util.UUID;

public final class AbilityApiBridge {
  private AbilityApiBridge() {
  }

  public static AbilityDenial lastDenial(UUID playerId) {
    return port().lastDenial(playerId);
  }

  static boolean allowedByUsePolicy(Adaptation<?> adaptation, AdaptPlayer player) {
    if (adaptation == null || player == null) {
      return true;
    }

    PlayerRef platformPlayer = player.getPlayer();

    if (platformPlayer == null) {
      return true;
    }

    String abilityId = adaptation.getName();
    String skillId = adaptation.getSkill().getName();
    AbilityApiPort port = port();

    if (!port.hasUsePolicies(abilityId, skillId)) {
      return true;
    }

    return port.allowedByUsePolicy(abilityId, skillId, AdaptationRuntimeGuards.getLevel(adaptation, player),
        platformPlayer);
  }

  static boolean charge(Adaptation<?> adaptation, PlayerRef player, String costKey, AbilityCostKind kind, ItemRef unit,
                        int amount, AbilityDefaultCost defaultCost) {
    AbilityApiPort port = port();

    if (!port.costsActive()) {
      return defaultCost == null || defaultCost.take();
    }

    return port.charge(costRequest(adaptation, player, costKey, kind, unit, amount), defaultCost).allowed();
  }

  static AbilityCharge chargeDeferred(Adaptation<?> adaptation, PlayerRef player, String costKey, AbilityCostKind kind,
                                      ItemRef unit, int amount, AbilityDefaultCost defaultCost) {
    AbilityApiPort port = port();

    if (!port.costsActive()) {
      boolean taken = defaultCost == null || defaultCost.take();
      return new AbilityCharge(UUID.randomUUID(),
          taken ? AbilityOutcome.ALLOWED_DEFAULT : AbilityOutcome.DENIED_INSUFFICIENT, false, "", "",
          List.of());
    }

    return port.chargeDeferred(costRequest(adaptation, player, costKey, kind, unit, amount), defaultCost);
  }

  static boolean settle(UUID activationId) {
    return port().settle(activationId);
  }

  static boolean refund(UUID activationId, AbilityRefundReason reason) {
    return port().refund(activationId, reason);
  }

  private static AbilityCostRequest costRequest(Adaptation<?> adaptation, PlayerRef player, String costKey,
                                                AbilityCostKind kind, ItemRef unit, int amount) {
    return new AbilityCostRequest(adaptation.getName(), adaptation.getSkill().getName(),
        AdaptationRuntimeGuards.getLevel(adaptation, player), player, player.location(),
        AdaptationRuntimeGuards.adaptationRewardKey(adaptation, costKey), kind, unit, Math.max(0, amount));
  }

  private static AbilityApiPort port() {
    return AdaptPorts.get(AbilityApiPort.class, AbilityApiPort.NONE);
  }
}
