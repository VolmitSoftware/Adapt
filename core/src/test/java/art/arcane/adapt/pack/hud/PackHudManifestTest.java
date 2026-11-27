package art.arcane.adapt.pack.hud;

import art.arcane.adapt.pack.link.PackAudience;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PackHudManifestTest {
  private static final String HUD = "\"kits\":[{\"kit\":\"adapt:herbalism\",\"tooltipStyle\":\"adapt:herbalism\","
      + "\"toastSprite\":\"adapt:gui/skill/herbalism\",\"toastModel\":\"adapt:adapt_ui/toast/herbalism\"}],"
      + "\"hud\":{\"font\":\"adapt:hud\",\"icons\":{\"adapt:gui/skill/herbalism\":57344}}";

  private static PackManifest read(String extra) {
    return PackManifestReader.read(new StringReader("{\"schema\":2,\"spaceFont\":\"adapt:space\",\"spaces\":{\"57345\":1,\"57346\":-1}," + extra + "}"));
  }

  @Test
  void hudGlyphsAndToastIconsResolve() {
    PackHudManifest hud = PackHudManifest.of(read(HUD));

    assertThat(hud.icon("adapt:gui/skill/herbalism")).isEqualTo("\uE000");
    assertThat(hud.icon("adapt:gui/skill/hunter")).isNull();
    assertThat(hud.toastModel("adapt:gui/skill/herbalism")).isEqualTo(Id.parse("adapt:adapt_ui/toast/herbalism"));
    assertThat(hud.toastModel("adapt:gui/adaptation/herbalism-growth")).isNull();
  }

  @Test
  void withoutAPublishedManifestNobodyIsAPackViewer() {
    assertThat(PackHudManifest.current()).isSameAs(PackHudManifest.NONE);
    assertThat(PackAudience.DELIVERED.book(mock(PlayerRef.class))).isFalse();
  }
}
