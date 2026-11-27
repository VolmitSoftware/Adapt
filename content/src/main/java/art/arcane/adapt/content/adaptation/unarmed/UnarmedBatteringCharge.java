/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UnarmedBatteringCharge extends SimpleAdaptation<UnarmedBatteringCharge.Config> {
  public static final Id ID = Id.adapt("unarmed/battering_charge");
  private static final Rgb TRAIL_COLOR = Rgb.of(0xCFCFCF);
  private static final Id SHIELD = Id.minecraft("shield");
  private static final long MOVEMENT_FRESH_MILLIS = 750L;
  private static final long CHARGE_KILL_WINDOW_MILLIS = 2000L;
  private final Map<UUID, Boolean> primedState = playerState();
  private final Map<UUID, MovementSample> recentMovement = playerState();
  private final Map<UUID, ChargeMark> chargeHitMarks = new ConcurrentHashMap<>();
  private final Cooldowns trailCooldown = cooldowns();
  private final Cooldowns fistsCooldown = cooldowns();

  public UnarmedBatteringCharge() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blaze_rod"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_charge_300")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_unarmed_charge_300", "unarmed.battering-charge.charges", 300, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond"))
        .key("challenge_unarmed_charge_kills_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_unarmed_charge_kills_100", "unarmed.battering-charge.charge-kills", 100, 1000);
  }

  private final Map<UUID, Long> armedUntil = playerState();

  @Handle
  public void on(PlayerInteractEvent event) {
    PlayerRef player = event.player();
    if (event.hand() == Hand.MAIN_HAND && event.action() == InteractAction.RIGHT_CLICK_AIR
        && player.isSneaking() && getActiveLevel(player) > 0 && preferenceEnabled(player, UnarmedPreferences.ARM)) {
      long now = System.currentTimeMillis();
      armedUntil.put(player.uuid(), now + 5000L);
      HudStatus.duration(player.uuid(), this, now, 5000L);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.LOADOUT, UnarmedPreferences.ARM);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDamageBonus(level), 1), 1);
    statLore(v, Format.f(getKnockback(level), 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (p.vehicle() != null) {
      return;
    }

    if (!isChargeLoadout(p) || !p.isSprinting()) {
      return;
    }

    int level = attack.level();
    ItemRef cooldownItem = getCooldownItem(p);
    if (cooldownItem != null) {
      if (p.hasCooldown(cooldownItem.type())) {
        return;
      }
    } else if (!fistsCooldown.isReady(p.uuid(), getCooldownTicks(level) * 50L)) {
      return;
    }

    if (!hasChargeMomentum(p)) {
      return;
    }

    e.setDamage(e.damage() + getDamageBonus(level));
    EntityRef target = e.entity();
    shove(p, target, getKnockback(level));

    long cooldownMillis = getCooldownTicks(level) * 50L;
    if (cooldownItem != null) {
      p.setCooldown(cooldownItem.type(), getCooldownTicks(level));
      HudStatus.cooldown(p, this, cooldownMillis);
    } else {
      fistsCooldown.mark(p.uuid(), cooldownMillis);
    }

    long now = System.currentTimeMillis();
    chargeHitMarks.values().removeIf(mark -> mark.expiresAt() < now);
    chargeHitMarks.put(target.uuid(), new ChargeMark(p.uuid(), now + CHARGE_KILL_WINDOW_MILLIS));

    playImpact(p, target, level);
    primedState.put(p.uuid(), false);
    xp(p, e.damage() * getConfig().xpPerDamage);
    addStat(p, "unarmed.battering-charge.charges", 1);
    if (armedUntil.remove(p.uuid()) != null) {
      HudStatus.clear(p.uuid(), this, HudGlyph.DURATION);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    ChargeMark mark = chargeHitMarks.remove(e.entity().uuid());
    if (mark == null || mark.expiresAt() < System.currentTimeMillis()) {
      return;
    }
    LastDamage last = e.entity().lastDamage();
    if (last != null
        && last.damager() instanceof PlayerRef p
        && p.uuid().equals(mark.attacker())
        && hasActiveAdaptation(p)) {
      addStat(p, "unarmed.battering-charge.charge-kills", 1);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc from = e.from();
    Loc to = e.to();
    PlayerRef p = e.player();
    if (to != null && from.sameWorld(to)) {
      double dx = to.x() - from.x();
      double dz = to.z() - from.z();
      recentMovement.put(p.uuid(), new MovementSample((dx * dx) + (dz * dz), System.currentTimeMillis()));
    }
    updatePrimedState(p);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      UUID id = e.player().uuid();
      if (Boolean.TRUE.equals(primedState.remove(id))) {
        playChargeExpiry(e.player());
      }
      trailCooldown.clear(id);
      return;
    }
    updatePrimedState(e.player());
  }

  /** The charge impact: {@code target} is pushed the way {@code p} faces with {@code knockback}. */
  public static void shove(PlayerRef p, EntityRef target, double knockback) {
    target.setVelocity(target.velocity().add(p.location().direction().normalize().multiply(knockback)));
  }

  private boolean isChargeLoadout(PlayerRef p) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();
    boolean fists = !ItemKinds.isItem(main) && !ItemKinds.isItem(off);
    boolean shieldLoadout = (ItemKinds.isItem(main) && main.type().equals(SHIELD)) || (ItemKinds.isItem(off) && off.type().equals(SHIELD));
    return preference(p, UnarmedPreferences.LOADOUT).accepts(fists, shieldLoadout)
        && (!preferenceEnabled(p, UnarmedPreferences.ARM) || armedUntil.getOrDefault(p.uuid(), 0L) > System.currentTimeMillis());
  }

  private ItemRef getCooldownItem(PlayerRef p) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();
    if (ItemKinds.isItem(main) && main.type().equals(SHIELD)) {
      return main;
    }

    if (ItemKinds.isItem(off) && off.type().equals(SHIELD)) {
      return off;
    }

    return null;
  }

  private boolean isChargeReady(PlayerRef p, int level) {
    if (p.vehicle() != null || !isChargeLoadout(p) || !p.isSprinting()) {
      return false;
    }

    ItemRef cooldownItem = getCooldownItem(p);
    if (cooldownItem != null) {
      if (p.hasCooldown(cooldownItem.type())) {
        return false;
      }
    } else if (!fistsCooldown.isReady(p.uuid(), getCooldownTicks(level) * 50L)) {
      return false;
    }

    return hasChargeMomentum(p);
  }

  private boolean hasChargeMomentum(PlayerRef p) {
    MovementSample sample = recentMovement.get(p.uuid());
    return sample != null
        && System.currentTimeMillis() - sample.atMillis() <= MOVEMENT_FRESH_MILLIS
        && sample.horizontalDeltaSquared() >= getConfig().minimumVelocitySquared;
  }

  private void playImpact(PlayerRef p, EntityRef target, int level) {
    fx(p.location(), FxPriority.COMBAT)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.95F, Sounds.ENTITY_IRON_GOLEM_ATTACK, 0.5F, 0.8F);
    Loc impact = target.location().add(0, 0.7D, 0);
    fx(impact, FxPriority.COMBAT)
        .particle(Particles.EXPLOSION, 1, 0, 0, 0, 0.06D, 0.02D)
        .particle(Particles.CLOUD, 14, 0, 0, 0, 0.25D, 0.05D)
        .sound(Sounds.ENTITY_ZOGLIN_ATTACK, 0.5F, 0.8F);
    double maxRadius = 1.5D + (level * 0.4D);
    timeline(target.location())
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> fx.dustRing(0.2D + (maxRadius * progress), 20, 1.0F))
        .start();
  }

  private void playPrimeTelegraph(PlayerRef p) {
    timeline(p)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.CRIT, 1.4D - (0.9D * progress), 8, 0.1D);
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_NOTE_BLOCK_BASEDRUM, 0.55F, 1.15F);
          } else if (tick == 5) {
            fx.sound(Sounds.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4F, 1.4F);
          }
        })
        .start();
  }

  private void playChargeExpiry(PlayerRef p) {
    fx(p.location().add(0, 0.2D, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 3, 0, 0, 0, 0.1D, 0.01D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_BASEDRUM, 0.3F, 0.85F);
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBase + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private double getKnockback(int level) {
    return getConfig().knockbackBase + (getLevelPercent(level) * getConfig().knockbackFactor);
  }

  private int getCooldownTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private void updatePrimedState(PlayerRef player) {
    boolean sprinting = player.isSprinting();
    if (!sprinting && primedState.isEmpty()) {
      return;
    }

    UUID id = player.uuid();
    if (!sprinting && !primedState.containsKey(id)) {
      return;
    }

    int level = getActiveLevel(player);
    boolean primed = level > 0 && isChargeReady(player, level);
    boolean wasPrimed = primedState.getOrDefault(id, false);
    if (primed) {
      long interval = Math.max(50L, getConfig().primedTrailIntervalMillis);
      if (trailCooldown.isReady(id, interval)) {
        trailCooldown.mark(id);
        fx(player.location().add(0, 0.2D, 0), FxPriority.TRAIL).dustBurst(TRAIL_COLOR, 2, 0.2D, 0.8F);
      }
      if (!wasPrimed) {
        playPrimeTelegraph(player);
      }
    } else {
      trailCooldown.clear(id);
      if (wasPrimed) {
        playChargeExpiry(player);
      }
    }
    primedState.put(id, primed);
  }

  private record MovementSample(double horizontalDeltaSquared, long atMillis) {
  }

  private record ChargeMark(UUID attacker, long expiresAt) {
  }

  @ConfigDescription("Sprint into enemies with fists or a shield to deal impact damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Damage Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBase = 0.5;
    @ConfigDoc(value = "Controls Damage Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 4.2;
    @ConfigDoc(value = "Controls Knockback Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackBase = 0.5;
    @ConfigDoc(value = "Controls Knockback Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackFactor = 1.2;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 80;
    @ConfigDoc(value = "Controls Cooldown Ticks Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 50;
    @ConfigDoc(value = "Minimum squared horizontal movement per tick required for a charge to connect.", impact = "Higher values require faster movement before a charge triggers; sprint movement is roughly 0.08 per tick squared.")
    double minimumVelocitySquared = 0.05;
    @ConfigDoc(value = "Controls Xp Per Damage for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamage = 3.3;
    @ConfigDoc(value = "Milliseconds between primed trail particle pulses while charge is ready.", impact = "Lower values increase visual frequency and particle cost; higher values reduce trail spam.")
    long primedTrailIntervalMillis = 120;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
