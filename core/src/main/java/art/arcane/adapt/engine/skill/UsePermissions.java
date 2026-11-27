package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.api.internal.HostPermissions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers the default-granted use permissions of every skill and adaptation, plus the {@code adapt.use.*}
 * wildcard, through the platform permission host. Without it a platform that defaults unknown nodes to operators
 * would lock ordinary players out of every skill. The Bukkit plugin registers the same nodes with their child
 * hierarchy itself.
 */
public final class UsePermissions {
  public static final String USE_WILDCARD = "adapt.use.*";
  private static final Set<String> PUBLISHED = new HashSet<>();

  private UsePermissions() {
  }

  /** The use permission of a skill or adaptation: {@code adapt.use.<namespace>.<path with / as .>}. */
  public static String useNode(Id id) {
    return "adapt.use." + id.namespace() + "." + id.path().replace('/', '.');
  }

  /**
   * Registers the nodes of {@code skills} and removes the nodes this method registered earlier for skills and
   * adaptations that are no longer listed, such as those of an uninstalled addon.
   */
  public static int registerDefaults(HostPermissions permissions, List<Skill<?>> skills) {
    int registered = 0;
    Set<String> current = new HashSet<>();
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        String node = useNode(adaptation.getId());
        current.add(node);
        permissions.add(node, "Allows using the " + adaptation.getId() + " adaptation", PermissionDefault.TRUE);
        registered++;
      }
      String node = useNode(skill.getId());
      current.add(node);
      permissions.add(node, "Allows using the " + skill.getId() + " skill and its adaptations",
          PermissionDefault.TRUE);
      registered++;
    }
    synchronized (PUBLISHED) {
      for (String stale : List.copyOf(PUBLISHED)) {
        if (!current.contains(stale)) {
          permissions.unregister(stale);
          PUBLISHED.remove(stale);
        }
      }
      PUBLISHED.addAll(current);
    }
    if (!skills.isEmpty()) {
      permissions.add(USE_WILDCARD, "Grants or denies every Adapt skill and adaptation use permission",
          PermissionDefault.TRUE);
      registered++;
    }
    return registered;
  }
}
