package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.chronos.ChronosEffectStretch;
import art.arcane.adapt.content.adaptation.chronos.ChronosOvertime;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chronos with Brewing, replacing Convergence: the clock gesture takes your beneficial potion effects off you with
 * their time left, and the next gesture, or the end of the longest pause, gives them back exactly as they were.
 */
public final class PausedPotions extends Signature<PausedPotions.Config> {
  private final Map<UUID, Paused> paused = new ConcurrentHashMap<>();

  public PausedPotions(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("potion"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.duration(pauseMillis(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    if (!SignatureKit.clockSwap(e)) {
      return;
    }
    PlayerRef player = e.player();
    Paused held = paused.get(player.uuid());
    if (held != null) {
      resume(player, held);
      e.setCancelled(true);
      return;
    }
    if (getActiveLevel(player) > 0 && fire(player, null, player.location(), false)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Paused held = paused.get(e.player().uuid());
    if (held != null) {
      resume(e.player(), held);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    paused.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    List<EffectInstance> held = new ArrayList<>();
    for (EffectInstance effect : List.copyOf(player.effects())) {
      if (pausable(effect)) {
        held.add(effect);
      }
    }
    if (held.isEmpty()) {
      return Outcome.MISSED;
    }
    for (EffectInstance effect : held) {
      player.removeEffect(effect.type());
    }
    Paused pause = new Paused(List.copyOf(held));
    paused.put(player.uuid(), pause);
    int ticks = (int) Math.max(1L, pauseMillis(use.level()) / 50L);
    AdaptApi.scheduler().runEntity(player, () -> resume(player, pause), ticks);
    fx(player.location().add(0D, 1D, 0D), FxPriority.TRAIL).particle(Particles.ENCHANT, 18, 0.4D, 0.6D, 0.4D, 0.3D, 0D);
    return Outcome.INSTANT;
  }

  private void resume(PlayerRef player, Paused pause) {
    if (!paused.remove(player.uuid(), pause)) {
      return;
    }
    for (EffectInstance effect : pause.effects()) {
      ChronosEffectStretch.restore(player, effect);
    }
    if (player.isOnline()) {
      ChronosSoundFX.playRewindFinish(player);
    }
  }

  private boolean pausable(EffectInstance effect) {
    return !effect.isInfinite() && !effect.ambient() && effect.durationTicks() >= getConfig().minimumDurationTicks
        && ChronosOvertime.BENEFICIAL.contains(effect.type());
  }

  long pauseMillis(int level) {
    return (long) (SignatureKit.scaled(level, getMaxLevel(), getConfig().pauseSecondsAtFirstLevel,
        getConfig().pauseSecondsAtTopLevel) * 1000D);
  }

  /** One pause; compared by identity, so a finished pause's timer never ends a newer one. */
  private static final class Paused {
    private final List<EffectInstance> effects;

    private Paused(List<EffectInstance> effects) {
      this.effects = effects;
    }

    private List<EffectInstance> effects() {
      return effects;
    }
  }

  @ConfigDescription("Hold your beneficial potion effects aside and give them back later with the time they had left.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Longest pause at level 1, in seconds.", impact = "Higher values hold effects aside longer.")
    double pauseSecondsAtFirstLevel = 20;
    @ConfigDoc(value = "Longest pause at the top level, in seconds.", impact = "Higher values hold effects aside longer.")
    double pauseSecondsAtTopLevel = 60;
    @ConfigDoc(value = "Shortest effect that can be paused, in ticks.", impact = "Higher values leave short effects running.")
    int minimumDurationTicks = 100;
  }
}
