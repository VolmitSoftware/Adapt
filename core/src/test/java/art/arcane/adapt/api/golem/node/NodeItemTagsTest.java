package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeItemTagsTest {
  private static final Id LOGS = Id.minecraft("logs");
  private static final Id MISSING = Id.minecraft("missing");
  private static final Id OAK_LOG = Id.minecraft("oak_log");
  private static final Id STONE = Id.minecraft("stone");

  private RegistryLookup registries;
  private AdaptCore previous;

  @BeforeEach
  void setUp() {
    NodeItemTags.shared().clear();
    previous = AdaptCore.current();
    AdaptPlatform platform = TestCores.mockPlatform();
    registries = mock(RegistryLookup.class);
    when(platform.host().registries()).thenReturn(registries);
    lenient().when(registries.tagValues(eq(RegistryKind.ITEM), eq(LOGS))).thenReturn(Set.of(OAK_LOG));
    lenient().when(registries.tagValues(eq(RegistryKind.ITEM), eq(MISSING))).thenReturn(Set.of());
    TestCores.install(platform);
  }

  @AfterEach
  void tearDown() {
    TestCores.use(previous);
    NodeItemTags.shared().clear();
  }

  @Test
  void matchingLooksUpKnownAndMissingTagsOnce() {
    NodeItemTags tags = NodeItemTags.shared();

    assertThat(tags.tagged("#minecraft:logs", OAK_LOG)).isTrue();
    assertThat(tags.tagged("#minecraft:logs", STONE)).isFalse();
    assertThat(tags.exists("#minecraft:logs")).isTrue();
    assertThat(tags.tagged("#minecraft:missing", STONE)).isFalse();
    assertThat(tags.tagged("#minecraft:missing", STONE)).isFalse();

    verify(registries, times(1)).tagValues(RegistryKind.ITEM, LOGS);
    verify(registries, times(1)).tagValues(RegistryKind.ITEM, MISSING);
  }

  @Test
  void validatingNeverCachesAMissingTag() {
    NodeItemTags tags = NodeItemTags.shared();

    assertThat(tags.exists("#minecraft:missing")).isFalse();
    assertThat(tags.exists("#minecraft:missing")).isFalse();
    assertThat(tags.exists("#minecraft:logs")).isTrue();
    assertThat(tags.tagged("#minecraft:logs", OAK_LOG)).isTrue();

    verify(registries, times(2)).tagValues(RegistryKind.ITEM, MISSING);
    verify(registries, times(1)).tagValues(RegistryKind.ITEM, LOGS);
  }

  @Test
  void aResourceReloadClearsTheCache() {
    NodeItemTags tags = NodeItemTags.shared();
    tags.exists("#minecraft:logs");

    new NodeItemTags.ReloadListener().on(new ResourcesReloadedEvent(ResourcesReloadedEvent.Cause.COMMAND));
    tags.exists("#minecraft:logs");

    verify(registries, times(2)).tagValues(RegistryKind.ITEM, LOGS);
  }
}
