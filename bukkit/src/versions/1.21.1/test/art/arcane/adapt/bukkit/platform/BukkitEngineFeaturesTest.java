package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.EngineFeature;
import org.bukkit.Bukkit;
import org.bukkit.FeatureFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitEngineFeaturesTest {
  @Test
  void bundlesNeedTheBundleFeatureFlagOfTheMainWorld() {
    World plain = world(flag("vanilla"));
    World experimental = world(flag("vanilla"), flag("bundle"));
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(plain, experimental));
      assertFalse(BukkitEngineFeatures.supports(EngineFeature.BUNDLES));
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(experimental, plain));
      assertTrue(BukkitEngineFeatures.supports(EngineFeature.BUNDLES));
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
      assertFalse(BukkitEngineFeatures.supports(EngineFeature.BUNDLES));
    }
  }

  @Test
  void serversBefore1_21_2NeverReceiveThePlayerInputPacket() {
    assertFalse(BukkitEngineFeatures.supports(EngineFeature.PLAYER_INPUT));
  }

  private static FeatureFlag flag(String name) {
    return new Flag(NamespacedKey.minecraft(name));
  }

  private record Flag(NamespacedKey getKey) implements FeatureFlag {
  }

  private static World world(FeatureFlag... flags) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getFeatureFlags()).thenReturn(Set.of(flags));
    return world;
  }
}
