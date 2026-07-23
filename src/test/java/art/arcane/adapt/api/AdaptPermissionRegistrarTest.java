package art.arcane.adapt.api;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.skill.Skill;
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
    assertThat(AdaptPermissionRegistrar.useNode("rift")).isEqualTo("adapt.use.rift");
    assertThat(AdaptPermissionRegistrar.useNode("rift-conduit")).isEqualTo("adapt.use.riftconduit");
    assertThat(AdaptPermissionRegistrar.useNode("agility-armor-up")).isEqualTo("adapt.use.agilityarmorup");
  }

  @Test
  void registerAllRegistersAdaptationsUnderSkillsWithWildcardRoot() {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    Skill<?> skill = mockSkill("rift", "rift-conduit", "rift-blink");

    int registered = AdaptPermissionRegistrar.registerAll(pm, List.of(skill));

    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    int expected = 2 + 1 + MutationType.values().length + 1;
    assertThat(registered).isEqualTo(expected);
    verify(pm, org.mockito.Mockito.times(expected)).addPermission(captor.capture());

    Map<String, Permission> byName = captor.getAllValues().stream()
        .collect(java.util.stream.Collectors.toMap(Permission::getName, p -> p));
    assertThat(byName).containsKeys("adapt.use.rift", "adapt.use.riftconduit", "adapt.use.riftblink", "adapt.use.*");
    assertThat(byName.get("adapt.use.riftconduit").getDefault()).isEqualTo(PermissionDefault.TRUE);
    assertThat(byName.get("adapt.use.rift").getChildren())
        .containsOnlyKeys("adapt.use.riftconduit", "adapt.use.riftblink");
    assertThat(byName.get("adapt.use.*").getChildren())
        .containsKey("adapt.use.rift")
        .containsKey(MutationType.values()[0].permission())
        .doesNotContainKey("adapt.use.riftconduit");
  }

  @Test
  void registerAllSkipsNodesAlreadyRegistered() {
    PluginManager pm = mock(PluginManager.class);
    when(pm.getPermission(anyString())).thenReturn(null);
    when(pm.getPermission("adapt.use.riftconduit")).thenReturn(new Permission("adapt.use.riftconduit"));
    Skill<?> skill = mockSkill("rift", "rift-conduit");

    int registered = AdaptPermissionRegistrar.registerAll(pm, List.of(skill));

    assertThat(registered).isEqualTo(1 + MutationType.values().length + 1);
    ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
    verify(pm, org.mockito.Mockito.times(registered)).addPermission(captor.capture());
    assertThat(captor.getAllValues()).noneMatch(p -> p.getName().equals("adapt.use.riftconduit"));
    assertThat(captor.getAllValues()).anyMatch(p -> p.getName().equals("adapt.use.rift"));
  }

  private static Skill<?> mockSkill(String name, String... adaptationNames) {
    Skill<?> skill = mock(Skill.class);
    when(skill.getName()).thenReturn(name);
    KList<Adaptation<?>> adaptations = new KList<>();
    for (String adaptationName : adaptationNames) {
      Adaptation<?> adaptation = mock(Adaptation.class);
      when(adaptation.getName()).thenReturn(adaptationName);
      adaptations.add(adaptation);
    }
    org.mockito.Mockito.doReturn(adaptations).when(skill).getAdaptations();
    return skill;
  }
}
