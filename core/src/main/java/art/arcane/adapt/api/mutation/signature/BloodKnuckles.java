package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.unarmed.upgraded.UnbrokenCombo;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unarmed and TragOul, either leading, replacing Convergence: every bare-hand hit of a streak on a hostile mob or a
 * player siphons health per stack, capped per second as Crimson Tide is, and Unbroken Combo's finisher heals half the
 * damage it dealt.
 */
public final class BloodKnuckles extends Signature<BloodKnuckles.Config> {
  private static final String UNBROKEN_COMBO = "upgraded-unarmed-unbroken-combo";
  private final Map<UUID, Streak> streaks = playerState();
  private final Map<UUID, Budget> budgets = playerState();
  private final Map<UUID, Double> finishers = new ConcurrentHashMap<>();

  public BloodKnuckles(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.f(siphonPerStack(level), 2), 1);
    statLore(v, Form.f(siphonCapPerSecond(level), 1), 2);
    statLore(v, Form.pc(getConfig().finisherHealShare, 0), 3);
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().finisherCooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled() || e.finalDamage() <= 0D) {
      return;
    }
    AttackContext hit = resolveAttackContext(e);
    if (hit == null || !(hit.target() instanceof LivingRef victim) || isMelee(hit.attacker().inventory().mainHand())
        || !(victim instanceof PlayerRef || victim.has(EntityTrait.ENEMY))) {
      return;
    }
    PlayerRef p = hit.attacker();
    long now = System.currentTimeMillis();
    int stacks = build(p, now);
    boolean farmed = MutationPayouts.farmed(p, victim);
    if (finisher(p, e)) {
      streaks.remove(p.uuid());
      finishers.put(p.uuid(), e.finalDamage());
      try {
        fire(p, victim, null, farmed);
      } finally {
        finishers.remove(p.uuid());
      }
      return;
    }
    if (!farmed && usable()) {
      int level = hit.level();
      double amount = stacks * siphonPerStack(level);
      Tasks.runEntity(p, () -> siphon(p, victim, amount, level));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    finishers.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    Double dealt = finishers.remove(use.player().uuid());
    if (dealt == null || dealt <= 0D) {
      return Outcome.MISSED;
    }
    PlayerRef p = use.player();
    double healed = heal(p, dealt * getConfig().finisherHealShare);
    if (healed <= 0D) {
      return Outcome.MISSED;
    }
    fx(p.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .burst(Particles.DAMAGE_INDICATOR, 10, 0.4D)
        .particle(Particles.HEART, 3, 0D, 0.4D, 0D, 0.3D, 0D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.8F, 0.6F, Sounds.ENTITY_WITHER_HURT, 0.3F, 1.6F);
    return Outcome.INSTANT;
  }

  private int build(PlayerRef p, long now) {
    Streak streak = streaks.computeIfAbsent(p.uuid(), (UUID ignored) -> new Streak());
    if (now - streak.lastHitMillis > getConfig().streakWindowMillis) {
      streak.stacks = 0;
    }
    streak.lastHitMillis = now;
    streak.stacks = Math.min(Math.max(1, getConfig().maxStacks), streak.stacks + 1);
    return streak.stacks;
  }

  private boolean finisher(PlayerRef p, EntityDamageByEntityEvent e) {
    Adaptation<?> combo = getServer().getSkillRegistry().getAdaptation(UNBROKEN_COMBO);
    return combo instanceof UnbrokenCombo unbroken && unbroken.finished(p, e);
  }

  private void siphon(PlayerRef p, LivingRef victim, double amount, int level) {
    if (!p.isOnline() || p.isDead() || amount <= 0D) {
      return;
    }
    long now = System.currentTimeMillis();
    Budget budget = budgets.computeIfAbsent(p.uuid(), (UUID ignored) -> new Budget());
    if (now - budget.windowStart >= 1000L) {
      budget.windowStart = now;
      budget.healed = 0D;
    }
    double room = siphonCapPerSecond(level) - budget.healed;
    if (room <= 0D) {
      return;
    }
    double healed = heal(p, Math.min(amount, room));
    if (healed <= 0D) {
      return;
    }
    budget.healed += healed;
    fx(victim.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .line(Particles.DAMAGE_INDICATOR, p.location().x(), p.location().y() + 1D, p.location().z(), 4);
  }

  private static double heal(PlayerRef p, double amount) {
    double attribute = p.attributeValue(Attributes.MAX_HEALTH);
    double max = Double.isNaN(attribute) ? 20D : attribute;
    double healed = Math.max(0D, Math.min(amount, max - p.health()));
    if (healed > 0D) {
      p.setHealth(p.health() + healed);
    }
    return healed;
  }

  double siphonPerStack(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().siphonPerStackAtFirstLevel, getConfig().siphonPerStackAtTopLevel);
  }

  double siphonCapPerSecond(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().siphonCapPerSecondAtFirstLevel, getConfig().siphonCapPerSecondAtTopLevel);
  }

  private static final class Streak {
    private int stacks;
    private long lastHitMillis;
  }

  private static final class Budget {
    private long windowStart;
    private double healed;
  }

  @ConfigDescription("Bare-hand streaks siphon health per stack, and a finisher heals half its damage.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Health siphoned per streak stack on each bare-hand hit at level 1.", impact = "Higher values heal more from long streaks.")
    double siphonPerStackAtFirstLevel = 0.2;
    @ConfigDoc(value = "Health siphoned per streak stack on each bare-hand hit at the top level.", impact = "Higher values heal more from long streaks.")
    double siphonPerStackAtTopLevel = 0.4;
    @ConfigDoc(value = "Most health siphoned per second at level 1; Crimson Tide's cap.", impact = "Lower values limit healing from fast or crowded fights.")
    double siphonCapPerSecondAtFirstLevel = 5;
    @ConfigDoc(value = "Most health siphoned per second at the top level; Crimson Tide's cap.", impact = "Lower values limit healing from fast or crowded fights.")
    double siphonCapPerSecondAtTopLevel = 9;
    @ConfigDoc(value = "Most streak stacks.", impact = "Higher values build longer streaks.")
    int maxStacks = 8;
    @ConfigDoc(value = "Time a streak survives without a bare-hand hit, in milliseconds.", impact = "Higher values keep streaks alive between punches.")
    long streakWindowMillis = 3000;
    @ConfigDoc(value = "Share of a finisher's damage healed back.", impact = "Higher values make finishers heal more.")
    double finisherHealShare = 0.5;
    @ConfigDoc(value = "Time between finisher heals, in milliseconds; Unbroken Combo's finisher cooldown.", impact = "Higher values make finisher heals rarer.")
    long finisherCooldownMillis = 8000;
  }
}
