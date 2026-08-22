package art.arcane.adapt.api.ability;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AbilityContext(UUID activationId, String abilityId, String skillId, int level, AbilityPhase phase,
                             Player player, Optional<Location> origin) {
  public AbilityContext {
    Objects.requireNonNull(activationId, "activationId");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(player, "player");
    abilityId = AbilityText.requireId(abilityId, "abilityId");
    skillId = AbilityText.requireId(skillId, "skillId");
    origin = origin == null ? Optional.empty() : origin.map(Location::clone);

    if (level < 0) {
      throw new IllegalArgumentException("an ability context level must not be negative");
    }
  }

  public static AbilityContext check(String abilityId, String skillId, int level, Player player) {
    return new AbilityContext(UUID.randomUUID(), abilityId, skillId, level, AbilityPhase.CHECK, player,
        Optional.empty());
  }

  public static AbilityContext activate(String abilityId, String skillId, int level, Player player, Location origin) {
    return new AbilityContext(UUID.randomUUID(), abilityId, skillId, level, AbilityPhase.ACTIVATE, player,
        Optional.ofNullable(origin));
  }

  public UUID playerId() {
    return player.getUniqueId();
  }

  @Override
  public Optional<Location> origin() {
    return origin.map(Location::clone);
  }
}
