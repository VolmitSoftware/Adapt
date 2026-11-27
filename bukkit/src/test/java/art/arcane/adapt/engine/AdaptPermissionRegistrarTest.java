package art.arcane.adapt.engine;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptPermissionRegistrarTest {
  @Test
  void useNodeMatchesTheRuntimeGuardDerivation() {
    assertThat(AdaptPermissionRegistrar.useNode(Id.adapt("rift"))).isEqualTo("adapt.use.adapt.rift");
    assertThat(AdaptPermissionRegistrar.useNode(Id.adapt("rift/conduit"))).isEqualTo("adapt.use.adapt.rift.conduit");
    assertThat(AdaptPermissionRegistrar.useNode(Id.adapt("agility/armor_up"))).isEqualTo("adapt.use.adapt.agility.armor_up");
    assertThat(AdaptPermissionRegistrar.useNode(Id.of("example", "frost/nova"))).isEqualTo("adapt.use.example.frost.nova");
  }

  @Test
  void registerAllRegistersAdaptationsUnderSkillsWithWildcardRoot() {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    Skill<?> skill = mockSkill("rift", "adapt:rift/conduit", "adapt:rift/blink");

    int registered = AdaptPermissionRegistrar.registerAll(pm, List.of(skill));

    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    int expected = 4;
    assertThat(registered).isEqualTo(expected);
    verify(pm, org.mockito.Mockito.times(expected)).addPermission(captor.capture());

    Map<String, Permission> byName = captor.getAllValues().stream()
        .collect(java.util.stream.Collectors.toMap(Permission::getName, p -> p));
    assertThat(byName).containsKeys("adapt.use.adapt.rift", "adapt.use.adapt.rift.conduit", "adapt.use.adapt.rift.blink", "adapt.use.*");
    assertThat(byName.get("adapt.use.adapt.rift.conduit").getDefault()).isEqualTo(PermissionDefault.TRUE);
    assertThat(byName.get("adapt.use.adapt.rift").getChildren())
        .containsOnlyKeys("adapt.use.adapt.rift.conduit", "adapt.use.adapt.rift.blink");
    assertThat(byName.get("adapt.use.*").getChildren())
        .containsKey("adapt.use.adapt.rift")
        .doesNotContainKey("adapt.use.adapt.rift.conduit");
  }

  @Test
  void registerAllSkipsNodesAlreadyRegistered() {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    when(pm.getPermission("adapt.use.adapt.rift.conduit")).thenReturn(new Permission("adapt.use.adapt.rift.conduit"));
    Skill<?> skill = mockSkill("rift", "adapt:rift/conduit");

    int registered = AdaptPermissionRegistrar.registerAll(pm, List.of(skill));

    assertThat(registered).isEqualTo(2);
    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    verify(pm, org.mockito.Mockito.times(registered)).addPermission(captor.capture());
    assertThat(captor.getAllValues()).noneMatch(p -> p.getName().equals("adapt.use.adapt.rift.conduit"));
    assertThat(captor.getAllValues()).anyMatch(p -> p.getName().equals("adapt.use.adapt.rift"));
  }

  @Test
  void aSkillWhoseAdaptationsChangedGetsItsParentNodeReplaced() {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    Permission stale = new Permission("adapt.use.adapt.rift", PermissionDefault.TRUE,
        Map.of("adapt.use.adapt.rift.conduit", Boolean.TRUE));
    when(pm.getPermission("adapt.use.adapt.rift")).thenReturn(stale);
    when(pm.getPermission("adapt.use.adapt.rift.conduit")).thenReturn(new Permission("adapt.use.adapt.rift.conduit"));
    Skill<?> skill = mockSkill("rift", "adapt:rift/conduit", "adapt:rift/blink");

    AdaptPermissionRegistrar.registerAll(pm, List.of(skill));

    verify(pm).removePermission(stale);
    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    verify(pm, org.mockito.Mockito.atLeastOnce()).addPermission(captor.capture());
    assertThat(captor.getAllValues()).filteredOn((Permission p) -> p.getName().equals("adapt.use.adapt.rift")).singleElement()
        .satisfies((Permission p) -> assertThat(p.getChildren())
            .containsOnlyKeys("adapt.use.adapt.rift.conduit", "adapt.use.adapt.rift.blink"));
  }

  @Test
  void xpMultiplierNodesRegisterAsDeniedByDefaultWhenEnabled() throws Exception {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    AdaptConfig.PermissionXpMultipliers settings = settings(true, Map.of(
        "adapt.xpmultiplier.vip", 1.5D,
        "adapt.xpmultiplier.mvp", 2D
    ));

    int registered = AdaptPermissionRegistrar.registerXpMultiplierNodes(pm, settings);

    assertThat(registered).isEqualTo(2);
    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    verify(pm, org.mockito.Mockito.times(2)).addPermission(captor.capture());
    Map<String, Permission> byName = captor.getAllValues().stream()
        .collect(java.util.stream.Collectors.toMap(Permission::getName, p -> p));
    assertThat(byName).containsOnlyKeys("adapt.xpmultiplier.vip", "adapt.xpmultiplier.mvp");
    assertThat(byName.get("adapt.xpmultiplier.vip").getDefault()).isEqualTo(PermissionDefault.FALSE);
  }

  @Test
  void xpMultiplierNodesAreNotRegisteredWhileDisabled() throws Exception {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);

    int registered = AdaptPermissionRegistrar.registerXpMultiplierNodes(pm,
        settings(false, Map.of("adapt.xpmultiplier.vip", 1.5D)));

    assertThat(registered).isZero();
    verify(pm, org.mockito.Mockito.never()).addPermission(org.mockito.ArgumentMatchers.any(Permission.class));
  }

  @Test
  void xpMultiplierNodesSkipUnusableEntriesAndExistingNodes() throws Exception {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    when(pm.getPermission("adapt.xpmultiplier.vip")).thenReturn(new Permission("adapt.xpmultiplier.vip"));
    Map<String, Double> nodes = new java.util.LinkedHashMap<>();
    nodes.put("adapt.xpmultiplier.vip", 1.5D);
    nodes.put("adapt.xpmultiplier.broken", 0D);
    nodes.put("  ", 3D);
    nodes.put("adapt.xpmultiplier.mvp", 2D);

    int registered = AdaptPermissionRegistrar.registerXpMultiplierNodes(pm, settings(true, nodes));

    assertThat(registered).isEqualTo(1);
    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    verify(pm).addPermission(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("adapt.xpmultiplier.mvp");
  }

  @Test
  void xpMultiplierNodeRegistrationToleratesMissingSettings() {
    PluginManager pm = mock(PluginManager.class);

    assertThat(AdaptPermissionRegistrar.registerXpMultiplierNodes(pm, null)).isZero();
  }

  private static AdaptConfig.PermissionXpMultipliers settings(boolean enabled, Map<String, Double> nodes) throws Exception {
    AdaptConfig.PermissionXpMultipliers settings = new AdaptConfig().getPermissionXpMultipliers();
    java.lang.reflect.Field enabledField = AdaptConfig.PermissionXpMultipliers.class.getDeclaredField("enabled");
    enabledField.setAccessible(true);
    enabledField.set(settings, enabled);
    settings.getMultipliers().putAll(nodes);
    return settings;
  }

  private static Skill<?> mockSkill(String name, String... adaptationNames) {
    Skill<?> skill = mock(Skill.class);
    when(skill.getId()).thenReturn(Id.parseAdapt(name));
    KList<Adaptation<?>> adaptations = new KList<>();
    for (String adaptationName : adaptationNames) {
      Adaptation<?> adaptation = mock(Adaptation.class);
      when(adaptation.getId()).thenReturn(Id.parseAdapt(adaptationName));
      adaptations.add(adaptation);
    }
    org.mockito.Mockito.doReturn(adaptations).when(skill).getAdaptations();
    return skill;
  }
}
