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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryOpenEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerTradeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class DiscoveryVillagerAtt extends SimpleAdaptation<DiscoveryVillagerAtt.Config> {
  public static final Id ID = Id.adapt("discovery/villager_att");
  public static final PlayerPreference<DiscoveryPreferences.Reserve> XP_RESERVE = DiscoveryPreferences.reserve("xp-reserve", Text.key("adapt.discovery.preferences.discoveryvillageratt.xp-reserve"));

  private static final Id VILLAGER = Id.minecraft("villager");
  private static final Id HERO_OF_THE_VILLAGE = Id.minecraft("hero_of_the_village");
  private static final int PENDING_TIMEOUT_TICKS = 20;
  private static final int EFFECT_DURATION_TICKS = 200;
  private static final int EFFECT_REFRESH_TICKS = 160;
  private final Map<UUID, PendingTrade> pending = playerState();
  private final Map<UUID, TradeSession> active = playerState();
  private final AtomicLong sessionSequence = new AtomicLong();

  public DiscoveryVillagerAtt() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.discovery.villager");
    setIcon(Id.minecraft("glass_bottle"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("emerald"))
        .key("challenge_discovery_villager_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("emerald_block"))
            .key("challenge_discovery_villager_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_villager_100", "discovery.villager-att.improved-trades", 100, 300);
    registerMilestone("challenge_discovery_villager_2500", "discovery.villager-att.improved-trades", 2500, 1000);
  }



  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, XP_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.discovery.villager.lore1").resolve());
    statLore(v, Format.pc(getEffectiveness(getLevelPercent(level)), 0), 2);
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

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    EntityRef villager = e.clicked();
    if (!isVillager(villager) || level <= 0
        || ThreadLocalRandom.current().nextDouble() > getEffectiveness(getLevelPercent(level))) {
      return;
    }

    int cost = getXpTaken(level);
    if (p.level() - cost < preference(p, XP_RESERVE).amount()) {
      AdaptApi.platform().host().animals().shakeHead(villager);
      fx(villager.location().add(0, 1.0, 0), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sounds.ENTITY_VILLAGER_NO, 1F, 1F);
      return;
    }

    UUID playerId = p.uuid();
    PendingTrade previousPending = pending.get(playerId);
    long startedAt = previousPending == null ? System.currentTimeMillis() : previousPending.startedAt();
    EffectInstance previousEffect = previousPending == null
        ? p.effect(HERO_OF_THE_VILLAGE)
        : previousPending.previousEffect();
    PendingTrade candidate = new PendingTrade(villager.uuid(), level, cost,
        sessionSequence.incrementAndGet(), previousEffect, startedAt);
    pending.put(playerId, candidate);
    applyTradeEffect(p, level);
    AdaptApi.scheduler().runEntity(p, () -> {
      if (pending.remove(playerId, candidate)) {
        restoreHeroEffect(p, candidate.level(), candidate.previousEffect(), candidate.startedAt());
      }
    }, PENDING_TIMEOUT_TICKS);
  }

  @Handle(priority = EventPriority.MONITOR)
  @ReceiveCancelledEvents
  public void on(InventoryOpenEvent event) {
    PlayerRef p = event.player();
    UUID playerId = p.uuid();
    PendingTrade candidate = pending.remove(playerId);
    EntityRef villager = event.kind() == InventoryKind.MERCHANT ? event.holderEntity() : null;
    if (candidate == null || event.isCancelled() || !isVillager(villager)
        || !candidate.villagerId().equals(villager.uuid()) || p.level() - candidate.cost() < preference(p, XP_RESERVE).amount() || !hasActiveAdaptation(p)) {
      if (candidate != null) {
        restoreHeroEffect(p, candidate.level(), candidate.previousEffect(), candidate.startedAt());
      }
      return;
    }

    p.giveExpLevels(-candidate.cost());
    TradeSession session = new TradeSession(candidate.villagerId(), candidate.level(), candidate.sequence(),
        candidate.previousEffect(), candidate.startedAt());
    active.put(playerId, session);
    AdaptApi.scheduler().runEntity(p, () -> refreshTradeSession(p, session), 1);
    playActivationFx(p, villager);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTradeEvent event) {
    PlayerRef player = event.player();
    TradeSession session = active.get(player.uuid());
    if (session != null && event.merchant() != null && session.villagerId().equals(event.merchant().uuid())) {
      addStat(player, "discovery.villager-att.improved-trades", 1);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent event) {
    PlayerRef p = event.player();
    TradeSession session = active.remove(p.uuid());
    if (session != null) {
      restoreHeroEffect(p, session.level(), session.previousEffect(), session.startedAt());
    }
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef owner = player.player();
    if (hasActiveAdaptation(owner)) {
      return;
    }
    PendingTrade candidate = pending.remove(owner.uuid());
    if (candidate != null) {
      restoreHeroEffect(owner, candidate.level(), candidate.previousEffect(), candidate.startedAt());
    }
    TradeSession session = active.remove(owner.uuid());
    if (session != null) {
      restoreHeroEffect(owner, session.level(), session.previousEffect(), session.startedAt());
    }
  }

  private void refreshTradeSession(PlayerRef player, TradeSession session) {
    UUID playerId = player.uuid();
    EntityRef villager = player.isOnline() ? player.openMerchant() : null;
    if (active.get(playerId) != session || !player.isOnline() || !hasActiveAdaptation(player)
        || !isVillager(villager)
        || !session.villagerId().equals(villager.uuid())) {
      if (active.remove(playerId, session)) {
        restoreHeroEffect(player, session.level(), session.previousEffect(), session.startedAt());
      }
      return;
    }

    EffectInstance current = player.effect(HERO_OF_THE_VILLAGE);
    if (current == null || current.amplifier() != session.level() || current.durationTicks() <= 60) {
      applyTradeEffect(player, session.level());
    }
    AdaptApi.scheduler().runEntity(player, () -> refreshTradeSession(player, session), EFFECT_REFRESH_TICKS);
  }

  private void playActivationFx(PlayerRef player, EntityRef villager) {
    timeline(villager)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          f.helix(Particles.VILLAGER_HAPPY, 0.6D, 1.8D, 6, progress * Math.PI * 2.0D);
          if ((tick & 3) == 0) {
            f.particle(Particles.WAX_ON, 2, 0, 1.0, 0, 0.25, 0.01);
          }
          if (tick == 0) {
            f.chord(Sounds.ENTITY_VILLAGER_CELEBRATE, 1F, 1F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.3F);
          }
        })
        .onComplete(() -> AdaptApi.scheduler().runEntity(player, () -> fx(player.location(), FxPriority.TRANSITION)
            .dustRing(new Rgb(80, 200, 120), 1.0D, 12, 1.0F)
            .dome(Particles.VILLAGER_HAPPY, 1.5D, 24)
            .chord(Sounds.BLOCK_BEACON_POWER_SELECT, 0.3F, 1.4F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F)))
        .start();
  }

  private void applyTradeEffect(PlayerRef player, int level) {
    player.addEffect(EffectInstance.of(HERO_OF_THE_VILLAGE, EFFECT_DURATION_TICKS, level).withAmbient(true));
  }

  private void restoreHeroEffect(PlayerRef player, int adaptationLevel, EffectInstance previous, long startedAt) {
    EffectInstance current = player.effect(HERO_OF_THE_VILLAGE);
    if (current == null || current.amplifier() != adaptationLevel) {
      return;
    }

    player.removeEffect(HERO_OF_THE_VILLAGE);
    if (previous == null) {
      return;
    }
    long elapsedTicks = Math.max(0L, (System.currentTimeMillis() - startedAt) / 50L);
    int remaining = (int) Math.max(0L, previous.durationTicks() - elapsedTicks);
    if (remaining > 0) {
      player.addEffect(EffectInstance.of(previous.type(), remaining, previous.amplifier()).withAmbient(previous.ambient()).withParticles(previous.particles()).withIcon(previous.icon()));
    }
  }

  private static boolean isVillager(EntityRef entity) {
    return entity != null && VILLAGER.equals(entity.type());
  }

  @Override
  public void unregister() {
    super.unregister();
    for (Map.Entry<UUID, PendingTrade> entry : pending.entrySet()) {
      PlayerRef player = AdaptApi.platform().host().player(entry.getKey());
      PendingTrade candidate = entry.getValue();
      if (player != null && candidate != null) {
        AdaptApi.scheduler().runEntity(player, () -> restoreHeroEffect(player, candidate.level(), candidate.previousEffect(), candidate.startedAt()));
      }
    }
    pending.clear();
    for (UUID playerId : active.keySet()) {
      PlayerRef player = AdaptApi.platform().host().player(playerId);
      TradeSession session = active.get(playerId);
      if (player != null && session != null) {
        AdaptApi.scheduler().runEntity(player, () -> restoreHeroEffect(player, session.level(), session.previousEffect(), session.startedAt()));
      }
    }
    active.clear();
  }

  private record PendingTrade(UUID villagerId, int level, int cost, long sequence,
                              EffectInstance previousEffect, long startedAt) {
  }

  private record TradeSession(UUID villagerId, int level, long sequence,
                              EffectInstance previousEffect, long startedAt) {
  }

  @ConfigDescription("Get better villager trades at the cost of XP per interaction.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Effectiveness Base for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectivenessBase = 0.005;
    @ConfigDoc(value = "Controls Max Effectiveness for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEffectiveness = 100;
    @ConfigDoc(value = "Controls Level Drain for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int levelDrain = 2;
    @ConfigDoc(value = "Controls Level Cost Add for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int levelCostAdd = 10;
    @ConfigDoc(value = "Controls Amplifier for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifier = 1.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
