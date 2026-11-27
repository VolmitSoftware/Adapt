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

package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.engine.fx.Fx;
import art.arcane.adapt.engine.runtime.AdaptationGate;
import art.arcane.adapt.engine.tick.TickedObject;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptStatTracker;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SkillRuntimeGuards {
  private static final Map<Id, String> USE_PERMISSION_NODES = new ConcurrentHashMap<>();

  private SkillRuntimeGuards() {
  }

  static boolean canEvaluateStatTrackers(AdaptPlayer player) {
    return player != null
        && AdaptConfig.get().isAdvancements()
        && isRuntimePlayer(player.getPlayer());
  }

  static void evaluateStatTrackers(List<StatTrackerIndex.Binding> bindings, AdaptPlayer player, double statValue) {
    if (bindings.isEmpty()) {
      return;
    }

    PlayerRef runtimePlayer = player.getPlayer();
    PlayerData data = player.getData();
    Skill<?> currentSkill = null;
    boolean skipCurrentSkill = true;
    for (StatTrackerIndex.Binding binding : bindings) {
      Skill<?> skill = binding.skill();
      if (skill != currentSkill) {
        currentSkill = skill;
        skipCurrentSkill = shouldSkipPlayer(skill, runtimePlayer);
      }
      if (skipCurrentSkill) {
        continue;
      }

      Adaptation<?> adaptation = binding.adaptation();
      if (adaptation != null && !adaptation.isEnabled()) {
        continue;
      }

      AdaptStatTracker tracker = binding.tracker();
      if (!(statValue >= tracker.getGoal()) || data.isGranted(tracker.getAdvancement())) {
        continue;
      }

      if (player.getAdvancementHandler().grant(tracker.getAdvancement())) {
        skill.xp(runtimePlayer, tracker.getReward(), "milestone:" + tracker.getAdvancement());
      }
    }
  }

  static boolean hasUsePermission(PlayerRef player, Skill<?> skill) {
    if (player == null || skill == null) {
      return false;
    }
    if (AdaptDebugMode.isActive(player)) {
      return true;
    }
    if (player.isOp()) {
      return true;
    }
    String usePermission = USE_PERMISSION_NODES.computeIfAbsent(skill.getId(), SkillRuntimeGuards::useNode);
    PermissionHost permissions = AdaptCore.platform().permissions();
    boolean permissionSet = permissions.isSet(player, usePermission);
    if (AdaptConfig.get().isVerbose()) {
      AdaptLog.verbose("Checking use permission " + usePermission + " for " + player.name()
          + " (set=" + permissionSet + ", value=" + permissions.has(player, usePermission) + ")");
    }
    if (!permissionSet) {
      return true;
    }
    return permissions.has(player, usePermission);
  }

  static boolean shouldSkipPlayer(Skill<?> skill, PlayerRef player) {
    try {
      if (skill == null || player == null) {
        return true;
      }

      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player)) {
        return true;
      }

      return AdaptationGate.shouldSkipPlayer(player, skill, isRuntimePlayer(player));
    } catch (Exception ex) {
      AdaptLog.verbose("Failed shouldSkipPlayer check for " + (player == null ? "null" : player.name())
          + " in skill " + (skill == null ? "null" : SkillHost.lineKey(skill)) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return true;
    }
  }

  static void withPlayer(Skill<?> skill, PlayerRef player, Runnable runnable) {
    try {
      if (skill == null || player == null || runnable == null || !isRuntimeActive(skill)) {
        return;
      }

      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player)) {
        Tasks.runEntity(player, () -> {
          if (isRuntimeActive(skill)) {
            withPlayer(skill, player, runnable);
          }
        });
        return;
      }

      if (!isRuntimeActive(skill) || shouldSkipPlayer(skill, player)) {
        return;
      }

      runnable.run();
    } catch (Exception ex) {
      AdaptLog.verbose("Failed guarded player runnable for skill " + (skill == null ? "null" : SkillHost.lineKey(skill)) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }

  static void withPlayer(Skill<?> skill, PlayerRef player, Cancellable cancellable, Runnable runnable) {
    try {
      if (skill == null || player == null || cancellable == null || runnable == null || !isRuntimeActive(skill)) {
        return;
      }

      if (cancellable.isCancelled()) {
        return;
      }

      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player)) {
        Tasks.runEntity(player, () -> {
          if (isRuntimeActive(skill)) {
            withPlayer(skill, player, cancellable, runnable);
          }
        });
        return;
      }

      if (cancellable.isCancelled() || !isRuntimeActive(skill) || shouldSkipPlayer(skill, player)) {
        return;
      }

      runnable.run();
    } catch (Exception ex) {
      AdaptLog.verbose("Failed guarded cancellable player runnable for skill " + (skill == null ? "null" : SkillHost.lineKey(skill)) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }

  static boolean shouldSkipWorld(Skill<?> skill, WorldRef world) {
    try {
      return AdaptationGate.shouldSkipWorld(world, skill);
    } catch (Exception ex) {
      AdaptLog.verbose("Failed shouldSkipWorld check for skill " + (skill == null ? "null" : SkillHost.lineKey(skill))
          + ": " + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return true;
    }
  }

  static boolean isWorldBlacklisted(PlayerRef player) {
    return AdaptationGate.isWorldBlacklisted(player);
  }

  static boolean isInCreativeOrSpectator(PlayerRef player) {
    return AdaptationGate.isInCreativeOrSpectator(player);
  }

  static boolean canGrantXp(Skill<?> skill, PlayerRef player) {
    return skill != null && skill.isEnabled() && isRuntimePlayer(player);
  }

  static void grantXp(Skill<?> skill, PlayerRef player, Loc location, double xp, String rewardKey, boolean silent, boolean visualBurst) {
    if (!canGrantXp(skill, player) || !isValidXp(xp)) {
      return;
    }
    try {
      XpAward award = XpAward.gameplay(xp, XP.source(skill, rewardKey));
      award = location == null ? award : award.at(location);
      XP.award(player, skill, silent ? award.silent() : award);

      if (visualBurst && location != null && xp > 50 && skill.areParticlesEnabled() && AdaptConfig.get().isUseEnchantmentTableParticleForActiveEffects()) {
        Fx.targeted(player, Particles.ENCHANT, location, Math.min((int) xp / 10, 20), 0.5, 0.5, 0.5, 1);
      }
      if (AdaptConfig.get().isVerbose()) {
        AdaptLog.verbose("Gave " + player.name() + " " + xp + " xp in " + SkillHost.lineKey(skill) + " " + skill.getClass());
      }
    } catch (Exception ex) {
      if (AdaptConfig.get().isVerbose()) {
        AdaptLog.verbose("Skipped XP grant for " + player.name() + " in " + SkillHost.lineKey(skill)
            + " (xp=" + xp + "): " + ex.getClass().getSimpleName());
      }
    }
  }

  static void grantXpSilent(Skill<?> skill, PlayerRef player, double xp, String rewardKey) {
    grantXp(skill, player, null, xp, rewardKey, true, false);
  }

  static void grantKnowledge(Skill<?> skill, PlayerRef player, long knowledge) {
    if (skill == null || !skill.isEnabled() || !isRuntimePlayer(player)) {
      return;
    }
    XP.knowledge(player, skill, knowledge);
  }

  static boolean isRuntimePlayer(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    if (player == null || core == null || core.getAdaptServer() == null) {
      return false;
    }
    AdaptPlayer adaptPlayer = core.getAdaptServer().getOnlineAdaptPlayer(player.uuid());
    return isReadyRuntimePlayer(player, adaptPlayer);
  }

  static boolean isReadyRuntimePlayer(PlayerRef player, AdaptPlayer adaptPlayer) {
    return player != null
        && adaptPlayer != null
        && adaptPlayer.isRuntimeReady()
        && player.equals(adaptPlayer.getPlayer())
        && player.isOnline();
  }

  static String useNode(Id componentName) {
    return UsePermissions.useNode(componentName);
  }

  static boolean isValidXp(double xp) {
    return Double.isFinite(xp) && xp > 0;
  }

  private static boolean isRuntimeActive(Skill<?> skill) {
    return !(skill instanceof TickedObject tickedObject) || tickedObject.isRuntimeRegistered();
  }
}
