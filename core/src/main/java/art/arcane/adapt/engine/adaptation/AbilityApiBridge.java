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

package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.engine.ability.EngineAbilityCostContext;
import art.arcane.adapt.engine.ability.EngineAbilityContext;
import art.arcane.adapt.engine.ability.EngineAbilityCharge;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityPhase;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.ability.internal.AbilityCostGateway;
import art.arcane.adapt.engine.ability.internal.AbilityHooksRuntime;
import art.arcane.adapt.engine.ability.internal.AbilityUsePolicyGateway;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Asks the use policies and cost providers registered through {@code AdaptApi.abilities()} about an adaptation. */
public final class AbilityApiBridge {
  private AbilityApiBridge() {
  }

  static boolean allowedByUsePolicy(Adaptation<?> adaptation, AdaptPlayer player) {
    if (adaptation == null || player == null) {
      return true;
    }

    PlayerRef platformPlayer = player.getPlayer();

    if (platformPlayer == null) {
      return true;
    }

    Id ability = adaptation.getId();
    Id skill = adaptation.getSkill().getId();
    AbilityUsePolicyGateway gateway = AbilityHooksRuntime.get().usePolicy();

    if (!gateway.hasPoliciesFor(ability, skill)) {
      return true;
    }

    return gateway.evaluate(new EngineAbilityContext(UUID.randomUUID(), ability, skill,
        AdaptationRuntimeGuards.getLevel(adaptation, player), AbilityPhase.CHECK, platformPlayer, Optional.empty()))
        .allowed();
  }

  static boolean charge(Adaptation<?> adaptation, PlayerRef player, String costKey, AbilityCostKind kind, ItemRef unit,
                        int amount, AbilityDefaultCost defaultCost) {
    AbilityCostGateway gateway = AbilityHooksRuntime.get().cost();

    if (gateway.idle()) {
      return defaultCost == null || defaultCost.take();
    }

    return gateway.charge(costContext(adaptation, player, costKey, kind, unit, amount), defaultCost).allowed();
  }

  static AbilityCharge chargeDeferred(Adaptation<?> adaptation, PlayerRef player, String costKey, AbilityCostKind kind,
                                      ItemRef unit, int amount, AbilityDefaultCost defaultCost) {
    AbilityCostGateway gateway = AbilityHooksRuntime.get().cost();

    if (gateway.idle()) {
      boolean taken = defaultCost == null || defaultCost.take();
      return new EngineAbilityCharge(UUID.randomUUID(),
          taken ? AbilityOutcome.ALLOWED_DEFAULT : AbilityOutcome.DENIED_INSUFFICIENT, false, "", "",
          List.of());
    }

    return gateway.chargeDeferred(costContext(adaptation, player, costKey, kind, unit, amount), defaultCost);
  }

  static boolean settle(UUID activationId) {
    return AbilityHooksRuntime.get().cost().settle(activationId);
  }

  static boolean refund(UUID activationId, AbilityRefundReason reason) {
    return AbilityHooksRuntime.get().cost().refund(activationId, reason);
  }

  private static AbilityCostContext costContext(Adaptation<?> adaptation, PlayerRef player, String costKey,
                                                AbilityCostKind kind, ItemRef unit, int amount) {
    Loc origin = player.location();
    AbilityContext ability = new EngineAbilityContext(UUID.randomUUID(), adaptation.getId(), adaptation.getSkill().getId(),
        AdaptationRuntimeGuards.getLevel(adaptation, player), AbilityPhase.ACTIVATE, player,
        Optional.ofNullable(origin));
    return new EngineAbilityCostContext(ability, AdaptationRuntimeGuards.adaptationRewardKey(adaptation, costKey), kind,
        Optional.ofNullable(unit), Math.max(0, amount));
  }
}
