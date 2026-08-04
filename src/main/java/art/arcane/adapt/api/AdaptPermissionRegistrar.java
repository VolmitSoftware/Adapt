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

package art.arcane.adapt.api;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.skill.Skill;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdaptPermissionRegistrar {
  static final String USE_WILDCARD = "adapt.use.*";

  private AdaptPermissionRegistrar() {
  }

  public static String useNode(String componentName) {
    return "adapt.use." + componentName.replace("-", "");
  }

  public static int registerAll(PluginManager pm, List<Skill<?>> skills) {
    int registered = 0;
    Map<String, Boolean> rootChildren = new LinkedHashMap<>();
    for (Skill<?> skill : skills) {
      Map<String, Boolean> skillChildren = new LinkedHashMap<>();
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        String node = useNode(adaptation.getName());
        skillChildren.put(node, Boolean.TRUE);
        registered += addIfAbsent(pm, new Permission(node,
            "Allows using the " + adaptation.getName() + " adaptation", PermissionDefault.TRUE));
      }
      String skillNode = useNode(skill.getName());
      rootChildren.put(skillNode, Boolean.TRUE);
      registered += addIfAbsent(pm, new Permission(skillNode,
          "Allows using the " + skill.getName() + " skill and its adaptations", PermissionDefault.TRUE, skillChildren));
    }
    for (MutationType type : MutationType.values()) {
      rootChildren.put(type.permission(), Boolean.TRUE);
      registered += addIfAbsent(pm, new Permission(type.permission(),
          "Allows using the " + type.id() + " mutation", PermissionDefault.TRUE));
    }
    if (!rootChildren.isEmpty()) {
      registered += addIfAbsent(pm, new Permission(USE_WILDCARD,
          "Grants or denies every Adapt skill, adaptation, and mutation use permission",
          PermissionDefault.TRUE, rootChildren));
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

  static int addIfAbsent(PluginManager pm, Permission permission) {
    if (pm.getPermission(permission.getName()) != null) {
      return 0;
    }
    pm.addPermission(permission);
    return 1;
  }
}
