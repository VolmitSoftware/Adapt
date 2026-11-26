package art.arcane.adapt.content.adaptation.blocking.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.blocking.BlockingCounterGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingMirror;
import art.arcane.adapt.content.adaptation.blocking.BlockingMirrorBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingParry;
import art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingPreferences;
import art.arcane.adapt.content.adaptation.blocking.BlockingRetaliation;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

/**
 * Perfect Guard, Mirror Block and Counter Guard fused: a shield raised just before a hit negates it, then a
 * projectile flies back at its shooter and a melee hit is dealt back to the attacker.
 */
public class MirrorGuard extends SkillUpgrade<MirrorGuard.Config> {
  private final BlockingParry parry = new BlockingParry(this, playerState());
  private final BlockingMirror mirror = new BlockingMirror(this, "mirror-guard-damage-factor", this::reflected);
  private final BlockingRetaliation retaliation = new BlockingRetaliation(this);
  private final Cooldowns cooldowns = cooldowns();

  public MirrorGuard() {
    super("upgraded-blocking-mirror-guard");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("light_weighted_pressure_plate"));
  }

  @Override
  protected void onRuntimeActivated() {
    mirror.activate();
    retaliation.activate();
  }

  @Override
  public void unregister() {
    mirror.close();
    retaliation.close();
    super.unregister();
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.PLAYERS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.YELLOW, "* ", Form.duration(getWindowMillis(level), 2), 1);
    statLore(v, Form.pc(getReflectShare(level), 0), 2);
    statLore(v, C.RED, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    parry.raise(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (retaliation.consume(e.damager(), e.entity())) {
      return;
    }

    if (e.damager() instanceof ProjectileRef projectile) {
      mirror.applyReflectedDamage(e, projectile);
    }

    if (!(e.entity() instanceof PlayerRef defender)) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || !BlockingShields.held(this, defender)) {
      return;
    }

    long now = System.currentTimeMillis();
    long cooldownMillis = getCooldownMillis(level);
    if (!parry.timed(defender, getWindowMillis(level), now) || !cooldowns.isReady(defender.uuid(), cooldownMillis)
        || !BlockingParry.faces(defender, e.damager(), getConfig().minFacingAlignment)) {
      return;
    }

    double damage = e.damage();
    e.setCancelled(true);
    parry.consume(defender);
    cooldowns.mark(defender.uuid(), cooldownMillis);
    AbilityGlyphs.cooldown(defender.uuid(), this, now, cooldownMillis);
    BlockingPerfectGuard.parryFlash(this, defender);
    cue(defender, MutationCue.FIGHT);
    xpFromAttacker(defender, e.damager(), getConfig().xpOnNegate);
    addStat(defender, "blocking.mirror-guard.hits-negated", 1);

    LivingRef attacker = BlockingParry.attacker(e.damager());
    if (attacker instanceof PlayerRef && !preferenceEnabled(defender, BlockingPreferences.PLAYERS)) {
      return;
    }

    double share = getReflectShare(level);
    if (e.damager() instanceof ProjectileRef projectile) {
      if (mirror.accepting() && !BlockingMirror.reflected(projectile)) {
        mirror.reflect(defender, projectile, new BlockingMirror.Shot(getConfig().reflectVelocityFactor, share,
            cooldownMillis, getConfig().minReflectedVelocitySquared, getConfig().fallbackReflectedSpeed));
      }
      return;
    }

    if (attacker != null && retaliation.accepting()) {
      retaliation.strike(defender, attacker, damage * share, () -> {
      }, (Loc attackerLocation) -> {
        BlockingCounterGuard.reflectDischarge(this, defender, attackerLocation);
        addStat(defender, "blocking.mirror-guard.hits-reflected", 1);
      });
    }
  }

  private void reflected(PlayerRef defender, BlockingMirror.Reflection reflection) {
    BlockingMirrorBlock.mirrorFlash(this, defender);
    addStat(defender, "blocking.mirror-guard.hits-reflected", 1);
  }

  long getWindowMillis(int level) {
    return (long) (getConfig().windowMillisBase + (level * getConfig().windowMillisPerLevel));
  }

  double getReflectShare(int level) {
    return getConfig().reflectShareBase + (level * getConfig().reflectSharePerLevel);
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  @ConfigDescription("A shield raised just before a hit negates it and sends it back, melee or projectile.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Timing window before level scaling, in milliseconds.", impact = "Higher values make the timed block easier to land.")
    double windowMillisBase = 250;
    @ConfigDoc(value = "Timing window added per level, in milliseconds.", impact = "Higher values widen the window at higher levels.")
    double windowMillisPerLevel = 100;
    @ConfigDoc(value = "Lowest dot product between your view and the attacker's direction that still counts as facing it.", impact = "Higher values require the attacker to be more directly in front of you.")
    double minFacingAlignment = 0.15;
    @ConfigDoc(value = "Share of the negated hit sent back before level scaling.", impact = "Higher values make reflected hits stronger.")
    double reflectShareBase = 0.6;
    @ConfigDoc(value = "Share of the negated hit sent back, added per level.", impact = "Higher values reward higher levels more.")
    double reflectSharePerLevel = 0.2;
    @ConfigDoc(value = "Speed of a reflected projectile relative to its incoming speed.", impact = "Higher values send projectiles back faster.")
    double reflectVelocityFactor = 1.0;
    @ConfigDoc(value = "Squared speed under which a reflected projectile is relaunched along your view instead.", impact = "Higher values relaunch more slow projectiles along your view.")
    double minReflectedVelocitySquared = 0.08;
    @ConfigDoc(value = "Speed of a projectile relaunched along your view.", impact = "Higher values send slow projectiles back faster.")
    double fallbackReflectedSpeed = 1.2;
    @ConfigDoc(value = "Cooldown before level scaling, in milliseconds.", impact = "Higher values make timed blocks rarer.")
    double cooldownMillisBase = 3000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels guard more often.")
    double cooldownMillisPerLevel = 500;
    @ConfigDoc(value = "Shortest cooldown at any level, in milliseconds.", impact = "Lower values allow more frequent timed blocks.")
    double minimumCooldownMillis = 1000;
    @ConfigDoc(value = "Skill XP for each negated hit.", impact = "Higher values reward timed blocks.")
    double xpOnNegate = 16;
  }
}
