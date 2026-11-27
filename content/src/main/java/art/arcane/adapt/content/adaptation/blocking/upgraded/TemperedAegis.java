package art.arcane.adapt.content.adaptation.blocking.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.blocking.BlockingAxeBlows;
import art.arcane.adapt.content.adaptation.blocking.BlockingBash;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.content.adaptation.blocking.BlockingTemper;
import art.arcane.adapt.content.adaptation.blocking.BlockingTemperedGuard;
import art.arcane.adapt.api.ability.AbilityDamage;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;

/**
 * Tempered Guard and Shieldbearer's Resolve fused, with Bulwark Bash's slam. A blocked hit from a natural mob or a player gives
 * back at most what it wore off the shield, shield first and the rest to armor; hits from farmed mobs, dispensers and
 * anything else repair nothing. An axe blow on the raised shield no longer leaves it on cooldown. Sprint, jump and raise
 * the shield in the air to slam the first creature in front of you, staggering it.
 */
public class TemperedAegis extends SkillUpgrade<TemperedAegis.Config> {
  public static final Id ID = Id.adapt("blocking/upgrade/tempered_aegis");

  /** Two ticks so a longer disable that axe adaptations apply one tick after the blow is cleared as well. */
  private static final int RESTORE_DELAY_TICKS = 2;
  private static final long NATURAL_HIT_WINDOW_MILLIS = 1_000L;
  private static final double BASH_REACH = 2.0D;
  private static final double BASH_FACING = 0.5D;
  private final Map<UUID, Long> naturalHits = playerState();
  private final Cooldowns sprints = cooldowns();
  private final Cooldowns bashes = cooldowns();

  public TemperedAegis() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blast_furnace"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getShieldRepair(level), 0), 1);
    statLore(v, Format.f(getArmorRepair(level), 0), 2);
    statLore(v, Format.f(getBashDamage(level), 1), 3);
    statLore(v, Format.duration(getBashCooldownMillis(level), 0), 4);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemDamageEvent e) {
    if (!BlockingTemperedGuard.isShieldDamage(e.item()) || e.damage() <= 0) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    Long naturalHit = naturalHits.remove(p.uuid());
    if (level <= 0 || naturalHit == null || System.currentTimeMillis() - naturalHit >= NATURAL_HIT_WINDOW_MILLIS) {
      return;
    }

    int wear = e.damage();
    AdaptApi.scheduler().runEntity(p, () -> repair(p, wear, level), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.entity() instanceof PlayerRef defender) {
      EntityRef attacker = attacker(e.damager());
      if (!e.isCancelled() && defender.isBlocking() && attacker instanceof LivingRef
          && !Mutations.farmed(defender, attacker)) {
        naturalHits.put(defender.uuid(), System.currentTimeMillis());
      } else {
        naturalHits.remove(defender.uuid());
      }
    }
    PlayerRef p = BlockingAxeBlows.defender(this, e);
    if (p == null || getActiveLevel(p) <= 0) {
      return;
    }

    EntityRef damager = e.damager();
    AdaptApi.scheduler().runEntity(p, () -> restore(p, damager), RESTORE_DELAY_TICKS);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSprintEvent e) {
    sprints.mark(e.player().uuid());
  }

  @Handle
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!p.isBlocking() || p.isOnGround() || p.isInWater() || p.vehicle() != null || p.isClimbing()
        || (!p.isSprinting() && sprints.isReady(p.uuid(), getConfig().recentSprintWindowMillis))) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0 || !bashes.isReady(p.uuid(), getBashCooldownMillis(level)) || !BlockingShields.held(this, p)) {
      return;
    }
    ItemRef shield = BlockingShields.preferred(p);
    if (!shield.isUnbreakable() && shield.maxDamage() - shield.damage() <= getConfig().bashDurability) {
      return;
    }

    LivingRef target = firstInFront(p);
    if (target == null) {
      return;
    }
    bash(p, target, level);
  }

  private LivingRef firstInFront(PlayerRef p) {
    Loc origin = p.location();
    Vec3 facing = origin.direction().withY(0);
    if (facing.lengthSquared() < 1.0E-6) {
      return null;
    }
    facing = facing.normalize();
    LivingRef best = null;
    double bestDistance = Double.MAX_VALUE;
    for (EntityRef nearby : origin.world().nearbyEntities(origin.position(), BASH_REACH, 1.5D, BASH_REACH)) {
      if (!(nearby instanceof LivingRef living) || living.uuid().equals(p.uuid()) || living.isDead()
          || !AdaptationDamageTargets.allows(living, false) || isProtectedFriendly(p, living)
          || p.uuid().equals(living.tameOwner())) {
        continue;
      }
      Vec3 toTarget = living.location().position().subtract(origin.position()).withY(0);
      double distance = toTarget.length();
      if (distance < 1.0E-3 || distance > BASH_REACH || toTarget.multiply(1D / distance).dot(facing) < BASH_FACING) {
        continue;
      }
      boolean allowed = living instanceof PlayerRef ? canPVP(p, living.location()) : canPVE(p, living.location());
      if (allowed && distance < bestDistance) {
        best = living;
        bestDistance = distance;
      }
    }
    return best;
  }

  private void bash(PlayerRef p, LivingRef target, int level) {
    bashes.mark(p.uuid());
    BlockingShields.wear(this, p, getConfig().bashDurability);
    Loc origin = p.location();
    BlockingBash.impact(target, origin, origin.direction().withY(0), getConfig().bashKnockback,
        getConfig().bashUpwardKnockback, getConfig().bashStaggerTicks, getConfig().bashStaggerAmplifier);
    AbilityDamage.deal(target, getBashDamage(level), p);
    xpOn(p, target, getConfig().xpPerBash);
    addStat(p, "blocking.tempered-aegis.bashes", 1);
    cue(p, MutationCue.FIGHT);
    fx(target.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT, 6, 0.3D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 0.8F, Sounds.BLOCK_ANVIL_PLACE, 0.4F, 1.3F);
  }

  private static EntityRef attacker(EntityRef damager) {
    return damager instanceof ProjectileRef projectile && projectile.shooter() != null ? projectile.shooter() : damager;
  }

  private void repair(PlayerRef p, int wear, int level) {
    if (!p.isOnline()) {
      return;
    }

    PlayerInventoryRef inventory = p.inventory();
    int shield = BlockingTemper.repairShield(this, inventory, Math.min(wear, getShieldRepair(level)));
    int armor = BlockingTemper.repairArmor(this, inventory, Math.min(wear - shield, getArmorRepair(level)));
    int repaired = shield + armor;
    if (repaired <= 0) {
      return;
    }

    addStat(p, "blocking.tempered-aegis.durability-repaired", repaired);
    BlockingTemperedGuard.temperGlint(this, p);
    cue(p, MutationCue.BOON);
  }

  private void restore(PlayerRef p, EntityRef damager) {
    if (!p.isOnline() || getActiveLevel(p) <= 0 || BlockingAxeBlows.disabledTicks(p) <= 0) {
      return;
    }

    p.setCooldown(BlockingShields.SHIELD, 0);
    addStat(p, "blocking.tempered-aegis.disables-shrugged", 1);
    xpFromAttacker(p, damager, getConfig().xpOnShrug);
    cue(p, MutationCue.FIGHT);
    fx(p.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.6D, 8, 0)
        .chord(Sounds.BLOCK_ANVIL_USE, 0.5F, 1.2F, Sounds.ITEM_SHIELD_BLOCK, 0.8F, 1.1F);
  }

  int getShieldRepair(int level) {
    return Math.max(1, (int) Math.round(getConfig().shieldRepairBase + (level * getConfig().shieldRepairPerLevel)));
  }

  int getArmorRepair(int level) {
    return Math.max(1, (int) Math.round(getConfig().armorRepairBase + (level * getConfig().armorRepairPerLevel)));
  }

  double getBashDamage(int level) {
    return getConfig().bashDamageBase + (level * getConfig().bashDamagePerLevel);
  }

  long getBashCooldownMillis(int level) {
    return Math.max(500L, getConfig().bashCooldownMillisBase - (level * getConfig().bashCooldownMillisPerLevel));
  }

  @ConfigDescription("Blocked hits from real foes repair your shield and armor, axes no longer disable your shield, and a sprint-jump with the shield up slams a foe.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Shield durability restored per blocked hit from a natural mob or a player before level scaling, never more than the hit wore off.", impact = "Higher values keep your shield whole longer.")
    double shieldRepairBase = 2;
    @ConfigDoc(value = "Shield durability restored per blocked hit, added per level.", impact = "Higher values reward higher levels more.")
    double shieldRepairPerLevel = 2;
    @ConfigDoc(value = "Armor durability restored per blocked hit before level scaling, from what the shield repair left of the hit's wear.", impact = "Higher values keep your armor whole longer.")
    double armorRepairBase = 1;
    @ConfigDoc(value = "Armor durability restored per blocked hit, added per level.", impact = "Higher values reward higher levels more.")
    double armorRepairPerLevel = 1;
    @ConfigDoc(value = "Skill XP each time an axe from a natural mob or a player fails to disable your shield.", impact = "Higher values reward holding against axes.")
    double xpOnShrug = 12;
    @ConfigDoc(value = "Bash damage before level scaling.", impact = "Higher values hit harder.")
    double bashDamageBase = 1.5;
    @ConfigDoc(value = "Bash damage added per level.", impact = "Higher values reward higher levels more.")
    double bashDamagePerLevel = 1.5;
    @ConfigDoc(value = "Bash cooldown before level scaling, in milliseconds.", impact = "Lower values bash more often.")
    long bashCooldownMillisBase = 7000;
    @ConfigDoc(value = "Milliseconds of bash cooldown removed per level.", impact = "Higher values shorten the cooldown at higher levels.")
    long bashCooldownMillisPerLevel = 1000;
    @ConfigDoc(value = "Shield durability each bash costs.", impact = "Higher values wear your shield faster.")
    int bashDurability = 5;
    @ConfigDoc(value = "How long after sprinting a raised shield in the air still bashes, in milliseconds.", impact = "Higher values give more time to raise the shield after the sprint-jump.")
    long recentSprintWindowMillis = 900;
    @ConfigDoc(value = "Horizontal shove given to the bashed target.", impact = "Higher values push it farther.")
    double bashKnockback = 1.0;
    @ConfigDoc(value = "Upward shove given to the bashed target.", impact = "Higher values lift it more.")
    double bashUpwardKnockback = 0.25;
    @ConfigDoc(value = "Ticks the bashed target is staggered.", impact = "Higher values stagger longer.")
    int bashStaggerTicks = 30;
    @ConfigDoc(value = "Slowness amplifier of the stagger.", impact = "Higher values slow the target more.")
    int bashStaggerAmplifier = 2;
    @ConfigDoc(value = "Skill XP per bash on a natural mob or a player.", impact = "Higher values reward bashing.")
    double xpPerBash = 8;
  }
}
