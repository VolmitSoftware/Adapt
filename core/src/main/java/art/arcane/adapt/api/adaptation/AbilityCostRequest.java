package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;

/** One cost an adaptation asks the ability API to charge; {@code unit} is set only for item costs. */
public record AbilityCostRequest(String abilityId, String skillId, int level, PlayerRef player, Loc origin,
                                 String costKey, AbilityCostKind kind, ItemRef unit, int amount) {
}
