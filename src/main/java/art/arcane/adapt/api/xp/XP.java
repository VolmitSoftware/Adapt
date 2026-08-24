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

package art.arcane.adapt.api.xp;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class XP {
  public static void xp(Player p, Skill<?> skill, double xp) {
    xp(runtimePlayer(p), skill, xp, null);
  }

  public static void xp(Player p, Skill<?> skill, double xp, String rewardKey) {
    xp(runtimePlayer(p), skill, xp, rewardKey);
  }

  public static void xp(AdaptPlayer p, Skill<?> skill, double xp) {
    xp(p, skill, xp, null);
  }

  public static void xp(AdaptPlayer p, Skill<?> skill, double xp, String rewardKey) {
    if (!runtimeReady(p) || skill == null) {
      return;
    }
    PlayerSkillLine skillLine = p.getSkillLine(skill.getName());
    if (skillLine != null) {
      p.getData().resetMonotonyForOtherSkills(skill.getName());
      skillLine.giveXP(p.getNot(), xp, rewardKey);
    }
  }

  public static void xpSilent(Player p, Skill<?> skill, double xp) {
    xpSilent(runtimePlayer(p), skill, xp, null);
  }

  public static void xpSilent(Player p, Skill<?> skill, double xp, String rewardKey) {
    xpSilent(runtimePlayer(p), skill, xp, rewardKey);
  }

  public static void xpSilent(AdaptPlayer p, Skill<?> skill, double xp) {
    xpSilent(p, skill, xp, null);
  }

  public static void xpSilent(AdaptPlayer p, Skill<?> skill, double xp, String rewardKey) {
    if (!runtimeReady(p) || skill == null) {
      return;
    }
    PlayerSkillLine skillLine = p.getSkillLine(skill.getName());
    if (skillLine == null) {
      return;
    }

    p.getData().resetMonotonyForOtherSkills(skill.getName());
    skillLine.giveXP(null, xp, rewardKey);
  }

  public static void spatialXP(Location l, Skill<?> skill, double xp, int rad, long duration) {
    Adapt.instance.getAdaptServer().offer(new SpatialXP(l, skill, xp, rad, duration));
  }

  public static void wisdom(Player p, long k) {
    wisdom(runtimePlayer(p), k);
  }

  public static void wisdom(AdaptPlayer p, long k) {
    if (!runtimeReady(p)) {
      return;
    }
    p.getData().setWisdom(p.getData().getWisdom() + k);
  }

  public static void knowledge(Player p, Skill<?> skill, long k) {
    knowledge(runtimePlayer(p), skill, k);
  }

  public static void knowledge(AdaptPlayer p, Skill<?> skill, long k) {
    if (!runtimeReady(p) || skill == null) {
      return;
    }
    PlayerSkillLine skillLine = p.getSkillLine(skill.getName());
    if (skillLine != null) {
      skillLine.giveKnowledge(k);
    }
  }

  public static void boostXP(Player p, Skill<?> skill, double percentChange, long durationMS) {
    boostXP(runtimePlayer(p), skill, percentChange, durationMS);
  }

  public static void boostXP(AdaptPlayer p, Skill<?> skill, double percentChange, long durationMS) {
    if (!runtimeReady(p) || skill == null) {
      return;
    }
    PlayerSkillLine skillLine = p.getSkillLine(skill.getName());
    if (skillLine != null) {
      skillLine.boost(percentChange, durationMS);
    }
  }

  public static double getXpUntilLevelUp(double xp) {
    double level = getLevelForXp(xp);
    int currentLevel = (int) Math.floor(level);
    int maximumLevel = Math.max(1, AdaptConfig.get().experienceMaxLevel);
    int nextLevel = currentLevel >= maximumLevel ? maximumLevel : currentLevel + 1;
    double xa = getXpForLevel(currentLevel);
    double xb = getXpForLevel(nextLevel);
    return M.lerp(xb - xa, 0, level - (int) level);
  }

  public static double getLevelProgress(double xp) {
    double level = getLevelForXp(xp);
    return level - (int) level;
  }

  public static double getXpForLevel(double level) {
    AdaptConfig config = AdaptConfig.get();
    int maximumLevel = Math.max(1, config.experienceMaxLevel);
    double boundedLevel;
    if (Double.isNaN(level) || level <= 0D) {
      boundedLevel = 0D;
    } else if (level >= maximumLevel) {
      boundedLevel = maximumLevel;
    } else {
      boundedLevel = level;
    }

    double xp = config.getXpCurve().getCurve().getXPForLevel(boundedLevel);
    if (Double.isFinite(xp)) {
      return xp;
    }
    return Curves.ADAPT_BALANCED.getCurve().getXPForLevel(boundedLevel);
  }

  public static double getLevelForXp(double xp) {
    AdaptConfig config = AdaptConfig.get();
    int maximumLevel = Math.max(1, config.experienceMaxLevel);
    if (Double.isNaN(xp) || xp <= 0D) {
      return 0D;
    }
    if (xp == Double.POSITIVE_INFINITY) {
      return maximumLevel;
    }

    double level = config.getXpCurve().getCurve().computeLevelForXP(xp, 0.000001);
    if (!Double.isFinite(level)) {
      level = Curves.ADAPT_BALANCED.getCurve().computeLevelForXP(xp, 0.000001);
    }
    return Math.max(0D, Math.min(maximumLevel, level));
  }

  private static AdaptPlayer runtimePlayer(Player player) {
    Adapt plugin = Adapt.instance;
    if (player == null || plugin == null || plugin.getAdaptServer() == null) {
      return null;
    }
    AdaptPlayer adaptPlayer = plugin.getAdaptServer().getOnlineAdaptPlayer(player.getUniqueId());
    return runtimeReady(adaptPlayer) && adaptPlayer.getPlayer() == player ? adaptPlayer : null;
  }

  private static boolean runtimeReady(AdaptPlayer player) {
    return player != null && player.isRuntimeReady();
  }
}
