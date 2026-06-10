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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class UnarmedMeditation extends SimpleAdaptation<UnarmedMeditation.Config> {
  private final Map<UUID, Long> lastCombatMillis = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<UUID, Location> lastPositions = new java.util.concurrent.ConcurrentHashMap<>();

  public UnarmedMeditation() {
    super("unarmed-meditation");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("unarmed.meditation.description"));
    setDisplayName(Localizer.dLocalize("unarmed.meditation.name"));
    setIcon(Material.AMETHYST_CLUSTER);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_meditate_500")
        .title(Localizer.dLocalize("advancement.challenge_unarmed_meditate_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_unarmed_meditate_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_meditate_5k")
            .title(Localizer.dLocalize("advancement.challenge_unarmed_meditate_5k.title"))
            .description(Localizer.dLocalize("advancement.challenge_unarmed_meditate_5k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_meditate_500", "unarmed.meditation.absorption-gained", 500, 400);
    registerMilestone("challenge_unarmed_meditate_5k", "unarmed.meditation.absorption-gained", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getAbsorptionCap(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.meditation.lore1"));
    v.addLore(C.GREEN + "+ " + Form.f(getConfig().gainPerPulse) + C.GRAY + " " + Localizer.dLocalize("unarmed.meditation.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration((double) getConfig().combatLockoutMillis, 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.meditation.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    long now = System.currentTimeMillis();
    if (e.getDamager() instanceof Player attacker) {
      lastCombatMillis.put(attacker.getUniqueId(), now);
    }
    if (e.getEntity() instanceof Player victim) {
      lastCombatMillis.put(victim.getUniqueId(), now);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    lastCombatMillis.remove(id);
    lastPositions.remove(id);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      UUID id = p.getUniqueId();
      if (!p.isSneaking()) {
        lastPositions.remove(id);
        continue;
      }

      if (isItem(p.getInventory().getItemInMainHand()) || isItem(p.getInventory().getItemInOffHand())) {
        lastPositions.remove(id);
        continue;
      }

      Long combat = lastCombatMillis.get(id);
      if (combat != null && now - combat < getConfig().combatLockoutMillis) {
        continue;
      }

      Location current = p.getLocation();
      Location previous = lastPositions.put(id, current);
      if (previous == null || previous.getWorld() != current.getWorld() || previous.distanceSquared(current) > getConfig().stationaryEpsilonSquared) {
        continue;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        continue;
      }

      double cap = getAbsorptionCap(level);
      double absorption = p.getAbsorptionAmount();
      if (absorption >= cap) {
        continue;
      }

      double gained = Math.min(cap - absorption, getConfig().gainPerPulse);
      J.runEntity(p, () -> {
        if (p.isOnline() && !p.isDead()) {
          p.setAbsorptionAmount(Math.min(cap, p.getAbsorptionAmount() + gained));
        }
      });

      SoundPlayer.of(p.getWorld()).play(current, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.5f);
      if (areParticlesEnabled()) {
        p.getWorld().spawnParticle(Particles.ENCHANTMENT_TABLE, current.clone().add(0, 1.4, 0), 8, 0.3, 0.4, 0.3, 0.04);
      }
      xpSilent(p, getConfig().xpPerPulse, "meditation");
      adaptPlayer.getData().addStat("unarmed.meditation.absorption-gained", gained);
    }
  }

  private double getAbsorptionCap(int level) {
    return getConfig().absorptionCapBase + (getLevelPercent(level) * getConfig().absorptionCapFactor);
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
  @ConfigDescription("Meditate while sneaking, still, and empty-handed to slowly build absorption hearts.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base absorption cap in health points at level 1.", impact = "Higher values allow more stored absorption early.")
    double absorptionCapBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorption cap granted at max level.", impact = "Higher values allow more stored absorption as levels increase.")
    double absorptionCapFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Absorption health points gained per meditation pulse.", impact = "Higher values build absorption faster.")
    double gainPerPulse = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds after combat before meditation can resume.", impact = "Higher values force a longer calm period after fighting.")
    long combatLockoutMillis = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum squared movement distance still considered stationary.", impact = "Higher values tolerate more drift while meditating.")
    double stationaryEpsilonSquared = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Silent XP granted per meditation pulse.", impact = "Higher values speed up unarmed skill progression from meditating.")
    double xpPerPulse = 1.2;
  }
}
