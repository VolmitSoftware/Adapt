package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.viewer.GlowLayer;
import art.arcane.adapt.engine.fx.LayeredViewerGlow;
import art.arcane.adapt.engine.fx.PrivateBlockDisplays;
import art.arcane.adapt.engine.fx.ViewerDisplayPort;
import art.arcane.adapt.engine.fx.ViewerGlowPort;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class ViewerFacesTest {
  private static final GlowLayer GOLEMANCY_LOCATE = new GlowLayer(Id.adapt("glow/golemancy_locate"), 800);
  private static final GlowLayer HUNTER = new GlowLayer(Id.adapt("glow/hunter"), 75);
  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    AdaptPorts.uninstall(ViewerGlowPort.class);
    AdaptPorts.uninstall(ViewerDisplayPort.class);
    if (adapt != null) {
      adapt.close();
    }
  }

  @Test
  void withoutAnInstalledPortTheFacesAreTheEngineFallbacks() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());

    assertThat(art.arcane.adapt.api.viewer.ViewerGlowPort.current()).isSameAs(LayeredViewerGlow.INSTANCE);
    assertThat(art.arcane.adapt.api.viewer.ViewerDisplayPort.current()).isSameAs(PrivateBlockDisplays.INSTANCE);
  }

  @Test
  void theGlowFaceReachesTheInstalledPortAndOldLayersBecomeGlowLayers() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    RecordingGlow glow = new RecordingGlow();
    AdaptPorts.install(ViewerGlowPort.class, glow);
    GlowLayer marked = new GlowLayer(Id.of("demo", "marked"), 150);
    EntityRef target = mock(EntityRef.class);
    PlayerRef viewer = mock(PlayerRef.class);

    art.arcane.adapt.api.viewer.ViewerGlowPort.current().set(marked, target, viewer, GlowColor.RED);
    glow.set(HUNTER, target, viewer, GlowColor.RED);
    glow.clearLayer(GOLEMANCY_LOCATE);

    assertThat(glow.layers).containsExactly(marked, new GlowLayer(Id.adapt("glow/hunter"), 75),
        new GlowLayer(Id.adapt("glow/golemancy_locate"), 800));
  }

  @Test
  void theDisplayFaceReachesTheInstalledPort() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    ViewerDisplayPort displays = mock(ViewerDisplayPort.class);
    AdaptPorts.install(ViewerDisplayPort.class, displays);
    UUID viewer = UUID.randomUUID();

    art.arcane.adapt.api.viewer.ViewerDisplayPort.current().clearViewer("demo:outline", viewer);

    verify(displays).clearViewer("demo:outline", viewer);
  }

  @Test
  void anAmbientLineKeyedByPurposeReachesTheHud() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Viewer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform);
    try (MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      HudStatus.ambient(player, "demo:stance", "Braced");
      HudStatus.clearAmbient(player, "demo:stance");

      hud.verify(() -> AdaptHud.ambientStatus(player, "demo:stance", "Braced"));
      hud.verify(() -> AdaptHud.clearAmbientStatus(player, "demo:stance"));
    }
  }

  private static final class RecordingGlow implements ViewerGlowPort {
    private final List<GlowLayer> layers = new ArrayList<>();

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public boolean set(GlowLayer layer, EntityRef entity, PlayerRef viewer, GlowColor color) {
      layers.add(layer);
      return true;
    }

    @Override
    public boolean unset(GlowLayer layer, EntityRef entity, PlayerRef viewer) {
      layers.add(layer);
      return true;
    }

    @Override
    public boolean unset(GlowLayer layer, UUID targetId, int runtimeEntityId, PlayerRef viewer) {
      layers.add(layer);
      return true;
    }

    @Override
    public void clearLayer(GlowLayer layer) {
      layers.add(layer);
    }

    @Override
    public void discardViewer(UUID viewerId) {
    }
  }
}
