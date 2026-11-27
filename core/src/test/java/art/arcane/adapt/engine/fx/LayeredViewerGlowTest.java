package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.viewer.GlowLayer;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.viewer.ViewerHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayeredViewerGlowTest extends AdaptTestBase {
  private static final GlowLayer GOLEMANCY = new GlowLayer(Id.adapt("glow/golemancy"), 50);
  private static final GlowLayer GOLEMANCY_LOCATE = new GlowLayer(Id.adapt("glow/golemancy_locate"), 800);
  private final LayeredViewerGlow glow = LayeredViewerGlow.INSTANCE;
  private PlayerRef viewer;
  private EntityRef target;
  private ViewerHost viewers;

  @BeforeEach
  void setUp() {
    viewers = core.getPlatform().viewers();
    viewer = mock(PlayerRef.class);
    when(viewer.uuid()).thenReturn(UUID.randomUUID());
    target = mock(EntityRef.class);
    when(target.uuid()).thenReturn(UUID.randomUUID());
    when(target.entityId()).thenReturn(77);
  }

  @AfterEach
  void forget() {
    glow.discardViewer(viewer.uuid());
  }

  @Test
  void theHighestLayerDecidesTheColourAndRemovingItRevealsTheNext() {
    assertThat(glow.set(GOLEMANCY, target, viewer, GlowColor.RED)).isTrue();
    assertThat(glow.set(GOLEMANCY_LOCATE, target, viewer, GlowColor.AQUA)).isTrue();
    assertThat(glow.set(GOLEMANCY, target, viewer, GlowColor.BLUE)).isTrue();

    verify(viewers).setGlow(viewer, target, GlowColor.RED);
    verify(viewers).setGlow(viewer, target, GlowColor.AQUA);
    verify(viewers, never()).setGlow(viewer, target, GlowColor.BLUE);

    assertThat(glow.unset(GOLEMANCY_LOCATE, target.uuid(), 77, viewer)).isTrue();
    verify(viewers).setGlow(viewer, target, GlowColor.BLUE);

    assertThat(glow.unset(GOLEMANCY, target, viewer)).isTrue();
    verify(viewers).clearGlow(viewer, 77);
  }

  @Test
  void aStaleRuntimeIdIsIgnoredWhenUnsetting() {
    glow.set(GOLEMANCY, target, viewer, GlowColor.RED);

    assertThat(glow.unset(GOLEMANCY, target.uuid(), 12, viewer)).isTrue();

    verify(viewers, never()).clearGlow(any(PlayerRef.class), anyInt());
  }

  @Test
  void aRespawnedEntityClearsTheOldRuntimeIdBeforeGlowingAgain() {
    glow.set(GOLEMANCY, target, viewer, GlowColor.RED);
    when(target.entityId()).thenReturn(78);

    glow.set(GOLEMANCY, target, viewer, GlowColor.RED);

    verify(viewers).clearGlow(viewer, 77);
    verify(viewers, times(2)).setGlow(viewer, target, GlowColor.RED);
  }
}
