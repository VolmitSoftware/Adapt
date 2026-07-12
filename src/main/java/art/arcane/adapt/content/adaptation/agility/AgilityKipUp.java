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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilityKipUp extends SimpleAdaptation<AgilityKipUp.Config> {
  private final Cooldowns knockbackAt = cooldowns();
  private final Cooldowns kipCooldown = cooldowns();
  private final Map<UUID, Boolean> wasOnGround = playerState();

  public AgilityKipUp() {
    super("agility-kip-up");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_agility_kip_up_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SCRAP)
            .key("challenge_agility_kip_up_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_kip_up_100", "agility.kip-up.recoveries", 100, 300);
    registerMilestone("challenge_agility_kip_up_1k", "agility.kip-up.recoveries", 1000, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getRecoveryWindowMillis(level), 1), 1);
    statLore(v, C.YELLOW, "* ", getSpeedAmplifier(level) + 1, 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getDamage() <= 0) {
      return;
    }

    if (hasActiveAdaptation(p)) {
      knockbackAt.mark(p.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    boolean groundNow = p.isOnGround();
    boolean groundBefore = wasOnGround.getOrDefault(id, groundNow);
    wasOnGround.put(id, groundNow);

    if (!groundBefore || groundNow) {
      return;
    }

    if (p.getVelocity().getY() <= getConfig().jumpVelocityThreshold) {
      return;
    }

    withPlayerThread(p, e, () -> attemptKip(p, e));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    wasOnGround.remove(id);
    knockbackAt.clear(id);
  }

  private void attemptKip(Player p, PlayerMoveEvent e) {
    if (!isKipEligible(p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (knockbackAt.remaining(id, getRecoveryWindowMillis(level)) <= 0) {
      return;
    }

    if (!kipCooldown.isReady(id, Math.max(0L, getConfig().cooldownMillis))) {
      return;
    }

    Vector direction = intendedDirection(p, e);
    if (direction == null) {
      return;
    }

    kipCooldown.mark(id);
    knockbackAt.clear(id);

    Vector current = p.getVelocity();
    double magnitude = getConfig().recoverySpeed;
    p.setVelocity(new Vector(direction.getX() * magnitude, current.getY(), direction.getZ() * magnitude));

    int amplifier = getSpeedAmplifier(level);
    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, getSpeedDurationTicks(), amplifier, false, false));

    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .trail(Particle.CLOUD, direction.getX(), 0.1D, direction.getZ(), 0.8D, 5)
        .ring(Particle.CRIT, 0.7D, 8, 0.1D)
        .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6F, 1.3F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6F, 1.0F);

    addStat(p, "agility.kip-up.recoveries", 1);
    xp(p, getConfig().xpPerRecovery);
  }

  private Vector intendedDirection(Player p, PlayerMoveEvent e) {
    Vector move = e.getTo() == null ? null : e.getTo().toVector().subtract(e.getFrom().toVector());
    if (move != null) {
      move.setY(0);
      if (move.lengthSquared() > 1.0E-4D) {
        return move.normalize();
      }
    }

    Vector look = p.getLocation().getDirection().clone().setY(0);
    if (look.lengthSquared() > 1.0E-4D) {
      return look.normalize();
    }

    return null;
  }

  private boolean isKipEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.getVehicle() == null;
  }

  private long getRecoveryWindowMillis(int level) {
    return Math.max(120L, Math.round(getConfig().recoveryWindowMillisBase
        + (getLevelPercent(level) * getConfig().recoveryWindowMillisFactor)));
  }

  private int getSpeedAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().speedAmplifierBase
        + (getLevelPercent(level) * getConfig().speedAmplifierFactor)));
  }

  private int getSpeedDurationTicks() {
    return Math.max(10, getConfig().speedDurationTicks);
  }

  @ConfigDescription("Jump right after taking a hit to convert knockback into recovered momentum and a speed burst.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base recovery window in milliseconds after a hit before level scaling.", impact = "Higher values give more time to kip-up after taking knockback.")
    double recoveryWindowMillisBase = 350;
    @ConfigDoc(value = "Additional recovery window in milliseconds granted at max level.", impact = "Higher values widen the timing window from leveling.")
    double recoveryWindowMillisFactor = 550;
    @ConfigDoc(value = "Base speed-effect amplifier granted on a recovery before level scaling.", impact = "Higher values start the recovery speed burst stronger.")
    double speedAmplifierBase = 0;
    @ConfigDoc(value = "Additional speed-effect amplifier granted at max level.", impact = "Higher values widen the speed-burst gain from leveling.")
    double speedAmplifierFactor = 1.6;
    @ConfigDoc(value = "Duration in ticks of the recovery speed burst.", impact = "Higher values keep the post-recovery speed longer.")
    int speedDurationTicks = 40;
    @ConfigDoc(value = "Horizontal velocity applied toward the intended direction on recovery.", impact = "Higher values fling the player harder out of the knockback.")
    double recoverySpeed = 0.5;
    @ConfigDoc(value = "Minimum upward velocity treated as a jump for triggering a kip-up.", impact = "Higher values require a stronger jump; lower values are more sensitive.")
    double jumpVelocityThreshold = 0.2;
    @ConfigDoc(value = "Cooldown between kip-up recoveries in milliseconds.", impact = "Higher values prevent chaining recoveries too quickly.")
    long cooldownMillis = 3000;
    @ConfigDoc(value = "Experience granted per successful recovery.", impact = "Higher values reward clutch recoveries with more skill xp.")
    double xpPerRecovery = 5;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
