package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** The generated State: the player earned XP in the skill in the last few seconds. */
public final class DefaultState extends StateBlock<DefaultState.Config> {
  private final Map<UUID, Long> earnedAt = playerState();

  DefaultState(Id skill) {
    super(skill);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("clock"));
  }

  void earned(PlayerRef player) {
    if (hasActiveAdaptation(player)) {
      earnedAt.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return heldWithin(player, getConfig().windowMillis);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long at = earnedAt.get(player.uuid());
    return at != null && System.currentTimeMillis() - at <= Math.max(windowMillis, getConfig().windowMillis);
  }

  @ConfigDescription("You earned XP in the skill in the last 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after earning the skill's XP the State still holds, in milliseconds.",
        impact = "Higher values keep the State on for longer.")
    long windowMillis = 5_000L;
  }
}
