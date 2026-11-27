package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionRegistration;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitPermissionHostTest {
  private final Map<String, Permission> nodes = new HashMap<>();
  private BukkitPermissionHost host;

  @BeforeEach
  void setUp() {
    PluginManager manager = mock(PluginManager.class);
    when(manager.getPermission(anyString())).thenAnswer(invocation -> nodes.get(invocation.<String>getArgument(0)));
    doAnswer(invocation -> {
      Permission permission = invocation.getArgument(0);
      nodes.put(permission.getName(), permission);
      return null;
    }).when(manager).addPermission(any(Permission.class));
    doAnswer(invocation -> nodes.remove(invocation.<String>getArgument(0))).when(manager).removePermission(anyString());
    host = new BukkitPermissionHost(() -> manager);
  }

  @Test
  void unregisterLeavesANodeAnotherPluginRegistered() {
    nodes.put("other.plugin.node", new Permission("other.plugin.node"));

    host.unregister("other.plugin.node");

    assertThat(nodes).containsKey("other.plugin.node");
  }

  @Test
  void aRegistrationOfAnExistingNodeRemovesNothing() {
    nodes.put("other.plugin.node", new Permission("other.plugin.node"));

    PermissionRegistration squat = host.register("other.plugin.node", "taken", PermissionDefault.TRUE);
    PermissionRegistration own = host.register("example.own", "mine", PermissionDefault.TRUE);
    assertThat(nodes).containsKey("example.own");
    squat.remove();
    own.remove();

    assertThat(nodes).containsKey("other.plugin.node").doesNotContainKey("example.own");
  }
}
