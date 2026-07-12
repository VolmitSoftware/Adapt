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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DiscoveryPolymath extends SimpleAdaptation<DiscoveryPolymath.Config> {
  private static final int REFRESH_INTERVAL_MILLIS = 3000;
  private static final int MULTIPLIER_DURATION_MILLIS = 2600;
  private static final long CHIME_INTERVAL_MILLIS = 30000L;

  private final Cooldowns chimeThrottle = cooldowns();

  public DiscoveryPolymath() {
    super("discovery-polymath");
    registerConfiguration(Config.class);
    setIcon(Material.KNOWLEDGE_BOOK);
    setInterval(REFRESH_INTERVAL_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.KNOWLEDGE_BOOK)
        .key("challenge_discovery_polymath_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_BOOK)
            .key("challenge_discovery_polymath_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_polymath_500", "discovery.polymath.boosts", 500, 400);
    registerMilestone("challenge_discovery_polymath_5k", "discovery.polymath.boosts", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(perSkillBonus(getLevelPercent(level), getConfig().perSkillBonusBase, getConfig().perSkillBonusFactor), 1), 1);
    statLore(v, C.YELLOW, "* ", getConfig().skillThreshold, 2);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      J.runEntity(player, () -> boostOwned(player));
    }
  }

  private void boostOwned(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    PlayerData data = getPlayer(player).getData();
    int qualifying = 0;
    for (PlayerSkillLine line : data.getSkillLines().values()) {
      if (line != null && line.getLevel() >= getConfig().skillThreshold) {
        qualifying++;
      }
    }

    double bonus = totalBonus(qualifying, perSkillBonus(getLevelPercent(level), getConfig().perSkillBonusBase, getConfig().perSkillBonusFactor), getConfig().maxTotalBonus);
    if (bonus <= 0) {
      return;
    }

    data.globalXPMultiplier(bonus, MULTIPLIER_DURATION_MILLIS);
    data.addStat("discovery.polymath.boosts", 1);

    UUID id = player.getUniqueId();
    if (getConfig().showParticles && chimeThrottle.isReady(id, CHIME_INTERVAL_MILLIS)) {
      chimeThrottle.mark(id);
      fx(player.getEyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, 4, 0, 0, 0, 0.4, 0.02)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.9F);
    }
  }

  static double perSkillBonus(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static double totalBonus(int qualifyingSkills, double perSkill, double maxTotal) {
    return Math.min(Math.max(0D, maxTotal), Math.max(0, qualifyingSkills) * Math.max(0D, perSkill));
  }


  @ConfigDescription("Each skill you have leveled past a threshold adds a small global XP-gain bonus.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Per Skill Bonus Base for the Discovery Polymath adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSkillBonusBase = 0.015;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Per Skill Bonus Factor for the Discovery Polymath adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perSkillBonusFactor = 0.045;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill level a line must reach to contribute a global XP bonus.", impact = "Higher values require deeper skills before they count toward the bonus.")
    int skillThreshold = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum combined global XP bonus across all qualifying skills.", impact = "Higher values raise the ceiling of the stacked XP bonus.")
    double maxTotalBonus = 1.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.4;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
