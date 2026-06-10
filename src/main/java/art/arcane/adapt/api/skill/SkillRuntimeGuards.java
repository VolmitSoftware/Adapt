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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SkillRuntimeGuards {
  private static final Map<String, String> USE_PERMISSION_NODES = new ConcurrentHashMap<>();

  private SkillRuntimeGuards() {
  }

  static void checkStatTrackers(Skill<?> skill, AdaptPlayer player) {
    if (skill == null || player == null || !skill.isEnabled()) {
      return;
    }
    if (!AdaptConfig.get().isAdvancements()) {
      return;
    }
    if (!isRuntimePlayer(player.getPlayer())) {
      return;
    }

    PlayerData data = player.getData();

    for (AdaptStatTracker tracker : skill.getStatTrackers()) {
      if (!data.isGranted(tracker.getAdvancement()) && data.getStat(tracker.getStat()) >= tracker.getGoal()) {
        player.getAdvancementHandler().grant(tracker.getAdvancement());
        skill.xp(player.getPlayer(), tracker.getReward());
      }
    }

    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
        continue;
      }
      if (!adaptation.isEnabled()) {
        continue;
      }
      for (AdaptStatTracker tracker : simpleAdaptation.getStatTrackers()) {
        if (!data.isGranted(tracker.getAdvancement()) && data.getStat(tracker.getStat()) >= tracker.getGoal()) {
          player.getAdvancementHandler().grant(tracker.getAdvancement());
          skill.xp(player.getPlayer(), tracker.getReward());
        }
      }
    }
  }

  static boolean hasUsePermission(Player player, Skill<?> skill) {
    if (player == null || skill == null) {
      return false;
    }
    if (player.isOp()) {
      return true;
    }
    String usePermission = USE_PERMISSION_NODES.computeIfAbsent(skill.getName(), n -> "adapt.use." + n.replace("-", ""));
    boolean permissionSet = player.isPermissionSet(usePermission);
    if (AdaptConfig.get().isVerbose()) {
      Adapt.verbose("Checking use permission " + usePermission + " for " + player.getName()
          + " (set=" + permissionSet + ", value=" + player.hasPermission(usePermission) + ")");
    }
    if (!permissionSet) {
      return true;
    }
    return player.hasPermission(usePermission);
  }

  static boolean shouldSkipPlayer(Skill<?> skill, Player player) {
    try {
      if (skill == null || player == null) {
        return true;
      }

      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
        return true;
      }

      return AdaptationGate.shouldSkipPlayer(player, skill, skill.getPlayer(player) != null);
    } catch (Exception ex) {
      Adapt.verbose("Failed shouldSkipPlayer check for " + (player == null ? "null" : player.getName())
          + " in skill " + (skill == null ? "null" : skill.getName()) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return true;
    }
  }

  static void withPlayer(Skill<?> skill, Player player, Runnable runnable) {
    try {
      if (skill == null || player == null || runnable == null) {
        return;
      }

      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
        J.runEntity(player, () -> withPlayer(skill, player, runnable));
        return;
      }

      if (shouldSkipPlayer(skill, player)) {
        return;
      }

      runnable.run();
    } catch (Exception ex) {
      Adapt.verbose("Failed guarded player runnable for skill " + (skill == null ? "null" : skill.getName()) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }

  static void withPlayer(Skill<?> skill, Player player, Cancellable cancellable, Runnable runnable) {
    try {
      if (skill == null || player == null || cancellable == null || runnable == null) {
        return;
      }

      if (cancellable.isCancelled()) {
        return;
      }

      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
        J.runEntity(player, () -> withPlayer(skill, player, cancellable, runnable));
        return;
      }

      if (cancellable.isCancelled() || shouldSkipPlayer(skill, player)) {
        return;
      }

      runnable.run();
    } catch (Exception ex) {
      Adapt.verbose("Failed guarded cancellable player runnable for skill " + (skill == null ? "null" : skill.getName()) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }

  static boolean shouldSkipWorld(Skill<?> skill, World world) {
    try {
      return AdaptationGate.shouldSkipWorld(world, skill);
    } catch (Exception ex) {
      Adapt.verbose("Failed shouldSkipWorld check for skill " + (skill == null ? "null" : skill.getName())
          + ": " + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return true;
    }
  }

  static boolean isWorldBlacklisted(Player player) {
    return AdaptationGate.isWorldBlacklisted(player);
  }

  static boolean isInCreativeOrSpectator(Player player) {
    return AdaptationGate.isInCreativeOrSpectator(player);
  }

  static boolean canGrantXp(Skill<?> skill, Player player) {
    return skill != null && skill.isEnabled() && isRuntimePlayer(player);
  }

  static void grantXp(Skill<?> skill, Player player, Location location, double xp, String rewardKey, boolean silent, boolean visualBurst) {
    if (!canGrantXp(skill, player)) {
      return;
    }
    try {
      if (silent) {
        XP.xpSilent(player, skill, xp, rewardKey);
      } else {
        XP.xp(player, skill, xp, rewardKey);
      }

      if (visualBurst && location != null && xp > 50) {
        skill.vfxXP(player, location, (int) xp);
      }
      if (AdaptConfig.get().isVerbose()) {
        Adapt.verbose("Gave " + player.getName() + " " + xp + " xp in " + skill.getName() + " " + skill.getClass());
      }
    } catch (Exception ex) {
      Adapt.verbose("Failed to give xp to " + player.getName() + " for " + skill.getName() + " (" + xp + ")");
    }
  }

  static void grantXpSilent(Skill<?> skill, Player player, double xp, String rewardKey) {
    if (!canGrantXp(skill, player)) {
      return;
    }
    try {
      XP.xpSilent(player, skill, xp, rewardKey);
    } catch (Exception ignored) {
      Adapt.verbose("Player was Given XP (Likely Teleportation) before i can see it because some plugin has higher priority than me and moves a player. so im not going to throw an error, as i know why it's happening.");
    }
  }

  static void grantKnowledge(Skill<?> skill, Player player, long knowledge) {
    if (skill == null || !skill.isEnabled() || player == null) {
      return;
    }
    XP.knowledge(player, skill, knowledge);
  }

  static boolean isRuntimePlayer(Player player) {
    return player != null && player.getClass().getSimpleName().equals("CraftPlayer");
  }
}
