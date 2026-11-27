package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.engine.skill.SkillRegistryPort;
import art.arcane.adapt.engine.skill.UsePermissions;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Removing an addon also removes the hooks and permission nodes that belong to it. */
class AddonHooksTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
    for (Protector protector : Engine.protectors().protectors(HostKeys.key())) {
      AdaptApi.protection().unregister(protector);
    }
  }

  @Test
  void uninstallDropsTheProtectorsAndPoliciesItsClassLoaderDefined() throws Exception {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    Protector engineSide = new Allowing("engine-side");
    AdaptApi.protection().register(engineSide);
    ClassLoader own = new ChildFirst(AddonHooksTest.class.getClassLoader());
    AdaptAddon addon = (AdaptAddon) own.loadClass(HookedAddon.class.getName()).getConstructor().newInstance();

    AddonHandle handle = AdaptApi.addons().install(addon);

    assertThat(AdaptApi.protection().names()).contains("hooked", "engine-side");
    assertThat(Engine.abilities().policies(HostKeys.key())).anyMatch(policy -> policy.getClass().getClassLoader() == own);

    handle.uninstall();

    assertThat(AdaptApi.protection().names()).containsExactly("engine-side");
    assertThat(Engine.abilities().policies(HostKeys.key())).noneMatch(policy -> policy.getClass().getClassLoader() == own);
  }

  @Test
  void uninstallRemovesTheUsePermissionsOfTheAddonsContent() {
    TestPlatform platform = TestPlatform.builder(data).build();
    AdaptPorts.install(SkillRegistryPort.class,
        (long revision, List<art.arcane.adapt.api.content.Skill<?>> skills) ->
            UsePermissions.registerDefaults(platform.platform().permissions(), skills));
    adapt = TestAdapt.start(platform);
    TestPlatform.Permissions permissions = (TestPlatform.Permissions) platform.platform().permissions();

    AddonHandle handle = AdaptApi.addons().install(new AddonLifecycleTest.GizmoAddon(false));

    assertThat(permissions.registered()).contains("adapt.use.gizmo.gizmos", "adapt.use.gizmo.gizmos.cog");

    handle.uninstall();

    assertThat(permissions.registered()).doesNotContain("adapt.use.gizmo.gizmos", "adapt.use.gizmo.gizmos.cog");
  }

  public static final class HookedAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "hooked";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      AdaptApi.protection().register(new Allowing("hooked"));
      AdaptApi.abilities().registerPolicy(new Permissive());
    }
  }

  public static final class Permissive implements AbilityUsePolicy {
    @Override
    public AbilityUseDecision evaluate(AbilityContext context) {
      return AbilityUseDecision.allow();
    }
  }

  public static final class Allowing implements Protector {
    private final String name;

    public Allowing(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean enabledByDefault() {
      return true;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim, Id adaptation) {
      return true;
    }
  }

  /** Defines this test's nested classes itself, as a plugin's own class loader would. */
  private static final class ChildFirst extends ClassLoader {
    private ChildFirst(ClassLoader parent) {
      super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith(AddonHooksTest.class.getName() + "$") || name.endsWith("$ChildFirst")) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) {
              throw new ClassNotFoundException(name);
            }
            byte[] bytes = stream.readAllBytes();
            loaded = defineClass(name, bytes, 0, bytes.length);
          } catch (IOException failure) {
            throw new ClassNotFoundException(name, failure);
          }
        }
        return loaded;
      }
    }
  }
}
