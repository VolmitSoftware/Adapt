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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Fish;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SeaborneFishWhisperer extends SimpleAdaptation<SeaborneFishWhisperer.Config> {
  private static final String LUCK_SLOT = "luck";
  private static final long LUCK_DURATION_TICKS = 400L;
  private static final int MAX_FISH = 12;
  private static final int MAX_ASSIST = 8;
  private static final long CHARM_SESSION_GAP_MILLIS = 12_000L;

  private final Map<UUID, Long> charmedRecently = new ConcurrentHashMap<>();

  public SeaborneFishWhisperer() {
    super("seaborne-fish-whisperer");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.fish_whisperer");
    setIcon(Material.TROPICAL_FISH);
    setInterval(4000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TROPICAL_FISH)
        .key("challenge_seaborne_charm_2k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.COD)
            .key("challenge_seaborne_charm_20k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_charm_2k", "seaborne.fish-whisperer.charmed", 2000, 300);
    registerMilestone("challenge_seaborne_charm_20k", "seaborne.fish-whisperer.charmed", 20000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, luckAmount(level, getMaxLevel()), 1);
    statLore(v, Form.f(getSchoolRange(level), 1), 2);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    charmedRecently.values().removeIf(at -> now - at >= CHARM_SESSION_GAP_MILLIS);
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        applyLuck(player, level);
        if (player.isInWater() || player.isSwimming()) {
          schoolFish(player, level);
        }
      });
    }
  }

  private void applyLuck(Player player, int level) {
    AdaptAttributeService.get().applyTimed(player, getName(), LUCK_SLOT, Attributes.LUCK, luckAmount(level, getMaxLevel()), AttributeModifier.Operation.ADD_NUMBER, LUCK_DURATION_TICKS);
  }

  private void schoolFish(Player player, int level) {
    double range = getSchoolRange(level);
    Location center = player.getLocation();
    Location target = center.clone().add(0D, 0.4D, 0D);
    int scheduled = 0;
    for (Fish fish : player.getWorld().getNearbyEntitiesByType(Fish.class, center, range, range, range)) {
      if (J.runEntity(fish, () -> nudgeAndCreditFish(player, fish, target, level))) {
        scheduled++;
      }

      if (scheduled >= MAX_FISH) {
        break;
      }
    }
  }

  private void nudgeAndCreditFish(Player player, Fish fish, Location target, int level) {
    if (!nudgeFish(fish, target, level)) {
      return;
    }
    UUID fishId = fish.getUniqueId();
    long now = System.currentTimeMillis();
    Long lastCharm = charmedRecently.put(fishId, now);
    if (!startsNewCharmSession(lastCharm, now)) {
      return;
    }
    if (!J.runEntity(player, () -> creditCharmedFish(player))) {
      charmedRecently.remove(fishId, now);
    }
  }

  private void creditCharmedFish(Player player) {
    if (!player.isOnline() || !isRuntimeRegistered() || getActiveLevel(player) <= 0) {
      return;
    }
    addStat(player, "seaborne.fish-whisperer.charmed", 1);
  }

  private boolean nudgeFish(Fish fish, Location target, int level) {
    if (!fish.isValid() || fish.isDead() || target.getWorld() != fish.getWorld()) {
      return false;
    }

    Vector toTarget = target.toVector().subtract(fish.getLocation().toVector());
    double distance = toTarget.length();
    if (distance < 1.0D) {
      return false;
    }

    Vector pull = toTarget.normalize().multiply(getSchoolPull(level));
    Vector current = fish.getVelocity().clone();
    Vector updated = current.clone().multiply(0.5D).add(pull);
    if (!hasVelocityChange(current, updated)) {
      return false;
    }
    fish.setVelocity(updated);
    fx(fish.getLocation(), FxPriority.AMBIENT)
        .particle(Particle.BUBBLE, 2, 0D, 0D, 0D, 0.1D, 0.02D);
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim) || !(victim instanceof Mob)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double range = getAssistRange(level);
    Location victimLocation = victim.getLocation().clone();
    int assisted = 0;
    for (Mob entity : p.getWorld().getNearbyEntitiesByType(
        Mob.class, victimLocation, range, range, range)) {
      if (entity == victim) {
        continue;
      }

      if (entity instanceof Dolphin dolphin) {
        J.runEntity(dolphin, () -> chargeDolphin(dolphin, victimLocation));
      } else if (entity instanceof Axolotl axolotl) {
        J.runEntity(axolotl, () -> {
          if (axolotl.getTarget() != victim) {
            axolotl.setTarget(victim);
          }
        });
      } else {
        continue;
      }

      if (++assisted >= MAX_ASSIST) {
        break;
      }
    }
  }

  private void chargeDolphin(Dolphin dolphin, Location victimLocation) {
    if (!dolphin.isValid() || dolphin.getWorld() != victimLocation.getWorld()) {
      return;
    }

    Vector toVictim = victimLocation.toVector().subtract(dolphin.getLocation().toVector());
    if (toVictim.lengthSquared() <= 0.01D) {
      return;
    }

    double strength = Math.max(0D, getConfig().dolphinChargeStrength);
    dolphin.setVelocity(dolphin.getVelocity().clone().multiply(0.4D).add(toVictim.normalize().multiply(strength)));
    fx(dolphin.getLocation(), FxPriority.AMBIENT)
        .particle(Particle.BUBBLE, 4, 0D, 0.1D, 0D, 0.2D, 0.03D);
  }

  private double getSchoolRange(int level) {
    return schoolRange(getConfig().schoolRangeBase, getConfig().schoolRangeFactor, getLevelPercent(level));
  }

  private double getSchoolPull(int level) {
    return getConfig().schoolPullBase + (getLevelPercent(level) * getConfig().schoolPullFactor);
  }

  private double getAssistRange(int level) {
    return getConfig().assistRangeBase + (getLevelPercent(level) * getConfig().assistRangeFactor);
  }

  static int luckAmplifier(int level, int maxLevel) {
    return Math.max(0, Math.min(maxLevel - 1, level - 1));
  }

  static int luckAmount(int level, int maxLevel) {
    return luckAmplifier(level, maxLevel) + 1;
  }

  static double schoolRange(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  static boolean startsNewCharmSession(Long lastCharm, long now) {
    return lastCharm == null || now - lastCharm >= CHARM_SESSION_GAP_MILLIS;
  }

  static boolean hasVelocityChange(Vector current, Vector updated) {
    if (current == null || updated == null) {
      return false;
    }
    double differenceSquared = updated.clone().subtract(current).lengthSquared();
    return Double.isFinite(differenceSquared) && differenceSquared > 1.0E-8D;
  }

  @ConfigDescription("Fish school toward you, dolphins and axolotls assist your hunts, and you fish with a permanent Luck of the Sea tier.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base block range fish school toward you within.", impact = "Higher values gather fish from farther away at low levels.")
    double schoolRangeBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional fish schooling range gained across levels.", impact = "Higher values gather fish from much farther at higher levels.")
    double schoolRangeFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base velocity pull applied to schooling fish.", impact = "Higher values pull fish toward you more strongly at low levels.")
    double schoolPullBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional schooling pull gained across levels.", impact = "Higher values pull fish more strongly at higher levels.")
    double schoolPullFactor = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base block range dolphins and axolotls assist within.", impact = "Higher values recruit sea creatures from farther away at low levels.")
    double assistRangeBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional assist recruitment range gained across levels.", impact = "Higher values recruit sea creatures from much farther at higher levels.")
    double assistRangeFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Velocity strength dolphins use to charge your combat target.", impact = "Higher values ram dolphins into your target harder; 0 disables the charge motion.")
    double dolphinChargeStrength = 0.9;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
