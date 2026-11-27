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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

public class TamingFetch extends SimpleAdaptation<TamingFetch.Config> {
  public static final Id ID = Id.adapt("tame/fetch");
  private final TameFetcher fetcher = new TameFetcher(this, (PlayerRef owner) -> {
    addStat(owner, "taming.fetch.items-fetched", 1);
    xp(owner, getConfig().xpPerItemFetched);
  });

  public TamingFetch() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.taming.fetch");
    setIcon(Id.minecraft("hopper"));
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("hopper"))
        .key("challenge_taming_fetch_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
            .key("challenge_taming_fetch_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_fetch_1k", "taming.fetch.items-fetched", 1000, 400);
    registerMilestone("challenge_taming_fetch_10k", "taming.fetch.items-fetched", 10000, 1500);
  }

  @Override
  public void unregister() {
    super.unregister();
    fetcher.close();
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    fetcher.abortOwnerFetches(player.player().uuid());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.ITEMS, TamingPreferences.COLLAR);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getFetchRange(level), 1), 1);
    statLore(v, Format.pc(getCarryRate(level), 0), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    fetcher.abortOwnerFetches(e.player().uuid());
  }

  @Override
  public void onTick() {
    if (!isEnabled()) {
      return;
    }

    long now = System.currentTimeMillis();
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef owner = adaptPlayer.player();
      if (owner == null) {
        continue;
      }
      AdaptApi.scheduler().runEntity(owner, () -> fetcher.fetch(owner, this::tuning));
    }
  }

  private TameFetcher.Tuning tuning(int level) {
    return new TameFetcher.Tuning(getFetchRange(level), getCarryRate(level), getConfig().wolfSearchRadius,
        getConfig().maxWolves, getConfig().maxCarryPerTick, getConfig().pathfindRadius, getConfig().fetchWalkSpeed,
        getConfig().fetchDeadlineMillis, getConfig().maintenanceIntervalTicks);
  }

  private double getFetchRange(int level) {
    return getConfig().fetchRangeBase + (getLevelPercent(level) * getConfig().fetchRangeFactor);
  }

  private double getCarryRate(int level) {
    return Math.min(getConfig().maxCarryRate, getConfig().carryRateBase + (getLevelPercent(level) * getConfig().carryRateFactor));
  }

  static void pickupFx(Adaptation<?> owner, Loc itemLocation) {
    Fx.now(owner, itemLocation, FxPriority.GAMEPLAY)
        .burst(Particles.VILLAGER_HAPPY, 6, 0.25D)
        .sound(Sounds.ENTITY_ITEM_PICKUP, 0.5F, 1.4F);
  }

  static void deliverFx(Adaptation<?> owner, Loc from) {
    Fx.now(owner, from, FxPriority.GAMEPLAY).sound(Sounds.ENTITY_WOLF_PANT, 0.7F, 1.25F);
  }

  @ConfigDescription("Your idle tamed wolves physically run to nearby dropped items, pick them up, and carry them back.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Fetch Range Base for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fetchRangeBase = 6.0;
    @ConfigDoc(value = "Controls Fetch Range Factor for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fetchRangeFactor = 10.0;
    @ConfigDoc(value = "Controls Carry Rate Base for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double carryRateBase = 0.35;
    @ConfigDoc(value = "Controls Carry Rate Factor for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double carryRateFactor = 0.5;
    @ConfigDoc(value = "Controls Max Carry Rate for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxCarryRate = 0.9;
    @ConfigDoc(value = "Controls Wolf Search Radius for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double wolfSearchRadius = 24.0;
    @ConfigDoc(value = "Xp granted each time a wolf fetches a dropped item.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemFetched = 4;
    @ConfigDoc(value = "Maximum tamed wolves counted around the owner, capped internally at 12.", impact = "Higher values let larger packs fetch more items per pass but scan more entities.")
    int maxWolves = 6;
    @ConfigDoc(value = "Maximum dropped items fetched per owner each pass, capped internally at 8.", impact = "Lower values reduce item scheduling in dense drops.")
    int maxCarryPerTick = 4;
    @ConfigDoc(value = "Movement speed multiplier used while a wolf walks a fetch, clamped to 0.1 - 4.0.", impact = "Higher values make the round trip finish sooner but look more frantic.")
    double fetchWalkSpeed = 1.15;
    @ConfigDoc(value = "Maximum physical fetch distance in blocks, clamped internally at 11 by vanilla pet follow behavior.", impact = "Higher values let wolves claim more distant drops; drops beyond it remain where they are.")
    double pathfindRadius = 9.0;
    @ConfigDoc(value = "Milliseconds a walked fetch may run before it is given up, clamped to 1000 - 60000.", impact = "Higher values let wolves keep working awkward routes instead of releasing the drop.")
    long fetchDeadlineMillis = 9000;
    @ConfigDoc(value = "Ticks between re-issuing a walking wolf its path, clamped to 1 - 20.", impact = "Lower values hold the route tighter against vanilla goals but schedule more often.")
    int maintenanceIntervalTicks = 5;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
