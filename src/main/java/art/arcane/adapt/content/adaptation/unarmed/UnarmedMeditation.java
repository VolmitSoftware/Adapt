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
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;

public class UnarmedMeditation extends SimpleAdaptation<UnarmedMeditation.Config> {
  private final Cooldowns combatCooldown = cooldowns();
  private final Map<UUID, Location> lastPositions = playerState();
  private final Map<UUID, Boolean> medState = playerState();

  public UnarmedMeditation() {
    super("unarmed-meditation");
    registerConfiguration(Config.class);
    setIcon(Material.AMETHYST_CLUSTER);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_meditate_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_meditate_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_meditate_500", "unarmed.meditation.absorption-gained", 500, 400);
    registerMilestone("challenge_unarmed_meditate_5k", "unarmed.meditation.absorption-gained", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getAbsorptionCap(level)), 1);
    statLore(v, Form.f(getConfig().gainPerPulse), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getConfig().combatLockoutMillis, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player attacker) {
      combatCooldown.mark(attacker.getUniqueId());
    }
    if (e.getEntity() instanceof Player victim) {
      combatCooldown.mark(victim.getUniqueId());
    }
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
      if (!p.isSneaking() || isItem(p.getInventory().getItemInMainHand()) || isItem(p.getInventory().getItemInOffHand())) {
        lastPositions.remove(id);
        breakMeditation(p, id);
        continue;
      }

      if (!combatCooldown.isReady(id, getConfig().combatLockoutMillis)) {
        breakMeditation(p, id);
        continue;
      }

      Location current = p.getLocation();
      Location previous = lastPositions.put(id, current);
      if (previous == null || previous.getWorld() != current.getWorld() || previous.distanceSquared(current) > getConfig().stationaryEpsilonSquared) {
        breakMeditation(p, id);
        continue;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        breakMeditation(p, id);
        continue;
      }

      enterMeditation(p, id);
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

      double fill = cap <= 0 ? 1.0D : Math.min(1.0D, (absorption + gained) / cap);
      fx(current.clone().add(0, 1.1D, 0), FxPriority.AMBIENT)
          .column(Particles.END_ROD, 2, 1.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0, 0, 0.4D, 0.04D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, (float) (1.4D + (fill * 0.3D)));
      xpSilent(p, getConfig().xpPerPulse, "meditation");
      adaptPlayer.getData().addStat("unarmed.meditation.absorption-gained", gained);
    }
  }

  private void enterMeditation(Player p, UUID id) {
    if (Boolean.TRUE.equals(medState.put(id, Boolean.TRUE))) {
      return;
    }

    fx(p.getLocation().add(0, 0.1D, 0), FxPriority.TRANSITION)
        .ring(Particles.END_ROD, 0.6D, 6, 0.1D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4F, 1.2F);
  }

  private void breakMeditation(Player p, UUID id) {
    if (!Boolean.TRUE.equals(medState.remove(id))) {
      return;
    }

    fx(p.getLocation().add(0, 0.2D, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.3F, 0.8F);
  }

  private double getAbsorptionCap(int level) {
    return getConfig().absorptionCapBase + (getLevelPercent(level) * getConfig().absorptionCapFactor);
  }

  @ConfigDescription("Meditate while sneaking, still, and empty-handed to slowly build absorption hearts.")
  protected static class Config extends AdaptationConfig {
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

    public Config() {
      costFactor = 0.55;
      initialCost = 5;
    }
  }
}
