package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.taming.TameInterceptor;
import art.arcane.adapt.content.adaptation.taming.TameLastStand;
import art.arcane.adapt.content.adaptation.taming.TamePainShare;
import art.arcane.adapt.content.adaptation.taming.TamingLastBreath;
import art.arcane.adapt.content.adaptation.taming.TamingPreferences;
import art.arcane.adapt.content.adaptation.taming.TamingSharedPain;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.UUID;

/**
 * Shared Pain, Guardian Instinct and Last Breath fused: your damage spreads across nearby pets, a pet leaps in front of
 * shots aimed at you, and a pet that would die stays at 1 HP and returns to you.
 */
public class BloodBond extends SkillUpgrade<BloodBond.Config> {
  public static final Id ID = Id.adapt("taming/upgrade/blood_bond");

  private final TamePainShare painShare = new TamePainShare(this, cooldowns());
  private final TameInterceptor interceptor = new TameInterceptor(this);
  private final Cooldowns interceptCooldowns = cooldowns();
  private final TameLastStand lastStand = new TameLastStand(this, cooldowns(), (PlayerRef owner) -> {
    addStat(owner, "taming.blood-bond.saves", 1);
  });

  public BloodBond() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("name_tag"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.HEALTH);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSharePercent(level), 0), 1);
    statLore(v, Format.pc(getInterceptChance(level), 0), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration((double) getSaveCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onProtectedWindow(EntityDamageEvent e) {
    lastStand.guard(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    int invulnTicks = Math.max(10, getConfig().invulnTicks);
    LivingRef saved = lastStand.save(e, this::getSaveCooldownMillis, invulnTicks);
    if (saved != null) {
      UUID ownerId = saved.tameOwner();
      PlayerRef owner = ownerId == null ? null : AdaptApi.platform().host().player(ownerId);
      if (owner != null) {
        cue(owner, MutationCue.BOON);
      }
      LingeringSound lingering = linger(saved);
      AdaptApi.scheduler().runEntity(saved, lingering::stop, invulnTicks);
      return;
    }

    if (!(e.entity() instanceof PlayerRef owner) || e.finalDamage() <= TamePainShare.MINIMUM_TRANSFER) {
      return;
    }
    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }
    double transferred = painShare.shareHit(e, owner, getSharePercent(level), getShareRadius(),
        getConfig().petHealthFloor, TamePainShare.petLimit(getConfig().maxPets));
    if (transferred <= TamePainShare.MINIMUM_TRANSFER) {
      return;
    }
    addStat(owner, "taming.blood-bond.damage-shared", transferred);
    if (e instanceof EntityDamageByEntityEvent byEntity) {
      xpFromAttacker(owner, byEntity.damager(), transferred * getConfig().xpPerSharedDamage);
    }
    cue(owner, MutationCue.FIGHT);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef owner) || !(e.damager() instanceof ProjectileRef)) {
      return;
    }
    int level = getActiveLevel(owner);
    double damage = e.finalDamage();
    if (level <= 0 || damage <= 0 || Math.random() > getInterceptChance(level)) {
      return;
    }
    UUID ownerId = owner.uuid();
    long cooldownMillis = (long) getConfig().interceptCooldownMillis;
    if (!interceptCooldowns.isReady(ownerId, cooldownMillis)
        || !interceptor.intercept(owner, damage, getConfig().radius, getConfig().petReduction, getConfig().leapStrength)) {
      return;
    }
    interceptCooldowns.mark(ownerId, cooldownMillis);
    e.setCancelled(true);
    addStat(owner, "taming.blood-bond.intercepts", 1);
    xpFromAttacker(owner, e.damager(), damage * getConfig().xpPerInterceptedDamage);
    cue(owner, MutationCue.FIGHT);
  }

  public double getSharePercent(int level) {
    return TamingSharedPain.clampRedirectPercent(getConfig().sharePercentBase + (level * getConfig().sharePercentPerLevel),
        getConfig().maxSharePercent);
  }

  public double getShareRadius() {
    return getConfig().radius;
  }

  double getInterceptChance(int level) {
    return Math.min(getConfig().maxInterceptChance, getConfig().interceptChanceBase + (level * getConfig().interceptChancePerLevel));
  }

  long getSaveCooldownMillis(int level) {
    return TamingLastBreath.cooldownMillis(level / (double) getMaxLevel(), getConfig().saveCooldownMillisBase,
        getConfig().saveCooldownMillisReduction, getConfig().minimumSaveCooldownMillis);
  }

  @ConfigDescription("Pets share your damage, intercept shots aimed at you, and return to you at 1 HP instead of dying.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Share of your incoming damage spread across nearby pets before level scaling.", impact = "Higher values move more of every hit onto your pets.")
    double sharePercentBase = 0.25;
    @ConfigDoc(value = "Share of incoming damage added per level.", impact = "Higher values reward higher levels more.")
    double sharePercentPerLevel = 0.1;
    @ConfigDoc(value = "Highest share of a hit your pets can take.", impact = "Caps the shared damage.")
    double maxSharePercent = 0.6;
    @ConfigDoc(value = "Health each pet keeps before it stops taking shared damage.", impact = "Higher values protect pets more and share less.")
    double petHealthFloor = 2.0;
    @ConfigDoc(value = "Radius in blocks searched for pets that share damage or intercept shots.", impact = "Higher values let farther pets help.")
    double radius = 12.0;
    @ConfigDoc(value = "Most pets that share one hit, capped at 16.", impact = "Higher values spread damage more thinly.")
    int maxPets = 8;
    @ConfigDoc(value = "Chance a pet intercepts a shot before level scaling.", impact = "Higher values make intercepts more common.")
    double interceptChanceBase = 0.4;
    @ConfigDoc(value = "Intercept chance added per level.", impact = "Higher values reward higher levels more.")
    double interceptChancePerLevel = 0.15;
    @ConfigDoc(value = "Highest intercept chance at any level.", impact = "Caps the intercept chance.")
    double maxInterceptChance = 0.85;
    @ConfigDoc(value = "Share of an intercepted shot the pet does not take.", impact = "Higher values make intercepts cheaper for the pet.")
    double petReduction = 0.6;
    @ConfigDoc(value = "Leap velocity of a pet intercepting a shot.", impact = "Higher values make the pet lunge harder.")
    double leapStrength = 0.8;
    @ConfigDoc(value = "Time between two intercepts, in milliseconds.", impact = "Lower values let pets block more of a barrage.")
    double interceptCooldownMillis = 1000;
    @ConfigDoc(value = "Per-pet time between two saves at level 1, in milliseconds.", impact = "Higher values make saves rarer.")
    double saveCooldownMillisBase = 240000;
    @ConfigDoc(value = "Save cooldown removed at the top level, in milliseconds.", impact = "Higher values shorten the cooldown faster as you level.")
    double saveCooldownMillisReduction = 180000;
    @ConfigDoc(value = "Shortest per-pet save cooldown, in milliseconds.", impact = "Lower values allow more frequent saves.")
    double minimumSaveCooldownMillis = 45000;
    @ConfigDoc(value = "Ticks a saved pet cannot be hurt, at least 10.", impact = "Higher values give the pet longer to escape.")
    int invulnTicks = 60;
    @ConfigDoc(value = "Skill XP per point of damage your pets share.", impact = "Higher values reward fighting beside your pets.")
    double xpPerSharedDamage = 2.0;
    @ConfigDoc(value = "Skill XP per point of damage a pet intercepts.", impact = "Higher values reward intercepts.")
    double xpPerInterceptedDamage = 2.0;
  }
}
