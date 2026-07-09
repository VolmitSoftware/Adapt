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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

import static org.bukkit.Particle.HEART;

public class TamingHealthRegeneration extends SimpleAdaptation<TamingHealthRegeneration.Config> {
  private final Map<UUID, Long> lastDamage = new java.util.concurrent.ConcurrentHashMap<>();

  public TamingHealthRegeneration() {
    super("tame-health-regeneration");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.regeneration");
    setIcon(Material.GOLDEN_APPLE);
    setInterval(1033);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLISTERING_MELON_SLICE)
        .key("challenge_taming_regen_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_regen_1k", "taming.health-regen.health-regened", 1000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRegenSpeed(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.regeneration.lore1"));
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    if (e.getEntity() instanceof Tameable tam
        && tam.getOwner() instanceof Player p) {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (lastDamage.containsKey(tam.getUniqueId())) {
        return;
      }
      art.arcane.adapt.api.version.IAttribute attribute = Version.get().getAttribute(tam, Attributes.GENERIC_MAX_HEALTH);
      double mh = attribute == null ? tam.getHealth() : attribute.getValue();
      if (tam.isTamed() && tam.getOwner() instanceof Player && tam.getHealth() < mh) {
        tam.addPotionEffect(PotionEffectType.REGENERATION.createEffect(25 * level, 3));
        addStat(p, "taming.health-regen.health-regened", 1);
        timeline(tam)
            .duration(5)
            .priority(FxPriority.TRANSITION)
            .cullRadius(24)
            .frame((fx, tick, progress) -> {
              fx.particle(HEART, 1, 0, 0.4D + (progress * 1.0D), 0, 0.12D, 0.01D);
              if (tick == 0) {
                fx.particle(Particles.VILLAGER_HAPPY, 3, 0, 0.1D, 0, 0.25D, 0)
                    .chord(Sound.ENTITY_WOLF_PANT, 0.5F, 1.3F, Sound.BLOCK_NOTE_BLOCK_BELL, 0.3F, 1.8F);
              }
            })
            .start();
      }
      lastDamage.put(e.getEntity().getUniqueId(), M.ms());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDeathEvent e) {
    lastDamage.remove(e.getEntity().getUniqueId());
  }


  private double getRegenSpeed(int level) {
    return ((getLevelPercent(level) * (getLevelPercent(level)) * getConfig().regenFactor) + getConfig().regenBase);
  }

  @Override
  public void onTick() {
    lastDamage.entrySet().removeIf(i -> M.ms() - i.getValue() > 8000);
  }

  @ConfigDescription("Increase your tamed animal regeneration rate.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Factor for the Taming Health Regeneration adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Base for the Taming Health Regeneration adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenBase = 1;

    public Config() {
      baseCost = 7;
      costFactor = 0.4;
      maxLevel = 3;
      initialCost = 8;
    }
  }
}
