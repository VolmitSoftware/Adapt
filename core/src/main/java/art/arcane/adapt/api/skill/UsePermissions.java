package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;

import java.util.List;

/**
 * Registers the default-granted use permissions of every skill and adaptation, plus the {@code adapt.use.*}
 * wildcard, through the platform permission host. Without it a platform that defaults unknown nodes to operators
 * would lock ordinary players out of every skill. The Bukkit plugin registers the same nodes with their child
 * hierarchy itself.
 */
public final class UsePermissions {
  public static final String USE_WILDCARD = "adapt.use.*";

  private UsePermissions() {
  }

  public static String useNode(String componentName) {
    return "adapt.use." + componentName.replace("-", "");
  }

  public static int registerDefaults(PermissionHost permissions, List<Skill<?>> skills) {
    int registered = 0;
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : Skill.ownedAdaptations(skill)) {
        permissions.register(useNode(adaptation.getName()),
            "Allows using the " + adaptation.getName() + " adaptation", PermissionDefault.TRUE);
        registered++;
      }
      permissions.register(useNode(skill.getName()),
          "Allows using the " + skill.getName() + " skill and its adaptations", PermissionDefault.TRUE);
      registered++;
    }
    if (!skills.isEmpty()) {
      permissions.register(USE_WILDCARD, "Grants or denies every Adapt skill and adaptation use permission",
          PermissionDefault.TRUE);
      registered++;
    }
    return registered;
  }
}
