package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.chronos.ChronosEffectStretch;
import art.arcane.adapt.content.adaptation.chronos.ChronosOvertime;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every active beneficial potion effect (Overtime's list) runs longer, re-applied through Overtime's stretch so the
 * change is not seen as a new effect; infinite effects are left alone. Each application of an effect is extended once,
 * by no more than the time it has left, so it never runs past twice its length.
 */
public class ChronosBoon extends BoonBlock<ChronosBoon.Config> {
  private final ChronosEffectStretch stretch = new ChronosEffectStretch(this, playerState(), (PlayerRef p, int bonus) -> {
  });
  private final Map<UUID, Map<Id, Stretched>> stretched = playerState();

  public ChronosBoon() {
    super("block-chronos-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("potion"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int bonus = (int) (use.scaled(getConfig().addedMillisAtFirstLevel, getConfig().addedMillisAtTopLevel) / 50D);
    if (bonus <= 0) {
      return Outcome.MISSED;
    }
    long now = System.currentTimeMillis();
    Map<Id, Stretched> history = stretched.computeIfAbsent(player.uuid(), (UUID id) -> new HashMap<>());
    List<EffectInstance> extended = new ArrayList<>();
    for (EffectInstance effect : player.effects()) {
      if (effect.isInfinite() || effect.durationTicks() <= 0 || !ChronosOvertime.BENEFICIAL.contains(effect.type())) {
        continue;
      }
      Stretched previous = history.get(effect.type());
      if (previous != null && previous.sameApplication(effect, now)) {
        continue;
      }
      int added = Math.min(bonus, effect.durationTicks());
      EffectInstance longer = new EffectInstance(effect.type(), effect.durationTicks() + added, effect.amplifier(),
          effect.ambient(), effect.particles(), effect.icon());
      stretch.reapply(player, longer);
      history.put(effect.type(), new Stretched(effect.amplifier(), now + (longer.durationTicks() * 50L)));
      extended.add(longer);
    }
    if (extended.isEmpty()) {
      return Outcome.MISSED;
    }
    ChronosOvertime.stretchFx(this, player, extended.size() > 2);
    return Outcome.INSTANT;
  }

  /** An application of an effect already extended, and when it is due to end. */
  private record Stretched(int amplifier, long endsAtMillis) {
    private static final long SAME_END_TOLERANCE_MILLIS = 1_500L;

    boolean sameApplication(EffectInstance effect, long now) {
      long endsAt = now + (effect.durationTicks() * 50L);
      return effect.amplifier() == amplifier && now <= endsAtMillis
          && Math.abs(endsAt - endsAtMillis) <= SAME_END_TOLERANCE_MILLIS;
    }
  }

  @ConfigDescription("5 s added to your active potion effects.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Time added to each beneficial effect at level 1, in milliseconds.", impact = "Higher values keep potions running longer.")
    double addedMillisAtFirstLevel = 5_000D;
    @ConfigDoc(value = "Time added to each beneficial effect at the top level, in milliseconds.", impact = "Higher values keep potions running longer.")
    double addedMillisAtTopLevel = 10_000D;
  }
}
