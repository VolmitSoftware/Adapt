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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;

public class DiscoveryVillagerAtt extends SimpleAdaptation<DiscoveryVillagerAtt.Config> {
  private static final int PENDING_TIMEOUT_TICKS = 20;
  private static final int EFFECT_DURATION_TICKS = 200;
  private static final int EFFECT_REFRESH_TICKS = 160;
  private final Map<UUID, PendingTrade> pending = playerState();
  private final Map<UUID, TradeSession> active = playerState();
  private final AtomicLong sessionSequence = new AtomicLong();

  public DiscoveryVillagerAtt() {
    super("discovery-villager-att");
    registerConfiguration(Config.class);
    setLocalizationKey("discovery.villager");
    setIcon(Material.GLASS_BOTTLE);
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
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(DiscoveryMessages.VILLAGER_LORE1));
    statLore(v, Form.pc(getEffectiveness(getLevelPercent(level)), 0), 2);
    statLore(v, getXpTaken(level), 3);
  }

  private double getEffectiveness(double multiplier) {
    return effectiveness(getConfig().maxEffectiveness, getConfig().effectivenessBase, multiplier);
  }

  private int getXpTaken(double level) {
    return xpCost(getConfig().levelCostAdd, getConfig().amplifier, getConfig().levelDrain, level);
  }

  static double effectiveness(double configuredMaximum, double base, double multiplier) {
    double ceiling = Math.max(0D, Math.min(1D, configuredMaximum));
    return Math.max(0D, Math.min(ceiling, multiplier * multiplier + base));
  }

  static int xpCost(int baseCost, double amplifier, int levelDrain, double level) {
    return Math.max(1, (int) Math.ceil((baseCost * amplifier) - (level * levelDrain)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (!(e.getRightClicked() instanceof Villager villager) || level <= 0
        || ThreadLocalRandom.current().nextDouble() > getEffectiveness(getLevelPercent(level))) {
      return;
    }

    int cost = getXpTaken(level);
    if (p.getLevel() < cost) {
      villager.shakeHead();
      fx(villager.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.ENTITY_VILLAGER_NO, 1F, 1F);
      return;
    }

    UUID playerId = p.getUniqueId();
    PendingTrade previousPending = pending.get(playerId);
    long startedAt = previousPending == null ? System.currentTimeMillis() : previousPending.startedAt();
    PotionEffect previousEffect = previousPending == null
        ? p.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE)
        : previousPending.previousEffect();
    PendingTrade candidate = new PendingTrade(villager.getUniqueId(), level, cost,
        sessionSequence.incrementAndGet(), previousEffect, startedAt);
    pending.put(playerId, candidate);
    applyTradeEffect(p, level);
    J.runEntity(p, () -> {
      if (pending.remove(playerId, candidate)) {
        restoreHeroEffect(p, candidate.level(), candidate.previousEffect(), candidate.startedAt());
      }
    }, PENDING_TIMEOUT_TICKS);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  @ReceiveCancelledEvents
  public void on(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player p)) {
      return;
    }
    UUID playerId = p.getUniqueId();
    PendingTrade candidate = pending.remove(playerId);
    if (candidate == null || event.isCancelled() || !(event.getInventory() instanceof MerchantInventory merchantInventory)
        || !(merchantInventory.getMerchant() instanceof Villager villager)
        || !candidate.villagerId().equals(villager.getUniqueId()) || p.getLevel() < candidate.cost()) {
      if (candidate != null) {
        restoreHeroEffect(p, candidate.level(), candidate.previousEffect(), candidate.startedAt());
      }
      return;
    }

    p.setLevel(p.getLevel() - candidate.cost());
    TradeSession session = new TradeSession(candidate.villagerId(), candidate.level(), candidate.sequence(),
        candidate.previousEffect(), candidate.startedAt());
    active.put(playerId, session);
    refreshTradeSession(p, session);
    playActivationFx(p, villager);
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("io.papermc.paper.event.player.PlayerTradeEvent")) {
      return List.of();
    }

    return List.of(new TradeListener());
  }

  private final class TradeListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerTradeEvent event) {
      Player player = event.getPlayer();
      TradeSession session = active.get(player.getUniqueId());
      if (session != null && session.villagerId().equals(event.getMerchant().getUniqueId())) {
        addStat(player, "discovery.villager-att.improved-trades", 1);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player p)) {
      return;
    }

    TradeSession session = active.remove(p.getUniqueId());
    if (session != null) {
      restoreHeroEffect(p, session.level(), session.previousEffect(), session.startedAt());
    }
  }

  private void refreshTradeSession(Player player, TradeSession session) {
    UUID playerId = player.getUniqueId();
    if (active.get(playerId) != session || !player.isOnline()
        || !(player.getOpenInventory().getTopInventory() instanceof MerchantInventory merchantInventory)
        || !(merchantInventory.getMerchant() instanceof Villager villager)
        || !session.villagerId().equals(villager.getUniqueId())) {
      if (active.remove(playerId, session)) {
        restoreHeroEffect(player, session.level(), session.previousEffect(), session.startedAt());
      }
      return;
    }

    PotionEffect current = player.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
    if (current == null || current.getAmplifier() != session.level() || current.getDuration() <= 60) {
      applyTradeEffect(player, session.level());
    }
    J.runEntity(player, () -> refreshTradeSession(player, session), EFFECT_REFRESH_TICKS);
  }

  private void playActivationFx(Player player, Villager villager) {
    timeline(villager)
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
        .onComplete(() -> J.runEntity(player, () -> fx(player.getLocation(), FxPriority.TRANSITION)
            .dustRing(Color.fromRGB(80, 200, 120), 1.0D, 12, 1.0F)
            .dome(Particles.VILLAGER_HAPPY, 1.5D, 24)
            .chord(Sound.BLOCK_BEACON_POWER_SELECT, 0.3F, 1.4F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F)))
        .start();
  }

  private void applyTradeEffect(Player player, int level) {
    player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
        EFFECT_DURATION_TICKS, level, true, true), true);
  }

  private void restoreHeroEffect(Player player, int adaptationLevel, PotionEffect previous, long startedAt) {
    PotionEffect current = player.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
    if (current == null || current.getAmplifier() != adaptationLevel) {
      return;
    }

    player.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
    if (previous == null) {
      return;
    }
    long elapsedTicks = Math.max(0L, (System.currentTimeMillis() - startedAt) / 50L);
    int remaining = (int) Math.max(0L, previous.getDuration() - elapsedTicks);
    if (remaining > 0) {
      player.addPotionEffect(new PotionEffect(previous.getType(), remaining, previous.getAmplifier(),
          previous.isAmbient(), previous.hasParticles(), previous.hasIcon()), true);
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    for (Map.Entry<UUID, PendingTrade> entry : pending.entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      PendingTrade candidate = entry.getValue();
      if (player != null && candidate != null) {
        J.runEntity(player, () -> restoreHeroEffect(player, candidate.level(), candidate.previousEffect(), candidate.startedAt()));
      }
    }
    pending.clear();
    for (UUID playerId : active.keySet()) {
      Player player = Bukkit.getPlayer(playerId);
      TradeSession session = active.get(playerId);
      if (player != null && session != null) {
        J.runEntity(player, () -> restoreHeroEffect(player, session.level(), session.previousEffect(), session.startedAt()));
      }
    }
    active.clear();
  }

  private record PendingTrade(UUID villagerId, int level, int cost, long sequence,
                              PotionEffect previousEffect, long startedAt) {
  }

  private record TradeSession(UUID villagerId, int level, long sequence,
                              PotionEffect previousEffect, long startedAt) {
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
