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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosRewind extends SimpleAdaptation<ChronosRewind.Config> {
  private final Map<UUID, RewindSnapshot> snapshots;
  private final Map<UUID, Long> cooldowns;
  private final Set<UUID> cooldownReadyNotify;

  public ChronosRewind() {
    super("chronos-rewind");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("chronos.rewind.description"));
    setDisplayName(Localizer.dLocalize("chronos.rewind.name"));
    setIcon(Material.ENDER_EYE);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(1000);
    snapshots = new ConcurrentHashMap<>();
    cooldowns = new ConcurrentHashMap<>();
    cooldownReadyNotify = ConcurrentHashMap.newKeySet();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_chronos_rewind_50")
        .title(Localizer.dLocalize("advancement.challenge_chronos_rewind_50.title"))
        .description(Localizer.dLocalize("advancement.challenge_chronos_rewind_50.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_chronos_rewind_500")
            .title(Localizer.dLocalize("advancement.challenge_chronos_rewind_500.title"))
            .description(Localizer.dLocalize("advancement.challenge_chronos_rewind_500.description"))
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
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    snapshots.put(id, new RewindSnapshot(p.getLocation().clone(), p.getHealth(), p.getFoodLevel(),
        now + Math.max(1000L, getConfig().snapshotWindowMillis)));

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindStart(p);
    }

    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.02);
    }
  }

  private void performRewind(Player p, int level, RewindSnapshot snapshot, long now) {
    UUID id = p.getUniqueId();
    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    snapshots.remove(id);

    Location destination = snapshot.location();
    if (destination.getWorld() == null) {
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    cooldowns.put(id, now + getCooldownMillis(level));
    cooldownReadyNotify.add(id);

    Location departure = p.getLocation().clone();
    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, departure.clone().add(0, 1, 0), 18, 0.3, 0.5, 0.3, 0.04);
      p.getWorld().spawnParticle(Particle.PORTAL, departure.clone().add(0, 1, 0), 28, 0.4, 0.6, 0.4, 0.6);
    }

    SoundPlayer.of(p.getWorld()).play(departure, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.65f);

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

      if (areParticlesEnabled()) {
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 10, 0.25, 0.4, 0.25, 0.02);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 22, 0.35, 0.55, 0.35, 0.05);
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 28, 0.4, 0.6, 0.4, 0.6);
      }

      SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.85f);
    };

    if (J.isFoliaThreading()) {
      J.runEntity(p, restore, 1);
    } else {
      J.s(restore, 1);
    }

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindFinish(p);
    }

    getPlayer(p).getData().addStat("chronos.rewind.rewinds", 1);
    xp(p, destination, getConfig().xpOnRewind + (level * getConfig().xpPerLevel));
  }

  @Override
  public void onTick() {
    long now = M.ms();
    snapshots.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);

    for (Iterator<UUID> iterator = cooldownReadyNotify.iterator(); iterator.hasNext(); ) {
      UUID id = iterator.next();
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        iterator.remove();
        continue;
      }

      if (cooldowns.getOrDefault(id, 0L) <= now) {
        if (getConfig().playClockSounds) {
          ChronosSoundFX.playCooldownReady(p);
        }
        iterator.remove();
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak and swap hands to mark a moment in time, then do it again within the window to snap back with health and hunger restored.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Rewind adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
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
  }

  private record RewindSnapshot(Location location, double health,
                                int foodLevel, long expiresAt) {
  }
}
