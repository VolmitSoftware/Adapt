package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBlood;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TragOul and Nether, either leading, replacing Convergence: a kill spreads Wither I to the hostiles around the corpse,
 * and a wither skeleton kill fills TragOul's blood reservoir.
 */
public final class WitherBlood extends Signature<WitherBlood.Config> {
  static final Id WITHER = Id.minecraft("wither");
  static final Id WITHER_SKELETON = Id.minecraft("wither_skeleton");
  private final Map<UUID, Kill> kills = new ConcurrentHashMap<>();

  public WitherBlood(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("nether_wart"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.f(radius(level), 1), 1);
    statLore(v, maxTargets(level), 2);
    statLore(v, Form.f(skeletonBlood(level) / 2D, 1), 3);
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().cooldownMillisAtFirstLevel, getConfig().cooldownMillisAtTopLevel)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    PlayerRef killer = e.killer();
    if (e.platformCancelled() || killer == null || victim instanceof PlayerRef || GolemKillCredit.isGolemKill(victim)
        || getActiveLevel(killer) <= 0) {
      return;
    }
    Loc corpse = victim.location();
    kills.put(killer.uuid(), new Kill(victim.uuid(), victim.type(), corpse));
    try {
      fire(killer, victim, corpse, MutationPayouts.farmed(killer, victim));
    } finally {
      kills.remove(killer.uuid());
    }
  }

  @Override
  protected Outcome run(BlockUse use) {
    Kill kill = kills.remove(use.player().uuid());
    if (kill == null) {
      return Outcome.MISSED;
    }
    PlayerRef p = use.player();
    int level = use.level();
    int withered = spread(p, kill, level);
    double stored = WITHER_SKELETON.equals(kill.type())
        ? TragoulBlood.fill(getServer().getSkillRegistry().getSkill("tragoul"), p, skeletonBlood(level)) : 0D;
    if (withered <= 0 && stored <= 0D) {
      return Outcome.MISSED;
    }
    fx(kill.at().add(0D, 0.5D, 0D), FxPriority.COMBAT)
        .ring(Particles.SMOKE, radius(level), 18, 0.2D)
        .burst(Particles.SOUL, 8, 0.5D)
        .sound(Sounds.ENTITY_WITHER_SHOOT, 0.5F, 1.4F);
    return Outcome.INSTANT;
  }

  private int spread(PlayerRef p, Kill kill, int level) {
    double radius = radius(level);
    int limit = maxTargets(level);
    int ticks = durationTicks(level);
    int withered = 0;
    for (EntityRef entity : kill.at().world().nearbyEntities(kill.at().position(), radius, radius, radius)) {
      if (withered >= limit) {
        break;
      }
      if (!(entity instanceof LivingRef living) || entity.uuid().equals(kill.victim()) || entity instanceof PlayerRef
          || !entity.has(EntityTrait.ENEMY) || WITHER_SKELETON.equals(entity.type()) || WITHER.equals(entity.type())
          || living.isDead() || !canDamageTarget(p, living)) {
        continue;
      }
      living.addEffect(new EffectInstance(WITHER, ticks, 0, false, true, true));
      withered++;
    }
    return withered;
  }

  double radius(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
  }

  int maxTargets(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().maxTargetsAtFirstLevel, getConfig().maxTargetsAtTopLevel));
  }

  int durationTicks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().witherTicksAtFirstLevel, getConfig().witherTicksAtTopLevel));
  }

  double skeletonBlood(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().skeletonBloodAtFirstLevel, getConfig().skeletonBloodAtTopLevel);
  }

  private record Kill(UUID victim, Id type, Loc at) {
  }

  @ConfigDescription("Kills spread Wither I to nearby hostiles, and wither skeleton kills fill the blood reservoir.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Radius around the corpse that Wither spreads to at level 1, in blocks.", impact = "Higher values reach more of a crowd.")
    double radiusAtFirstLevel = 4;
    @ConfigDoc(value = "Radius around the corpse that Wither spreads to at the top level, in blocks.", impact = "Higher values reach more of a crowd.")
    double radiusAtTopLevel = 6;
    @ConfigDoc(value = "Most hostiles withered by one kill at level 1.", impact = "Higher values spread further through crowds.")
    double maxTargetsAtFirstLevel = 4;
    @ConfigDoc(value = "Most hostiles withered by one kill at the top level.", impact = "Higher values spread further through crowds.")
    double maxTargetsAtTopLevel = 8;
    @ConfigDoc(value = "Wither I duration at level 1, in ticks.", impact = "Higher values wither longer.")
    double witherTicksAtFirstLevel = 80;
    @ConfigDoc(value = "Wither I duration at the top level, in ticks.", impact = "Higher values wither longer.")
    double witherTicksAtTopLevel = 160;
    @ConfigDoc(value = "Blood a wither skeleton kill stores in the reservoir at level 1; 2 is one heart.", impact = "Higher values store more.")
    double skeletonBloodAtFirstLevel = 4;
    @ConfigDoc(value = "Blood a wither skeleton kill stores in the reservoir at the top level; 2 is one heart.", impact = "Higher values store more.")
    double skeletonBloodAtTopLevel = 8;
    @ConfigDoc(value = "Time between spreads at level 1, in milliseconds.", impact = "Higher values make spreads rarer.")
    double cooldownMillisAtFirstLevel = 6000;
    @ConfigDoc(value = "Time between spreads at the top level, in milliseconds.", impact = "Higher values make spreads rarer.")
    double cooldownMillisAtTopLevel = 3000;
  }
}
