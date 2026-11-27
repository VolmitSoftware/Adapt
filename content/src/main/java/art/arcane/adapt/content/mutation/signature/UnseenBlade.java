package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthFightStrike;
import art.arcane.adapt.content.adaptation.sword.SwordBleed;
import art.arcane.adapt.content.adaptation.sword.SwordPreferences;
import art.arcane.adapt.content.adaptation.sword.upgraded.Witherblade;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swords leading Stealth, replacing Convergence: a sword critical hit on a creature that cannot see you stops its
 * healing for twice Witherblade's time and bleeds it hard; if it dies within 3 s you vanish for 2 s.
 */
public final class UnseenBlade extends Signature<UnseenBlade.Config> {
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final Rgb ASH = Rgb.of(0x2B2B2B);

  private final SwordBleed bleed = new SwordBleed(this, (PlayerRef source, double dealt) -> {
  });
  private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();
  private final Map<UUID, Long> hiddenUntil = new ConcurrentHashMap<>();

  public UnseenBlade(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("tinted_glass"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    Witherblade witherblade = witherblade();
    statLore(v, Format.duration(witherblade == null ? 0L : noHealMillis(witherblade, level), 1), 1);
    statLore(v, Format.f(bleedPerPulse(level) * SwordBleed.procCount(getConfig().bleedMillis), 1), 2);
    statLore(v, Format.duration(getConfig().vanishMillis, 1), 3);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!e.isCritical()) {
      return;
    }
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    Witherblade witherblade = combat == null || getActiveLevel(combat.attacker()) <= 0 ? null : witherblade();
    if (witherblade == null
        || !witherblade.preference(combat.attacker(), SwordPreferences.TARGETS).accepts(combat.target())
        || !ShadowSteps.unseenBy(this, combat.attacker(), combat.target())) {
      return;
    }
    fire(combat.attacker(), combat.target(), combat.target().location(), false);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    UUID victim = e.entity().uuid();
    Mark mark = marks.remove(victim);
    if (mark == null || mark.until() < System.currentTimeMillis()) {
      return;
    }
    PlayerRef player = mark.player();
    if (player.isOnline() && !player.isDead()
        && !ShadowSteps.huntedByAnother(player, victim, getConfig().huntRadius)) {
      vanish(player);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef player)) {
      return;
    }
    Long until = hiddenUntil.get(player.uuid());
    if (until == null) {
      return;
    }
    if (until < System.currentTimeMillis()) {
      hiddenUntil.remove(player.uuid(), until);
      return;
    }
    e.setCancelled(true);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    hiddenUntil.remove(id);
    marks.values().removeIf((Mark mark) -> mark.player().uuid().equals(id));
  }

  @Override
  public Outcome run(BlockUse use) {
    LivingRef target = use.target();
    Witherblade witherblade = witherblade();
    if (target == null || witherblade == null) {
      return Outcome.MISSED;
    }
    PlayerRef player = use.player();
    int level = use.level();
    if (!bleed.start(target, player, bleedPerPulse(level), SwordBleed.procCount(getConfig().bleedMillis))) {
      return Outcome.MISSED;
    }
    long now = System.currentTimeMillis();
    long noHeal = noHealMillis(witherblade, level);
    witherblade.stopHealing(target, noHeal);
    marks.values().removeIf((Mark mark) -> mark.until() < now);
    marks.put(target.uuid(), new Mark(player, now + getConfig().killWindowMillis));
    fx(target.location().add(0, 1, 0), FxPriority.COMBAT)
        .dustBurst(ASH, 14, 0.35D, 1.2F)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0D, 0D);
    return Outcome.lasting(target, (int) Math.max(1L, noHeal / 50L));
  }

  private void vanish(PlayerRef player) {
    int ticks = (int) Math.max(1L, getConfig().vanishMillis / 50L);
    player.addEffect(EffectInstance.of(INVISIBILITY, ticks, 0).withParticles(false).withIcon(false));
    hiddenUntil.put(player.uuid(), System.currentTimeMillis() + getConfig().vanishMillis);
    StealthFightStrike.shakeOff(player, getConfig().huntRadius);
    fx(player.location().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 12, 0.4D)
        .ring(Particles.SQUID_INK, 0.7D, 10, 0.05D);
  }

  /** Witherblade, whose no-healing the strike borrows; null when it is missing or disabled. */
  private @Nullable Witherblade witherblade() {
    Witherblade witherblade = PairParts.owned(Id.adapt("swords/upgrade/witherblade"), Witherblade.class);
    return witherblade != null && witherblade.isEnabled() ? witherblade : null;
  }

  /** Twice Witherblade's no-healing at the same level. */
  static long noHealMillis(Witherblade witherblade, int level) {
    return witherblade.getDurationMillis(level) * 2L;
  }

  double bleedPerPulse(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().bleedPerPulseAtFirstLevel,
        getConfig().bleedPerPulseAtTopLevel);
  }

  private record Mark(PlayerRef player, long until) {
  }

  @ConfigDescription("A sword critical hit from stealth stops healing and bleeds hard; a kill within 3 s hides you for 2 s.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Bleed damage per pulse at level 1.", impact = "Higher values make the bleed hit harder.")
    double bleedPerPulseAtFirstLevel = 0.6D;
    @ConfigDoc(value = "Bleed damage per pulse at the top level.", impact = "Higher values make the bleed hit harder.")
    double bleedPerPulseAtTopLevel = 1.0D;
    @ConfigDoc(value = "How long the bleed lasts, in milliseconds.", impact = "Higher values add more bleed pulses.")
    long bleedMillis = 3_000L;
    @ConfigDoc(value = "Time after the hit in which the target's death hides you, in milliseconds.", impact = "Higher values give more time to finish it.")
    long killWindowMillis = 3_000L;
    @ConfigDoc(value = "How long you vanish after the kill, in milliseconds.", impact = "Higher values keep you hidden longer.")
    long vanishMillis = 2_000L;
    @ConfigDoc(value = "Reach in blocks within which another mob targeting you stops the vanish, and mobs lose you.", impact = "Higher values make the vanish harder to earn.")
    double huntRadius = 16D;
    @ConfigDoc(value = "Cooldown after a strike, in milliseconds.", impact = "Higher values make the strike rarer.")
    long cooldownMillis = 20_000L;
  }
}
