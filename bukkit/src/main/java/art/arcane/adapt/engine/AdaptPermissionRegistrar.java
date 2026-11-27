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

package art.arcane.adapt.engine;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.engine.skill.UsePermissions;
import art.arcane.adapt.AdaptConfig;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class AdaptPermissionRegistrar {
  static final String USE_WILDCARD = "adapt.use.*";
  private static final Set<String> PUBLISHED = new HashSet<>();

  private AdaptPermissionRegistrar() {
  }

  public static String useNode(Id componentName) {
    return UsePermissions.useNode(componentName);
  }

  public static int registerAll(PluginManager pm, List<Skill<?>> skills) {
    int registered = 0;
    Map<String, Boolean> rootChildren = new LinkedHashMap<>();
    for (Skill<?> skill : skills) {
      Map<String, Boolean> skillChildren = new LinkedHashMap<>();
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        String node = useNode(adaptation.getId());
        skillChildren.put(node, Boolean.TRUE);
        registered += addIfAbsent(pm, new Permission(node,
            "Allows using the " + adaptation.getId() + " adaptation", PermissionDefault.TRUE));
      }
      String skillNode = useNode(skill.getId());
      rootChildren.put(skillNode, Boolean.TRUE);
      registered += addOrReplaceParent(pm, new Permission(skillNode,
          "Allows using the " + skill.getId() + " skill and its adaptations", PermissionDefault.TRUE, skillChildren));
    }
    if (!rootChildren.isEmpty()) {
      registered += addOrReplaceParent(pm, new Permission(USE_WILDCARD,
          "Grants or denies every Adapt skill and adaptation use permission",
          PermissionDefault.TRUE, rootChildren));
    }
    Set<String> current = new HashSet<>(rootChildren.keySet());
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        current.add(useNode(adaptation.getId()));
      }
    }
    synchronized (PUBLISHED) {
      for (String stale : List.copyOf(PUBLISHED)) {
        if (!current.contains(stale) && pm.getPermission(stale) != null) {
          pm.removePermission(stale);
        }
      }
      PUBLISHED.clear();
      PUBLISHED.addAll(current);
    }
    return registered;
  }

  public static int registerXpMultiplierNodes(PluginManager pm, AdaptConfig.PermissionXpMultipliers settings) {
    if (settings == null || !settings.isEnabled()) {
      return 0;
    }

    Map<String, Double> nodes = settings.getMultipliers();
    if (nodes == null || nodes.isEmpty()) {
      return 0;
    }

    int registered = 0;
    for (Map.Entry<String, Double> entry : nodes.entrySet()) {
      String node = entry.getKey();
      Double value = entry.getValue();
      if (node == null || node.isBlank() || value == null || value <= 0D) {
        continue;
      }

      registered += addIfAbsent(pm, new Permission(node,
          "Grants the configured Adapt XP multiplier of " + value, PermissionDefault.FALSE));
    }
    return registered;
  }

  /** Adds a parent node, replacing a registered one whose children no longer match the catalog. */
  static int addOrReplaceParent(PluginManager pm, Permission permission) {
    Permission existing = pm.getPermission(permission.getName());
    if (existing != null) {
      if (existing.getChildren().equals(permission.getChildren())) {
        return 0;
      }
      pm.removePermission(existing);
    }
    pm.addPermission(permission);
    return 1;
  }

  static int addIfAbsent(PluginManager pm, Permission permission) {
    if (pm.getPermission(permission.getName()) != null) {
      return 0;
    }
    pm.addPermission(permission);
    return 1;
  }
}
