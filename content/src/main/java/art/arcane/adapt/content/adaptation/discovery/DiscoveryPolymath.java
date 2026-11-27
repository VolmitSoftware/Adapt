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
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.UUID;

public class DiscoveryPolymath extends SimpleAdaptation<DiscoveryPolymath.Config> {
  public static final Id ID = Id.adapt("discovery/polymath");
  private static final int REFRESH_INTERVAL_MILLIS = 3000;
  private static final int MULTIPLIER_DURATION_MILLIS = 2600;
  private static final long CHIME_INTERVAL_MILLIS = 30000L;

  private final Cooldowns chimeThrottle = cooldowns();

  public DiscoveryPolymath() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("knowledge_book"));
    setInterval(REFRESH_INTERVAL_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("knowledge_book"))
        .key("challenge_discovery_polymath_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_book"))
            .key("challenge_discovery_polymath_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_polymath_500", "discovery.polymath.boosts", 500, 400);
    registerMilestone("challenge_discovery_polymath_5k", "discovery.polymath.boosts", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(perSkillBonus(getLevelPercent(level), getConfig().perSkillBonusBase, getConfig().perSkillBonusFactor), 1), 1);
    statLore(v, TextFormat.YELLOW, "* ", getConfig().skillThreshold, 2);
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

      AdaptApi.scheduler().runEntity(player, () -> boostOwned(player));
    }
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (!hasActiveAdaptation(player.player())) {
      player.endBoosts(getId().toString());
    }
  }

  private void boostOwned(PlayerRef player) {
    PlayerProfile data = getPlayer(player);
    if (data == null) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      data.endBoosts(getId().toString());
      return;
    }

    int qualifying = DiscoveryXpShare.levelledSkills(data, getConfig().skillThreshold);

    double bonus = totalBonus(qualifying, perSkillBonus(getLevelPercent(level), getConfig().perSkillBonusBase, getConfig().perSkillBonusFactor), getConfig().maxTotalBonus);
    if (bonus <= 0) {
      return;
    }

    DiscoveryXpShare.boost(data, getId().toString(), bonus, MULTIPLIER_DURATION_MILLIS);
    data.addStat("discovery.polymath.boosts", 1);

    UUID id = player.uuid();
    if (getConfig().showParticles && chimeThrottle.isReady(id, CHIME_INTERVAL_MILLIS)) {
      chimeThrottle.mark(id);
      fx(player.eyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.ENCHANT, 4, 0, 0, 0, 0.4, 0.02)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.9F);
    }
  }

  public static double perSkillBonus(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  public static double totalBonus(int qualifyingSkills, double perSkill, double maxTotal) {
    return Math.min(Math.max(0D, maxTotal), Math.max(0, qualifyingSkills) * Math.max(0D, perSkill));
  }


  @ConfigDescription("Each skill you have leveled past a threshold adds a small global XP-gain bonus.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Per Skill Bonus Base for the Discovery Polymath adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSkillBonusBase = 0.015;
    @ConfigDoc(value = "Controls Per Skill Bonus Factor for the Discovery Polymath adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSkillBonusFactor = 0.045;
    @ConfigDoc(value = "Skill level a line must reach to contribute a global XP bonus.", impact = "Higher values require deeper skills before they count toward the bonus.")
    int skillThreshold = 5;
    @ConfigDoc(value = "Maximum combined global XP bonus across all qualifying skills.", impact = "Higher values raise the ceiling of the stacked XP bonus.")
    double maxTotalBonus = 1.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
