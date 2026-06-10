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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosDejaVu extends SimpleAdaptation<ChronosDejaVu.Config> {
  private final Map<UUID, DamageMemory> memory;

  public ChronosDejaVu() {
    super("chronos-deja-vu");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("chronos.deja_vu.description"));
    setDisplayName(Localizer.dLocalize("chronos.deja_vu.name"));
    setIcon(Material.ITEM_FRAME);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(60000);
    memory = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ITEM_FRAME)
        .key("challenge_chronos_deja_vu_500")
        .title(Localizer.dLocalize("advancement.challenge_chronos_deja_vu_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_chronos_deja_vu_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_deja_vu_500", "chronos.deja-vu.damage-absorbed", 500, 700);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Math.round(getReductionFraction(level) * 100D) + "% " + Localizer.dLocalize("chronos.deja_vu.lore1"));
    v.addLore(C.YELLOW + "+ " + Form.duration(getConfig().memoryWindowMillis, 1) + " " + Localizer.dLocalize("chronos.deja_vu.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.deja_vu.lore3"));
  }

  private double getReductionFraction(int level) {
    return Math.min(getConfig().maxReductionFraction,
        getConfig().baseReductionFraction + (Math.max(1, level) * getConfig().reductionFractionPerLevel));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    memory.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    long now = System.currentTimeMillis();
    DamageMemory previous = memory.put(p.getUniqueId(), new DamageMemory(cause, now));
    if (previous == null || previous.cause() != cause || now - previous.timestamp() > getConfig().memoryWindowMillis) {
      return;
    }

    double fraction = getReductionFraction(level);
    if (fraction <= 0D) {
      return;
    }

    double absorbed = e.getFinalDamage() * fraction;
    if (absorbed <= 0D) {
      return;
    }

    e.setDamage(Math.max(0D, e.getDamage() * (1D - fraction)));

    getPlayer(p).getData().addStat("chronos.deja-vu.damage-absorbed", absorbed);
    xpSilent(p, absorbed * getConfig().xpPerAbsorbedDamage, "chronos:deja-vu");

    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0.02);
    }
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    long window = getConfig().memoryWindowMillis;
    memory.entrySet().removeIf(entry -> now - entry.getValue().timestamp() > window);
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
  @ConfigDescription("Remember the last source of pain; taking the same kind of damage again shortly after hurts less.")
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which a repeated damage cause counts as familiar.", impact = "Higher values keep the damage memory alive longer between hits.")
    long memoryWindowMillis = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of repeated damage that is absorbed.", impact = "Higher values reduce familiar damage more before level scaling.")
    double baseReductionFraction = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra absorbed fraction per adaptation level.", impact = "Higher values make leveling reduce familiar damage faster.")
    double reductionFractionPerLevel = 0.09;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the absorbed damage fraction.", impact = "Higher values allow more of a familiar hit to be absorbed.")
    double maxReductionFraction = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per point of absorbed damage.", impact = "Higher values grant more skill XP from familiar hits.")
    double xpPerAbsorbedDamage = 0.8;
  }

  private record DamageMemory(EntityDamageEvent.DamageCause cause, long timestamp) {
  }
}
