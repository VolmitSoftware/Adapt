package art.arcane.adapt.content.adaptation.sword.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.sword.SwordBleed;
import art.arcane.adapt.content.adaptation.sword.SwordPreferences;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Poisoned Blade and Bloody Blade fused: sword hits wither and bleed the target, which cannot heal meanwhile. */
public class Witherblade extends SkillUpgrade<Witherblade.Config> {
  public static final Id ID = Id.adapt("swords/upgrade/witherblade");

  private static final Id WITHER = Id.minecraft("wither");
  private static final Rgb ASH = Rgb.of(0x2B2B2B);
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Long> noRegenUntil = new ConcurrentHashMap<>();
  private final SwordBleed bleed = new SwordBleed(this,
      (PlayerRef source, double dealt) -> addStat(source, "swords.witherblade.bleed-damage", dealt));

  public Witherblade() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_rose"));
    declareOvercharge();
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getDurationMillis(level), 1), 1);
    statLore(v, Format.f(getBleedDamage(level), 2), 2);
    statLore(v, TextFormat.RED, "* ", Format.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat == null || !preference(combat.attacker(), SwordPreferences.TARGETS).accepts(combat.target())) {
      return;
    }

    PlayerRef player = combat.attacker();
    int level = combat.level();
    long cooldownMillis = getCooldownMillis(level);
    if (!cooldowns.isReady(player.uuid(), cooldownMillis)) {
      return;
    }

    LivingRef target = combat.target();
    long durationMillis = getDurationMillis(level);
    if (!bleed.start(target, player, getBleedDamage(level), SwordBleed.procCount(durationMillis))) {
      return;
    }
    cooldowns.mark(player.uuid(), cooldownMillis);
    durationMillis = Math.round(durationMillis * overcharge(player));
    int durationTicks = (int) Math.max(1L, durationMillis / 50L);
    target.addEffect(EffectInstance.of(WITHER, durationTicks, level >= getMaxLevel() ? getConfig().witherAmplifierAtMaxLevel : 0));
    stopHealing(target, durationMillis);

    cue(player, MutationCue.FIGHT);
    LingeringSound lingering = linger(target);
    AdaptApi.scheduler().runEntity(target, lingering::stop, durationTicks);
    fx(target.location().add(0, 1, 0), FxPriority.COMBAT)
        .dustBurst(ASH, 10, 0.3D, 1.1F)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.02D);
  }

  /** {@code target} cannot heal for {@code durationMillis}; a longer wound already running is kept. */
  public void stopHealing(LivingRef target, long durationMillis) {
    if (durationMillis <= 0L) {
      return;
    }
    long now = System.currentTimeMillis();
    noRegenUntil.values().removeIf((Long until) -> until < now);
    noRegenUntil.merge(target.uuid(), now + durationMillis, Math::max);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityRegainHealthEvent e) {
    Long until = noRegenUntil.get(e.entity().uuid());
    if (until == null) {
      return;
    }
    if (until < System.currentTimeMillis()) {
      noRegenUntil.remove(e.entity().uuid(), until);
      return;
    }
    e.setCancelled(true);
  }

  public long getDurationMillis(int level) {
    return (long) (getConfig().durationMillisBase + (level * getConfig().durationMillisPerLevel));
  }

  double getBleedDamage(int level) {
    return Math.max(0.01D, getConfig().bleedDamageBase + ((level - 1) * getConfig().bleedDamagePerLevel));
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  @ConfigDescription("Sword hits wither and bleed the target; it cannot heal while the wither lasts.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Wither and bleed duration before level scaling, in milliseconds.", impact = "Higher values make every hit last longer.")
    double durationMillisBase = 2000;
    @ConfigDoc(value = "Wither and bleed duration added per level, in milliseconds.", impact = "Higher values widen the gap between levels.")
    double durationMillisPerLevel = 1000;
    @ConfigDoc(value = "Bleed damage per pulse at level 1.", impact = "Higher values make the bleed hit harder.")
    double bleedDamageBase = 0.5;
    @ConfigDoc(value = "Bleed damage per pulse added for each level after the first.", impact = "Higher values reward higher levels more.")
    double bleedDamagePerLevel = 0.25;
    @ConfigDoc(value = "Wither amplifier at the maximum level; lower levels use amplifier 0.", impact = "Higher values make the wither tick faster at the top level.")
    int witherAmplifierAtMaxLevel = 1;
    @ConfigDoc(value = "Cooldown before level scaling, in milliseconds.", impact = "Higher values make the effect rarer.")
    double cooldownMillisBase = 4500;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values make higher levels fire more often.")
    double cooldownMillisPerLevel = 1000;
    @ConfigDoc(value = "Shortest cooldown at any level, in milliseconds.", impact = "Lower values allow more frequent withering.")
    double minimumCooldownMillis = 1500;
  }
}
