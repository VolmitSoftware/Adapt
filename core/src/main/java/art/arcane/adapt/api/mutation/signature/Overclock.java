package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.chronos.ChronosRestoration;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chronos with Golemancy, replacing Convergence: the clock gesture near your golems runs every one of them within reach
 * at triple speed for a while, paid with stored time from a chrono bottle.
 */
public final class Overclock extends Signature<Overclock.Config> {
  private final Map<UUID, Clocked> clocked = new ConcurrentHashMap<>();
  private final GolemWorkSpeed.Source speed = new GolemWorkSpeed.Source() {
    @Override
    public String id() {
      return getName();
    }

    @Override
    public double intervalMultiplier(GolemHandle handle) {
      Clocked run = clocked.get(handle.owner());
      if (run == null || !run.golems().contains(handle.id())) {
        return 1D;
      }
      if (System.currentTimeMillis() >= run.until()) {
        clocked.remove(handle.owner(), run);
        return 1D;
      }
      return 1D / Math.max(1D, getConfig().speedMultiplier);
    }
  };

  public Overclock(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("repeater"));
  }

  @Override
  protected void onRuntimeActivated() {
    GolemWorkSpeed.register(speed);
  }

  @Override
  public void unregister() {
    GolemWorkSpeed.unregister(speed);
    clocked.clear();
    super.unregister();
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.f(getConfig().speedMultiplier, 0) + "x", 1);
    statLore(v, Form.duration(durationMillis(level), 0), 2);
    statLore(v, Form.f(getConfig().bottleSeconds, 0) + "s", 3);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (SignatureKit.clockSwap(e) && getActiveLevel(player) > 0) {
      SignatureKit.settle(e, fire(player, null, player.location(), false));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    clocked.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Set<UUID> golems = golemsNear(player, player.location(), getConfig().golemRadius);
    if (golems.isEmpty() || !ChronosRestoration.spendCharge(player.inventory(), getConfig().bottleSeconds)) {
      ChronosSoundFX.playClockReject(player);
      SignatureKit.refuse(player);
      return Outcome.MISSED;
    }
    long millis = durationMillis(use.level());
    clocked.put(player.uuid(), new Clocked(System.currentTimeMillis() + millis, Set.copyOf(golems)));
    fx(player.location().add(0D, 1D, 0D), FxPriority.TRAIL).particle(Particles.ELECTRIC_SPARK, 16, 0.5D, 0.6D, 0.5D, 0.1D, 0D);
    return Outcome.lasting(player, (int) (millis / 50L));
  }

  private static Set<UUID> golemsNear(PlayerRef owner, Loc center, double radius) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    Set<UUID> near = new HashSet<>();
    if (registry.isEmpty()) {
      return near;
    }
    double limit = radius * radius;
    for (GolemHandle handle : registry.get().loadedOf(owner.uuid())) {
      MobRef golem = handle.entity();
      if (handle.isRetired() || handle.isDormant() || golem == null || !golem.isValid()) {
        continue;
      }
      Loc at = golem.location();
      if (at.sameWorld(center) && at.distanceSquared(center) <= limit) {
        near.add(handle.id());
      }
    }
    return near;
  }

  long durationMillis(int level) {
    return (long) (SignatureKit.scaled(level, getMaxLevel(), getConfig().secondsAtFirstLevel,
        getConfig().secondsAtTopLevel) * 1000D);
  }

  private record Clocked(long until, Set<UUID> golems) {
  }

  @ConfigDescription("Your golems nearby work at triple speed for a while, paid with stored bottle time.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "How many times faster overclocked golems work.", impact = "Higher values make golems act sooner.")
    double speedMultiplier = 3;
    @ConfigDoc(value = "How long golems stay overclocked at level 1, in seconds.", impact = "Higher values keep golems fast longer.")
    double secondsAtFirstLevel = 10;
    @ConfigDoc(value = "How long golems stay overclocked at the top level, in seconds.", impact = "Higher values keep golems fast longer.")
    double secondsAtTopLevel = 20;
    @ConfigDoc(value = "How far from you a golem may be to be overclocked, in blocks.", impact = "Higher values reach golems farther away.")
    double golemRadius = 16;
    @ConfigDoc(value = "Stored chrono bottle seconds one overclock costs.", impact = "Higher values drain bottles faster.")
    double bottleSeconds = 60;
    @ConfigDoc(value = "Cooldown after an overclock, in milliseconds.", impact = "Higher values make overclocks rarer.")
    long cooldownMillis = 180_000L;
  }
}
