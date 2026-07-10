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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class UnarmedBatteringCharge extends SimpleAdaptation<UnarmedBatteringCharge.Config> {
  private static final Color TRAIL_COLOR = Color.fromRGB(0xCFCFCF);
  private final Map<UUID, Boolean> primedState = playerState();
  private final Cooldowns trailCooldown = cooldowns();

  public UnarmedBatteringCharge() {
    super("unarmed-battering-charge");
    registerConfiguration(Config.class);
    setIcon(Material.BLAZE_ROD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_charge_300")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_unarmed_charge_300", "unarmed.battering-charge.charges", 300, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND)
        .key("challenge_unarmed_charge_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_unarmed_charge_kills_100", "unarmed.battering-charge.charge-kills", 100, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDamageBonus(level)), 1);
    statLore(v, Form.f(getKnockback(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    if (p.isInsideVehicle()) {
      return;
    }

    if (!isChargeLoadout(p) || !p.isSprinting()) {
      return;
    }

    ItemStack cooldownItem = getCooldownItem(p);
    if (cooldownItem != null && p.hasCooldown(cooldownItem.getType())) {
      return;
    }

    Vector v = p.getVelocity();
    if (v.lengthSquared() < getConfig().minimumVelocitySquared) {
      return;
    }

    int level = attack.level();
    e.setDamage(e.getDamage() + getDamageBonus(level));
    Entity target = e.getEntity();
    target.setVelocity(target.getVelocity().add(p.getLocation().getDirection().normalize().multiply(getKnockback(level))));

    if (cooldownItem != null) {
      p.setCooldown(cooldownItem.getType(), getCooldownTicks(level));
    }

    playImpact(p, target, level);
    primedState.put(p.getUniqueId(), false);
    xp(p, e.getDamage() * getConfig().xpPerDamage);
    addStat(p, "unarmed.battering-charge.charges", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
        && dmg.getDamager() instanceof Player p
        && hasActiveAdaptation(p)
        && isChargeLoadout(p)) {
      addStat(p, "unarmed.battering-charge.charge-kills", 1);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    updatePrimedState(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      UUID id = e.getPlayer().getUniqueId();
      if (Boolean.TRUE.equals(primedState.remove(id))) {
        playChargeExpiry(e.getPlayer());
      }
      trailCooldown.clear(id);
      return;
    }
    updatePrimedState(e.getPlayer());
  }

  private boolean isChargeLoadout(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    boolean fists = (!isItem(main) || main.getType() == Material.AIR) && (!isItem(off) || off.getType() == Material.AIR);
    boolean shieldLoadout = (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
    return fists || shieldLoadout;
  }

  private ItemStack getCooldownItem(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    if (isItem(main) && main.getType() == Material.SHIELD) {
      return main;
    }

    if (isItem(off) && off.getType() == Material.SHIELD) {
      return off;
    }

    return null;
  }

  private boolean isChargeReady(Player p) {
    if (p.isInsideVehicle() || !isChargeLoadout(p) || !p.isSprinting()) {
      return false;
    }

    ItemStack cooldownItem = getCooldownItem(p);
    if (cooldownItem != null && p.hasCooldown(cooldownItem.getType())) {
      return false;
    }

    Vector v = p.getVelocity();
    return v.lengthSquared() >= getConfig().minimumVelocitySquared;
  }

  private void playImpact(Player p, Entity target, int level) {
    fx(p.getLocation(), FxPriority.COMBAT)
        .chord(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.95F, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5F, 0.8F);
    Location impact = target.getLocation().add(0, 0.7D, 0);
    fx(impact, FxPriority.COMBAT)
        .particle(Particle.EXPLOSION, 1, 0, 0, 0, 0.06D, 0.02D)
        .particle(Particle.CLOUD, 14, 0, 0, 0, 0.25D, 0.05D)
        .sound(Sound.ENTITY_ZOGLIN_ATTACK, 0.5F, 0.8F);
    double maxRadius = 1.5D + (level * 0.4D);
    timeline(target.getLocation())
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> fx.dustRing(0.2D + (maxRadius * progress), 20, 1.0F))
        .start();
  }

  private void playPrimeTelegraph(Player p) {
    timeline(p)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.CRIT, 1.4D - (0.9D * progress), 8, 0.1D);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.55F, 1.15F);
          } else if (tick == 5) {
            fx.sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4F, 1.4F);
          }
        })
        .start();
  }

  private void playChargeExpiry(Player p) {
    fx(p.getLocation().add(0, 0.2D, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 3, 0, 0, 0, 0.1D, 0.01D)
        .sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.3F, 0.85F);
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

  private void updatePrimedState(Player player) {
    UUID id = player.getUniqueId();
    if (!player.isSprinting() && !primedState.containsKey(id)) {
      return;
    }

    int level = getActiveLevel(player);
    boolean primed = level > 0 && isChargeReady(player);
    boolean wasPrimed = primedState.getOrDefault(id, false);
    if (primed) {
      long interval = Math.max(50L, getConfig().primedTrailIntervalMillis);
      if (trailCooldown.isReady(id, interval)) {
        trailCooldown.mark(id);
        fx(player.getLocation().add(0, 0.2D, 0), FxPriority.TRAIL).dustBurst(TRAIL_COLOR, 2, 0.2D, 0.8F);
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

  @ConfigDescription("Sprint into enemies with fists or a shield to deal impact damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 4.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Velocity Squared for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumVelocitySquared = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamage = 3.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between primed trail particle pulses while charge is ready.", impact = "Lower values increase visual frequency and particle cost; higher values reduce trail spam.")
    long primedTrailIntervalMillis = 120;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
