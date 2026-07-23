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
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.api.telemetry.AdaptRuntimeTelemetry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpNovelty;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SkillRuntimeGuards {
  private static final Map<String, String> USE_PERMISSION_NODES = new ConcurrentHashMap<>();

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

    Player runtimePlayer = player.getPlayer();
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

  static boolean hasUsePermission(Player player, Skill<?> skill) {
    if (player == null || skill == null) {
      return false;
    }
    if (AdaptDebugMode.isActive(player)) {
      return true;
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
      if (skill == null || player == null || runnable == null || !isRuntimeActive(skill)) {
        return;
      }

      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
        J.runEntity(player, () -> {
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
      Adapt.verbose("Failed guarded player runnable for skill " + (skill == null ? "null" : skill.getName()) + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }

  static void withPlayer(Skill<?> skill, Player player, Cancellable cancellable, Runnable runnable) {
    try {
      if (skill == null || player == null || cancellable == null || runnable == null || !isRuntimeActive(skill)) {
        return;
      }

      if (cancellable.isCancelled()) {
        return;
      }

      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
        J.runEntity(player, () -> {
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
    if (!canGrantXp(skill, player) || !isValidXp(xp)) {
      return;
    }
    try {
      xp *= XpNovelty.noveltyMultiplier(player, location, rewardKey);
      if (!isValidXp(xp)) {
        return;
      }
      if (silent) {
        XP.xpSilent(player, skill, xp, rewardKey);
      } else {
        XP.xp(player, skill, xp, rewardKey);
      }
      AdaptRuntimeTelemetry.recordXpPayout(System.currentTimeMillis(), xp);

      if (visualBurst && location != null && xp > 50 && skill.areParticlesEnabled() && AdaptConfig.get().isUseEnchantmentTableParticleForActiveEffects()) {
        Fx.targeted(player, Particles.ENCHANTMENT_TABLE, location, Math.min((int) xp / 10, 20), 0.5, 0.5, 0.5, 1);
      }
      if (AdaptConfig.get().isVerbose()) {
        Adapt.verbose("Gave " + player.getName() + " " + xp + " xp in " + skill.getName() + " " + skill.getClass());
      }
    } catch (Exception ex) {
      Adapt.verbose("Failed to give xp to " + player.getName() + " for " + skill.getName() + " (" + xp + ")");
    }
  }

  static void grantXpSilent(Skill<?> skill, Player player, double xp, String rewardKey) {
    if (!canGrantXp(skill, player) || !isValidXp(xp)) {
      return;
    }
    try {
      XP.xpSilent(player, skill, xp, rewardKey);
      AdaptRuntimeTelemetry.recordXpPayout(System.currentTimeMillis(), xp);
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

  static boolean isValidXp(double xp) {
    return Double.isFinite(xp) && xp > 0;
  }

  private static boolean isRuntimeActive(Skill<?> skill) {
    return !(skill instanceof TickedObject tickedObject) || tickedObject.isRuntimeRegistered();
  }
}
