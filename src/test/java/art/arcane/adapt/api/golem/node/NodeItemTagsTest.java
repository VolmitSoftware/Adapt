package art.arcane.adapt.api.golem.node;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class NodeItemTagsTest {
  private static final String ITEMS = "items";
  private static final NamespacedKey LOGS = NamespacedKey.minecraft("logs");
  private static final NamespacedKey MISSING = NamespacedKey.minecraft("missing");

  private MockedStatic<Bukkit> bukkit;

  @BeforeEach
  void setUp() {
    NodeItemTags.shared().clear();
    Tag<Material> logs = new FakeItemTag(LOGS, Set.of(Material.OAK_LOG));
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getTag(eq(ITEMS), eq(LOGS), eq(Material.class))).thenReturn(logs);
  }

  @AfterEach
  void tearDown() {
    bukkit.close();
    NodeItemTags.shared().clear();
  }

  @Test
  void knownAndMissingTagsAreEachLookedUpOnce() {
    NodeItemTags tags = NodeItemTags.shared();

    assertThat(tags.tagged("#minecraft:logs", Material.OAK_LOG)).isTrue();
    assertThat(tags.tagged("#minecraft:logs", Material.STONE)).isFalse();
    assertThat(tags.exists("#minecraft:missing")).isFalse();
    assertThat(tags.tagged("#minecraft:missing", Material.STONE)).isFalse();

    bukkit.verify(() -> Bukkit.getTag(eq(ITEMS), eq(LOGS), eq(Material.class)), times(1));
    bukkit.verify(() -> Bukkit.getTag(eq(ITEMS), eq(MISSING), eq(Material.class)), times(1));
  }

  @Test
  void aResourceReloadClearsTheCache() {
    NodeItemTags tags = NodeItemTags.shared();
    tags.exists("#minecraft:missing");

    new NodeItemTags.ReloadListener().on(mock(ServerResourcesReloadedEvent.class));
    tags.exists("#minecraft:missing");

    bukkit.verify(() -> Bukkit.getTag(eq(ITEMS), eq(MISSING), eq(Material.class)), times(2));
  }

  private record FakeItemTag(NamespacedKey key, Set<Material> values) implements Tag<Material> {
    @Override
    public boolean isTagged(Material item) {
      return values.contains(item);
    }

    @Override
    public Set<Material> getValues() {
      return values;
    }

    @Override
    public NamespacedKey getKey() {
      return key;
    }
  }
}
