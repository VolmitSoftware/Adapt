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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DiscoveryVillagerAtt extends SimpleAdaptation<DiscoveryVillagerAtt.Config> {
  private final Map<UUID, Integer> active = playerState();

  public DiscoveryVillagerAtt() {
    super("discovery-villager-att");
    registerConfiguration(Config.class);
    setLocalizationKey("discovery.villager");
    setIcon(Material.GLASS_BOTTLE);
    setInterval(2432);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_discovery_villager_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.EMERALD_BLOCK)
            .key("challenge_discovery_villager_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_villager_100", "discovery.villager-att.improved-trades", 100, 300);
    registerMilestone("challenge_discovery_villager_2500", "discovery.villager-att.improved-trades", 2500, 1000);
  }


  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("discovery.villager.lore1"));
    statLore(v, Form.pc(getEffectiveness(getLevelPercent(level)), 0), 2);
    v.addLore(C.GREEN + "+ " + getXpTaken(level) + " " + C.GRAY + Localizer.dLocalize("discovery.villager.lore3"));
  }

  private double getEffectiveness(double multiplier) {
    return Math.min(getConfig().maxEffectiveness, multiplier * multiplier + getConfig().effectivenessBase);
  }

  private int getXpTaken(double level) {
    double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
    return (int) d;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (e.getRightClicked() instanceof Villager v && level > 0) {
      if (ThreadLocalRandom.current().nextDouble() <= getEffectiveness(getLevelPercent(level))) {
        if (p.getLevel() - getXpTaken(level) > 0) {
          p.setLevel((p.getLevel() - getXpTaken(level)));
          active.put(p.getUniqueId(), level);
          p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, level, true, true));
          addStat(p, "discovery.villager-att.improved-trades", 1);

          timeline(v)
              .duration(8)
              .priority(FxPriority.TRANSITION)
              .cullRadius(24)
              .frame((f, tick, progress) -> {
                f.helix(Particles.VILLAGER_HAPPY, 0.6D, 1.8D, 6, progress * Math.PI * 2.0D);
                if ((tick & 3) == 0) {
                  f.particle(Particle.WAX_ON, 2, 0, 1.0, 0, 0.25, 0.01);
                }
                if (tick == 0) {
                  f.chord(Sound.ENTITY_VILLAGER_CELEBRATE, 1F, 1F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.3F);
                }
              })
              .onComplete(() -> fx(p.getLocation(), FxPriority.TRANSITION)
                  .dustRing(Color.fromRGB(80, 200, 120), 1.0D, 12, 1.0F)
                  .dome(Particles.VILLAGER_HAPPY, 1.5D, 24)
                  .chord(Sound.BLOCK_BEACON_POWER_SELECT, 0.3F, 1.4F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F))
              .start();
        } else {
          v.shakeHead();
          fx(v.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
              .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
              .sound(Sound.ENTITY_VILLAGER_NO, 1F, 1F);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  @ReceiveCancelledEvents
  public void on(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player p)) {
      return;
    }
    int level = active.getOrDefault(p.getUniqueId(), 0);
    if (level == 0) return;

    if (event.isCancelled()) {
      active.remove(p.getUniqueId());
      p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
    } else {
      p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, level, true, true));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player p) || !active.containsKey(p.getUniqueId())) {
      return;
    }

    active.remove(p.getUniqueId());
    p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
  }

  @Override
  public void onTick() {
    active.forEach((p, lvl) -> {
      org.bukkit.entity.Player player = Bukkit.getPlayer(p);
      if (player == null) return;
      J.runEntity(player, () -> player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, lvl, true, true)));
    });
  }

  @ConfigDescription("Get better villager trades at the cost of XP per interaction.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectivenessBase = 0.005;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEffectiveness = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Drain for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int levelDrain = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Cost Add for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int levelCostAdd = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifier = 1.0;

    public Config() {
      baseCost = 1;
      costFactor = 0.01;
      initialCost = 5;
    }
  }
}
