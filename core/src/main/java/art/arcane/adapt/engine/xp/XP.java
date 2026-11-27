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

package art.arcane.adapt.engine.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.engine.mutation.kit.DefaultKit;
import art.arcane.adapt.engine.protection.RegionPolicyService;
import art.arcane.adapt.engine.runtime.AdaptationGate;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;

public class XP {
  private static final int MAX_SOURCE = 160;

  public static void xp(PlayerRef p, Skill<?> skill, double xp) {
    xp(runtimePlayer(p), skill, xp, null);
  }

  public static void xp(PlayerRef p, Skill<?> skill, double xp, String rewardKey) {
    xp(runtimePlayer(p), skill, xp, rewardKey);
  }

  public static void xp(AdaptPlayer p, Skill<?> skill, double xp) {
    xp(p, skill, xp, null);
  }

  public static void xp(AdaptPlayer p, Skill<?> skill, double xp, String rewardKey) {
    if (!runtimeReady(p) || skill == null || !Double.isFinite(xp) || xp <= 0D) {
      return;
    }
    award(p, skill, XpAward.gameplay(xp, source(skill, rewardKey)));
  }

  public static void xpSilent(PlayerRef p, Skill<?> skill, double xp) {
    xpSilent(runtimePlayer(p), skill, xp, null);
  }

  public static void xpSilent(PlayerRef p, Skill<?> skill, double xp, String rewardKey) {
    xpSilent(runtimePlayer(p), skill, xp, rewardKey);
  }

  public static void xpSilent(AdaptPlayer p, Skill<?> skill, double xp) {
    xpSilent(p, skill, xp, null);
  }

  public static void xpSilent(AdaptPlayer p, Skill<?> skill, double xp, String rewardKey) {
    if (!runtimeReady(p) || skill == null || !Double.isFinite(xp) || xp <= 0D) {
      return;
    }
    award(p, skill, XpAward.gameplay(xp, source(skill, rewardKey)).silent());
  }

  public static void award(PlayerRef player, Skill<?> skill, XpAward award) {
    award(runtimePlayer(player), skill, award);
  }

  public static void award(AdaptPlayer player, Skill<?> skill, XpAward award) {
    if (!runtimeReady(player) || skill == null || award == null || player.getPlayer() == null) {
      return;
    }
    PlayerRef online = player.getPlayer();
    if (!Tasks.isOwnedByCurrentRegion(online)) {
      Tasks.runEntity(online, () -> award(player, skill, award));
      return;
    }
    if (award.kind() == XpAward.Kind.GAMEPLAY && AdaptationGate.shouldSkipPlayer(online, skill, true)) {
      return;
    }
    PlayerSkillLine line = player.getSkillLine(SkillHost.lineKey(skill));
    if (line == null) {
      return;
    }
    double contextual = award.amount();
    if (award.kind() == XpAward.Kind.GAMEPLAY) {
      Loc location = award.location().orElse(online.location());
      contextual = RegionPolicyService.adjustXp(contextual, RegionPolicyService.resolve(online, location));
      if (Double.isFinite(contextual) && contextual > 0D) {
        contextual *= XpNovelty.noveltyMultiplier(online, location, award.source());
        player.getData().noteActivity(SkillHost.lineKey(skill));
      }
      SkillPair mutation = player.getData().mutationOf(SkillHost.lineKey(skill));
      if (mutation != null) {
        contextual = line.sameActionPayment(mutation.lead().equals(skill.getId()), contextual, online.ticksLived(),
            AdaptConfig.get().getMutations().getSameActionSmallerShare());
      }
    }
    XpReceipt receipt = line.award(!award.quiet() ? player.getNot() : null, award, contextual,
        player.getData().getXpEarnings());
    if (receipt.credited() > 0D && award.kind() == XpAward.Kind.GAMEPLAY) {
      DefaultKit.earned(online, skill);
    }
  }

  public static String source(Skill<?> skill, String rewardKey) {
    if (rewardKey == null || rewardKey.isBlank()) {
      return "skill:" + SkillHost.lineKey(skill) + ":general";
    }
    String source = rewardKey.trim();
    return source.length() > MAX_SOURCE ? source.substring(0, MAX_SOURCE) : source;
  }

  public static void spatialXP(Loc l, Skill<?> skill, double xp, int rad, long duration) {
    AdaptCore.get().getAdaptServer().offer(new SpatialXP(l, skill, xp, rad, duration));
  }

  public static void wisdom(PlayerRef p, long k) {
    wisdom(runtimePlayer(p), k);
  }

  public static void wisdom(AdaptPlayer p, long k) {
    if (!runtimeReady(p)) {
      return;
    }
    p.getData().setWisdom(p.getData().getWisdom() + k);
  }

  public static void knowledge(PlayerRef p, Skill<?> skill, long k) {
    knowledge(runtimePlayer(p), skill, k);
  }

  public static void knowledge(AdaptPlayer p, Skill<?> skill, long k) {
    if (!runtimeReady(p) || skill == null) {
      return;
    }
    PlayerSkillLine skillLine = p.getSkillLine(SkillHost.lineKey(skill));
    if (skillLine != null) {
      skillLine.grantKnowledge(k);
    }
  }

  public static void boostXP(PlayerRef p, Skill<?> skill, double percentChange, long durationMS) {
    boostXP(runtimePlayer(p), skill, percentChange, durationMS);
  }

  public static void boostXP(AdaptPlayer p, Skill<?> skill, double percentChange, long durationMS) {
    if (!runtimeReady(p) || skill == null) {
      return;
    }
    PlayerSkillLine skillLine = p.getSkillLine(SkillHost.lineKey(skill));
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

  private static AdaptPlayer runtimePlayer(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    if (player == null || core == null || core.getAdaptServer() == null) {
      return null;
    }
    AdaptPlayer adaptPlayer = core.getAdaptServer().getOnlineAdaptPlayer(player.uuid());
    return runtimeReady(adaptPlayer) && player.equals(adaptPlayer.getPlayer()) && player.isOnline()
        ? adaptPlayer : null;
  }

  private static boolean runtimeReady(AdaptPlayer player) {
    return player != null && player.isRuntimeReady();
  }
}
