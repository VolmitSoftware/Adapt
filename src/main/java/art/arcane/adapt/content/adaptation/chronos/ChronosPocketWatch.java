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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosPocketWatch extends SimpleAdaptation<ChronosPocketWatch.Config> {
  private static final long PULSE_MILLIS = 250L;

  private final Map<UUID, Long> airBudgetMillis;

  public ChronosPocketWatch() {
    super("chronos-pocket-watch");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("chronos.pocket_watch.description"));
    setDisplayName(Localizer.dLocalize("chronos.pocket_watch.name"));
    setIcon(Material.FEATHER);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(PULSE_MILLIS);
    airBudgetMillis = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FEATHER)
        .key("challenge_chronos_pocket_watch_500")
        .title(Localizer.dLocalize("advancement.challenge_chronos_pocket_watch_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_chronos_pocket_watch_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_pocket_watch_500", "chronos.pocket-watch.slow-fall-seconds", 500, 650);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + (getBudgetMillis(level) / 1000D) + "s " + Localizer.dLocalize("chronos.pocket_watch.lore1"));
    v.addLore(C.YELLOW + "* " + Localizer.dLocalize("chronos.pocket_watch.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.pocket_watch.lore3"));
  }

  private long getBudgetMillis(int level) {
    return (long) ((getConfig().baseBudgetSeconds + (Math.max(1, level) * getConfig().budgetSecondsPerLevel)) * 1000D);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    airBudgetMillis.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      UUID id = p.getUniqueId();
      if (p.isOnGround()) {
        airBudgetMillis.remove(id);
        continue;
      }

      int level = getActiveLevel(p, Player::isSneaking);
      if (level <= 0) {
        continue;
      }

      if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isSwimming()) {
        continue;
      }

      if (!airBudgetMillis.containsKey(id) && p.getFallDistance() < getConfig().minFallDistance) {
        continue;
      }

      if (getConfig().requireClock && !p.getInventory().contains(Material.CLOCK)) {
        continue;
      }

      long budget = airBudgetMillis.computeIfAbsent(id, k -> getBudgetMillis(level));
      if (budget < PULSE_MILLIS) {
        continue;
      }

      airBudgetMillis.put(id, budget - PULSE_MILLIS);

      Runnable apply = () -> {
        if (!p.isOnline() || p.isDead()) {
          return;
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
            getConfig().pulseDurationTicks, 0, true, false, false), true);

        if (areParticlesEnabled()) {
          p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 1, 0.1, 0, 0.1, 0);
        }
      };

      if (J.isFoliaThreading()) {
        J.runEntity(p, apply);
      } else {
        apply.run();
      }

      getPlayer(p).getData().addStat("chronos.pocket-watch.slow-fall-seconds", PULSE_MILLIS / 1000D);
      xpSilent(p, getConfig().xpPerPulse, "chronos:pocket-watch");
    }
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
  @ConfigDescription("Sneak while airborne to fall in slow motion, with a level scaled time budget per airtime.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base seconds of slow falling sustainable per airtime.", impact = "Higher values let the player drift longer each fall.")
    double baseBudgetSeconds = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra sustain seconds per adaptation level.", impact = "Higher values make leveling extend the drift budget faster.")
    double budgetSecondsPerLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshing slow falling pulse.", impact = "Higher values leave slow falling lingering longer after sneaking stops.")
    int pulseDurationTicks = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum fall distance in blocks before the slow fall kicks in.", impact = "Lower values start the slow fall earlier in the drop.")
    double minFallDistance = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires a clock anywhere in the inventory for the slow fall to apply.", impact = "False removes the item requirement entirely.")
    boolean requireClock = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per slow fall pulse.", impact = "Higher values grant more skill XP while drifting.")
    double xpPerPulse = 0.3;
  }
}
