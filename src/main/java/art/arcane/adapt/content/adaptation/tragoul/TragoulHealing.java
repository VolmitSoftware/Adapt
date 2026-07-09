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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;

public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
  private static final Color HEAL_CRIMSON = Color.fromRGB(150, 0, 10);
  private static final Color HEAL_PINK = Color.fromRGB(210, 40, 40);
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Boolean> healingWindow = playerState();

  public TragoulHealing() {
    super("tragoul-healing");
    registerConfiguration(TragoulHealing.Config.class);
    setIcon(Material.GLISTERING_MELON_SLICE);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_tragoul_healing_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RED_DYE)
            .key("challenge_tragoul_healing_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_healing_500", "tragoul.healing.health-stolen", 500, 400);
    registerMilestone("challenge_tragoul_healing_10k", "tragoul.healing.health-stolen", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.healing.lore1"));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.healing.lore2"));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.healing.lore3") + Form.pc(getHealPercent(level), 0));
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p) {
      withAdaptedPlayer(p, e, () -> {
        int level = getActiveLevel(p);
        if (level <= 0 || !canDamageTarget(p, e.getEntity())) {
          return;
        }

        if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDuration)) {
          return;
        }

        if (!healingWindow.containsKey(p.getUniqueId())) {
          startHealingWindow(p);
          playDrainTether(e.getEntity().getLocation(), p.getLocation());
          fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
              .dustBurst(HEAL_CRIMSON, 6, 0.3D, 1.0F)
              .sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4F, 1.3F);
        }

        double healAmount = e.getDamage() * getHealPercent(level);
        art.arcane.adapt.api.version.IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_MAX_HEALTH);
        p.setHealth(Math.min(attribute == null ? p.getHealth() : attribute.getValue(), p.getHealth() + healAmount));
        addStat(p, "tragoul.healing.health-stolen", (int) healAmount);
        fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRAIL).dustBurst(HEAL_PINK, 2, 0.15D, 0.8F);
      });
    }
  }

  private double getHealPercent(int level) {
    return getConfig().minHealPercent + (getConfig().maxHealPercent - getConfig().minHealPercent) * (level - 1) / (getConfig().maxLevel - 1);
  }

  private void startHealingWindow(Player p) {
    UUID id = p.getUniqueId();
    healingWindow.put(id, true);
    J.runEntity(p, () -> {
      healingWindow.remove(id);
      cooldowns.mark(id);
      fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION).burst(Particle.SOUL, 3, 0.2D);
    }, getConfig().windowDuration / 50);
  }

  private void playDrainTether(Location victimLoc, Location playerLoc) {
    Location from = victimLoc.clone().add(0, 1.0, 0);
    double dx = playerLoc.getX() - from.getX();
    double dy = (playerLoc.getY() + 1.0) - from.getY();
    double dz = playerLoc.getZ() - from.getZ();
    Particle.DustTransition trans = new Particle.DustTransition(HEAL_CRIMSON, HEAL_PINK, 1.0F);
    timeline(from)
        .duration(5)
        .priority(FxPriority.TRAIL)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.particle(Particle.DUST_COLOR_TRANSITION, 2, dx * progress, dy * progress, dz * progress, 0.05D, 0, trans);
          if (tick == 0) {
            fx.sound(Sound.PARTICLE_SOUL_ESCAPE, 0.35F, 1.5F);
          }
        })
        .start();
  }

  @Override
  public void onTick() {
  }

  @ConfigDescription("Regain health based on the damage you deal.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Heal Percent for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minHealPercent = 0.10; // 0.10%
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Heal Percent for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHealPercent = 0.45; // 0.45%
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Duration for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int cooldownDuration = 1000; // 1 second
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Duration for the Tragoul Healing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int windowDuration = 3000; // 3 seconds
  }
}
