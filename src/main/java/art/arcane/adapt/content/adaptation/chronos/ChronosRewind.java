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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class ChronosRewind extends SimpleAdaptation<ChronosRewind.Config> {
  private final Map<UUID, RewindSnapshot> snapshots = playerState();
  private final Map<UUID, Long> cooldowns = playerState();
  private final Map<UUID, Boolean> cooldownReadyNotify = playerState();

  public ChronosRewind() {
    super("chronos-rewind");
    registerConfiguration(Config.class);
    setIcon(Material.ENDER_EYE);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_chronos_rewind_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_chronos_rewind_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_chronos_rewind_50", "chronos.rewind.rewinds", 50, 350);
    registerMilestone("challenge_chronos_rewind_500", "chronos.rewind.rewinds", 500, 1400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.duration(getConfig().snapshotWindowMillis, 1) + " " + Localizer.dLocalize("chronos.rewind.lore1"));
    v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(level), 1) + " " + Localizer.dLocalize("chronos.rewind.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.rewind.lore3"));
    if (getConfig().hungerCost > 0) {
      v.addLore(C.RED + "* " + getConfig().hungerCost + " " + Localizer.dLocalize("chronos.rewind.lore_cost_hunger"));
    }
  }

  private long getCooldownMillis(int level) {
    return Math.max(getConfig().minimumCooldownMillis,
        getConfig().baseCooldownMillis - (Math.max(1, level) * getConfig().cooldownReductionPerLevelMillis));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    snapshots.remove(id);
    cooldowns.remove(id);
    cooldownReadyNotify.remove(id);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    e.setCancelled(true);
    UUID id = p.getUniqueId();
    long now = M.ms();

    RewindSnapshot snapshot = snapshots.get(id);
    if (snapshot != null && snapshot.expiresAt() > now) {
      performRewind(p, context.level(), snapshot, now);
      return;
    }

    long cooldownUntil = cooldowns.getOrDefault(id, 0L);
    if (cooldownUntil > now) {
      reject(p);
      return;
    }

    snapshots.put(id, new RewindSnapshot(p.getLocation().clone(), p.getHealth(), p.getFoodLevel(),
        now + Math.max(1000L, getConfig().snapshotWindowMillis)));

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindStart(p);
    }

    fx(p.getLocation(), FxPriority.TRANSITION)
        .column(Particles.END_ROD, 4, 1.4D)
        .ring(Particle.REVERSE_PORTAL, 0.5D, 8, 0.6D)
        .sound(Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.5F, 1.6F);
  }

  private void reject(Player p) {
    if (getConfig().playClockSounds) {
      ChronosSoundFX.playClockReject(p);
    }
    fx(p.getLocation().add(0, 1.2, 0), FxPriority.TRANSITION).burst(Particles.SMOKE, 3, 0.15D);
  }

  private void performRewind(Player p, int level, RewindSnapshot snapshot, long now) {
    UUID id = p.getUniqueId();
    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      reject(p);
      return;
    }

    snapshots.remove(id);

    Location destination = snapshot.location();
    if (destination.getWorld() == null) {
      reject(p);
      return;
    }

    cooldowns.put(id, now + getCooldownMillis(level));
    cooldownReadyNotify.put(id, true);

    Location departure = p.getLocation().clone();
    Location departFx = departure.clone().add(0, 1, 0);
    fx(departFx, FxPriority.TRANSITION)
        .particle(Particle.REVERSE_PORTAL, 18, 0, 0, 0, 0.35D, 0.04D)
        .particle(Particle.PORTAL, 28, 0, 0, 0, 0.45D, 0.6D)
        .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 0.65F);

    if (departure.getWorld() != null && departure.getWorld().equals(destination.getWorld())) {
      fx(departFx, FxPriority.TRAIL)
          .line(Particle.PORTAL, destination.getX(), destination.getY() + 1, destination.getZ(), 10);
    }

    J.teleport(p, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);

    Runnable restore = () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }

      if (p.getHealth() < snapshot.health()) {
        AttributeInstance maxHealthAttribute = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? 20D : maxHealthAttribute.getValue();
        p.setHealth(Math.min(maxHealth, snapshot.health()));
      }

      if (p.getFoodLevel() < snapshot.foodLevel()) {
        p.setFoodLevel(Math.min(20, snapshot.foodLevel()));
      }

      if (hungerCost > 0) {
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
      }

      p.setFallDistance(0);
      p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 35, 0, true, false, false));

      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .particle(Particle.END_ROD, 10, 0, 0, 0, 0.3D, 0.02D)
          .particle(Particle.REVERSE_PORTAL, 22, 0, 0, 0, 0.4D, 0.05D)
          .particle(Particle.PORTAL, 28, 0, 0, 0, 0.45D, 0.6D)
          .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 0.85F);
    };

    if (J.isFoliaThreading()) {
      J.runEntity(p, restore, 1);
    } else {
      J.s(restore, 1);
    }

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindFinish(p);
    }

    addStat(p, "chronos.rewind.rewinds", 1);
    xp(p, destination, getConfig().xpOnRewind + (level * getConfig().xpPerLevel));
  }

  @Override
  public void onTick() {
    long now = M.ms();
    snapshots.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);

    for (UUID id : cooldownReadyNotify.keySet()) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        cooldownReadyNotify.remove(id);
        continue;
      }

      if (cooldowns.getOrDefault(id, 0L) <= now && cooldownReadyNotify.remove(id) != null) {
        J.runEntity(p, () -> {
          if (p.isOnline() && getConfig().playClockSounds) {
            ChronosSoundFX.playCooldownReady(p);
          }
        });
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  @ConfigDescription("Sneak and swap hands to mark a moment in time, then do it again within the window to snap back with health and hunger restored.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Rewind adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Food points consumed when a rewind completes.", impact = "Higher values make each rewind cost more hunger; 0 disables the cost.")
    int hungerCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds after marking a snapshot during which the rewind can be completed.", impact = "Higher values give more time to trigger the snap back.")
    long snapshotWindowMillis = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds applied after a completed rewind.", impact = "Higher values force longer waits between rewinds.")
    long baseCooldownMillis = 45000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 15000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted when a rewind completes.", impact = "Higher values grant more skill XP per rewind.")
    double xpOnRewind = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra XP granted per adaptation level on rewind.", impact = "Higher values scale rewind XP with level faster.")
    double xpPerLevel = 3;

    public Config() {
      baseCost = 6;
      costFactor = 0.4;
      initialCost = 5;
    }
  }

  private record RewindSnapshot(Location location, double health,
                                int foodLevel, long expiresAt) {
  }
}
