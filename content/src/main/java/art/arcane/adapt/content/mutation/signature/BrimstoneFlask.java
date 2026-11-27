package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingTrigger;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nether leading Brewing, replacing Graft: a thrown splash potion leaves a ring of harmless soul fire that gives its
 * effects once more to everything that steps into it. One ring per player at a time.
 */
public final class BrimstoneFlask extends Signature<BrimstoneFlask.Config> {
  private final Map<UUID, List<EffectInstance>> splashes = new ConcurrentHashMap<>();
  private final Map<UUID, Ring> rings = new ConcurrentHashMap<>();

  public BrimstoneFlask(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("splash_potion"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.f(radius(level), 1), 1);
    statLore(v, Format.pc(durationShare(level), 0), 2);
  }

  @Override
  public void unregister() {
    rings.clear();
    splashes.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PotionSplashEvent e) {
    if (e.isCancelled() || !(e.potion().shooter() instanceof PlayerRef thrower) || e.effects().isEmpty()
        || getActiveLevel(thrower) <= 0) {
      return;
    }
    splashes.put(thrower.uuid(), List.copyOf(e.effects()));
    try {
      if (fire(thrower, null, e.potion().location(), false)) {
        BrewingTrigger.take(e, thrower);
      }
    } finally {
      splashes.remove(thrower.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    rings.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    List<EffectInstance> effects = splashes.remove(use.player().uuid());
    Loc center = use.at();
    if (effects == null || center == null) {
      return Outcome.MISSED;
    }
    Ring ring = new Ring(use.player(), center, effects, radius(use.level()), durationShare(use.level()));
    rings.put(use.player().uuid(), ring);
    int pulses = Math.max(1, getConfig().ringTicks / Math.max(1, getConfig().pulseTicks));
    for (int pulse = 0; pulse < pulses; pulse++) {
      AdaptApi.scheduler().runAt(center, () -> pulse(ring), 1 + (pulse * Math.max(1, getConfig().pulseTicks)));
    }
    return Outcome.INSTANT;
  }

  private void pulse(Ring ring) {
    if (rings.get(ring.thrower().uuid()) != ring) {
      return;
    }
    fx(ring.center().add(0D, 0.1D, 0D), FxPriority.COMBAT)
        .ring(Particles.SOUL_FIRE_FLAME, ring.radius(), 20, 0D)
        .burst(Particles.SOUL, 2, ring.radius() * 0.5D);
    int touched = 0;
    for (EntityRef entity : ring.center().world().nearbyEntities(ring.center().position(), ring.radius(), 1.5D, ring.radius())) {
      if (touched >= getConfig().maxTargetsPerPulse) {
        break;
      }
      if (entity instanceof LivingRef living && !living.isDead() && ring.touched().add(living.uuid())) {
        touched++;
        apply(ring, living);
      }
    }
  }

  private void apply(Ring ring, LivingRef living) {
    RegistryLookup registries = AdaptApi.platform().host().registries();
    boolean mayHarm = !living.uuid().equals(ring.thrower().uuid()) && canDamageTarget(ring.thrower(), living);
    for (EffectInstance effect : ring.effects()) {
      boolean harmful = registries.effectCategory(effect.type()) == EffectCategory.HARMFUL;
      if (harmful && !mayHarm) {
        continue;
      }
      int ticks = registries.isInstantEffect(effect.type()) ? 1
          : Math.max(1, (int) Math.round(effect.durationTicks() * ring.durationShare()));
      living.addEffect(EffectInstance.of(effect.type(), ticks, effect.amplifier()));
    }
  }

  double radius(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
  }

  double durationShare(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().durationShareAtFirstLevel, getConfig().durationShareAtTopLevel);
  }

  private record Ring(PlayerRef thrower, Loc center, List<EffectInstance> effects, double radius, double durationShare,
                      Set<UUID> touched) {
    private Ring(PlayerRef thrower, Loc center, List<EffectInstance> effects, double radius, double durationShare) {
      this(thrower, center, effects, radius, durationShare, Collections.synchronizedSet(new HashSet<>()));
    }
  }

  @ConfigDescription("Thrown splash potions leave a ring of harmless soul fire that gives their effects once more.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Ring radius at level 1, in blocks.", impact = "Higher values cover more ground.")
    double radiusAtFirstLevel = 2;
    @ConfigDoc(value = "Ring radius at the top level, in blocks.", impact = "Higher values cover more ground.")
    double radiusAtTopLevel = 3.5;
    @ConfigDoc(value = "Share of each lasting effect's duration the ring gives at level 1.", impact = "Higher values make the second dose last longer.")
    double durationShareAtFirstLevel = 0.5;
    @ConfigDoc(value = "Share of each lasting effect's duration the ring gives at the top level.", impact = "Higher values make the second dose last longer.")
    double durationShareAtTopLevel = 1;
    @ConfigDoc(value = "How long a ring burns, in ticks.", impact = "Higher values leave the ring longer.")
    int ringTicks = 80;
    @ConfigDoc(value = "Ticks between two checks of who stands in the ring.", impact = "Lower values catch passers-by sooner.")
    int pulseTicks = 10;
    @ConfigDoc(value = "Most creatures one check of the ring reaches.", impact = "Lower values cap work in crowds.")
    int maxTargetsPerPulse = 12;
  }
}
