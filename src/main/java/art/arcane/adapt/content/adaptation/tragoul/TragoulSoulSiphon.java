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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TragoulSoulSiphon extends SimpleAdaptation<TragoulSoulSiphon.Config> {
  private final Map<UUID, HealBudget> budgets = new ConcurrentHashMap<>();

  public TragoulSoulSiphon() {
    super("tragoul-soul-siphon");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("tragoul.soul_siphon.description"));
    setDisplayName(Localizer.dLocalize("tragoul.soul_siphon.name"));
    setIcon(Material.SOUL_LANTERN);
    setInterval(25000);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SOUL_LANTERN)
        .key("challenge_tragoul_siphon_500")
        .title(Localizer.dLocalize("advancement.challenge_tragoul_siphon_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_tragoul_siphon_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_CAMPFIRE)
            .key("challenge_tragoul_siphon_10k")
            .title(Localizer.dLocalize("advancement.challenge_tragoul_siphon_10k.title"))
            .description(Localizer.dLocalize("advancement.challenge_tragoul_siphon_10k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_siphon_500", "tragoul.soul-siphon.health-siphoned", 500, 400);
    registerMilestone("challenge_tragoul_siphon_10k", "tragoul.soul-siphon.health-siphoned", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getHealPercent(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.soul_siphon.lore1"));
    v.addLore(C.YELLOW + "* " + Form.f(getHealCapPerSecond(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.soul_siphon.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    budgets.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p)) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
      return;
    }

    if (!(e.getEntity() instanceof LivingEntity)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !canDamageTarget(p, e.getEntity())) {
        return;
      }

      long now = System.currentTimeMillis();
      HealBudget budget = budgets.computeIfAbsent(p.getUniqueId(), k -> new HealBudget());
      if (now - budget.windowStart >= 1000L) {
        budget.windowStart = now;
        budget.healed = 0;
      }

      double remaining = getHealCapPerSecond(level) - budget.healed;
      if (remaining <= 0) {
        return;
      }

      double heal = Math.min(remaining, e.getFinalDamage() * getHealPercent(level));
      if (heal <= 0) {
        return;
      }

      IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_MAX_HEALTH);
      double maxHealth = attribute == null ? 20D : attribute.getValue();
      double newHealth = Math.min(maxHealth, p.getHealth() + heal);
      if (newHealth <= p.getHealth()) {
        return;
      }

      budget.healed += heal;
      p.setHealth(newHealth);
      if (areParticlesEnabled()) {
        J.runEntity(p, () -> p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1.2, 0), 5, 0.25, 0.3, 0.25, 0.01));
      }
      getPlayer(p).getData().addStat("tragoul.soul-siphon.health-siphoned", heal);
      xp(p, getConfig().xpPerHeal);
    });
  }

  private double getHealPercent(int level) {
    return Math.max(0, getConfig().healPercentBase + (getLevelPercent(level) * getConfig().healPercentFactor));
  }

  private double getHealCapPerSecond(int level) {
    return Math.max(0.5, getConfig().healCapPerSecondBase + (getLevelPercent(level) * getConfig().healCapPerSecondFactor));
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  private static class HealBudget {
    private long windowStart;
    private double healed;
  }

  @NoArgsConstructor
  @ConfigDescription("Melee hits heal you for a portion of the damage dealt, capped per second.")
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
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.72;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of melee damage returned as healing before level scaling.", impact = "Higher values increase baseline lifesteal.")
    double healPercentBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional lifesteal fraction granted at max level.", impact = "Higher values increase level-scaled lifesteal growth.")
    double healPercentFactor = 0.20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum health restored per second before level scaling.", impact = "Lower values harden the anti-abuse healing cap.")
    double healCapPerSecondBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional per-second healing cap granted at max level.", impact = "Higher values let high levels sustain more healing per second.")
    double healCapPerSecondFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per successful siphon heal.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerHeal = 3;
  }
}
