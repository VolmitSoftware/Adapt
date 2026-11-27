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
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.UUID;

public class DiscoveryTrailblazer extends SimpleAdaptation<DiscoveryTrailblazer.Config> {
  public static final Id ID = Id.adapt("discovery/trailblazer");
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed-burst", Text.key("adapt.discovery.preferences.discoverytrailblazer.speed-burst"), CommonPreferences.Toggle.ON);

  private static final long SCAN_INTERVAL_MILLIS = 1200L;

  private final Cooldowns scanThrottle = cooldowns();

  public DiscoveryTrailblazer() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_boots"));
    setInterval(600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_discovery_trailblazer_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("filled_map"))
            .key("challenge_discovery_trailblazer_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_trailblazer_25", "discovery.trailblazer.discoveries", 25, 400);
    registerMilestone("challenge_discovery_trailblazer_100", "discovery.trailblazer.discoveries", 100, 1200);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (!hasActiveAdaptation(player.player()) || !preferenceEnabled(player.player(), SPEED)) {
      AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    }
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SPEED);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(firstVisitXp(getLevelPercent(level), getConfig().firstVisitXpBase, getConfig().firstVisitXpFactor), 0), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(speedDurationTicks(getLevelPercent(level), getConfig().speedDurationTicksBase, getConfig().speedDurationTicksFactor) * 50D, 1), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      if (player == null || !player.isOnline()) {
        continue;
      }

      UUID id = player.uuid();
      if (!scanThrottle.isReady(id, SCAN_INTERVAL_MILLIS)) {
        continue;
      }

      scanThrottle.mark(id);
      AdaptApi.scheduler().runEntity(player, () -> scanOwned(player));
    }
  }

  private void scanOwned(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    PlayerProfile profile = getPlayer(player);
    FirstVisits.Visit visit = profile == null ? FirstVisits.Visit.NONE : FirstVisits.record(profile, player);
    if (visit != FirstVisits.Visit.NONE) {
      rewardDiscovery(player, level, visit == FirstVisits.Visit.STRUCTURE);
    }
  }

  private void rewardDiscovery(PlayerRef player, int level, boolean structure) {
    double xpAmount = firstVisitXp(getLevelPercent(level), getConfig().firstVisitXpBase, getConfig().firstVisitXpFactor);
    if (structure) {
      xpAmount *= getConfig().structureXpMultiplier;
    }

    xp(player, xpAmount);
    flushDiscoveryXp(player);
    int speedTicks = speedDurationTicks(getLevelPercent(level), getConfig().speedDurationTicksBase, getConfig().speedDurationTicksFactor);
    if (speedTicks > 0 && preferenceEnabled(player, SPEED)) {
      AdaptAttributeService.get().applyTimed(player, getId().toString(), "speed", Attributes.MOVEMENT_SPEED, speedBonus(getConfig().speedAmplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, speedTicks);
    }
    addStat(player, "discovery.trailblazer.discoveries", 1);

    Rgb color = structure ? new Rgb(255, 200, 60) : new Rgb(0, 255, 255);
    timeline(player)
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .cullRadius(28)
        .frame((f, tick, progress) -> {
          f.dustRing(color, 0.6D + (progress * 3.0D), 12, 1.0F);
          if (tick == 0) {
            f.chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.5F, structure ? 1.0F : 1.4F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
          }
        })
        .start();
  }

  private void flushDiscoveryXp(PlayerRef player) {
    PlayerProfile profile = getPlayer(player);
    if (profile != null) {
      profile.skill(getSkillId()).flush();
    }
  }

  public static double firstVisitXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static int speedDurationTicks(double levelPercent, double base, double factor) {
    return Math.max(20, (int) Math.round(base + (levelPercent * factor)));
  }

  static double speedBonus(int amplifier) {
    return 0.20D * (Math.max(0, amplifier) + 1);
  }

  @ConfigDescription("Your first visit to each biome or structure type grants a skill-XP burst and brief speed.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls First Visit Xp Base for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstVisitXpBase = 40;
    @ConfigDoc(value = "Controls First Visit Xp Factor for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstVisitXpFactor = 160;
    @ConfigDoc(value = "Multiplier applied to the reward when the first visit is a structure type instead of a biome.", impact = "Higher values make structure discoveries pay more than biome discoveries.")
    double structureXpMultiplier = 2.5;
    @ConfigDoc(value = "Controls Speed Duration Ticks Base for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedDurationTicksBase = 80;
    @ConfigDoc(value = "Controls Speed Duration Ticks Factor for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedDurationTicksFactor = 120;
    @ConfigDoc(value = "Speed tier granted on a fresh discovery (0 grants +20% movement speed, each tier adds another +20%).", impact = "Higher values grant a stronger speed burst.")
    int speedAmplifier = 1;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
