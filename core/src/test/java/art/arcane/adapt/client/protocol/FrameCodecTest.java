package art.arcane.adapt.client.protocol;

import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.SkillView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameCodecTest {
  @Test
  void everyServerFrameSurvivesARoundTrip() throws ClientProtocolException {
    for (Map.Entry<String, ServerFrame> sample : FrameSamples.server().entrySet()) {
      byte[] encoded = FrameCodec.encodeServer(sample.getValue());
      assertThat(FrameCodec.decodeServer(encoded)).as(sample.getKey()).isEqualTo(sample.getValue());
    }
  }

  @Test
  void everyClientFrameSurvivesARoundTrip() throws ClientProtocolException {
    for (Map.Entry<String, ClientFrame> sample : FrameSamples.client().entrySet()) {
      byte[] encoded = FrameCodec.encodeClient(sample.getValue());
      assertThat(FrameCodec.decodeClient(encoded)).as(sample.getKey()).isEqualTo(sample.getValue());
    }
  }

  @Test
  void samplesCoverEveryFrameType() {
    List<Class<?>> serverTypes = FrameSamples.server().values().stream().<Class<?>>map(Object::getClass).toList();
    List<Class<?>> clientTypes = FrameSamples.client().values().stream().<Class<?>>map(Object::getClass).toList();

    assertThat(serverTypes).containsAll(Arrays.asList(ServerFrame.class.getPermittedSubclasses()));
    assertThat(clientTypes).containsAll(Arrays.asList(ClientFrame.class.getPermittedSubclasses()));
  }

  @Test
  void unknownOpcodesFailCleanly() {
    assertThatThrownBy(() -> FrameCodec.decodeClient(new byte[] {(byte) 99}))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("unknown client opcode 99");
    assertThatThrownBy(() -> FrameCodec.decodeServer(new byte[] {(byte) 0}))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("unknown server opcode 0");
    assertThatThrownBy(() -> FrameCodec.decodeServer(new byte[] {(byte) FrameCodec.C2S_HELLO, 1, 1}))
        .isInstanceOf(ClientProtocolException.class);
  }

  @Test
  void emptyTruncatedAndTrailingFramesFail() throws ClientProtocolException {
    byte[] learn = FrameCodec.encodeClient(new ClientFrame.Learn("agility-wall-jump", 3, 42L));

    assertThatThrownBy(() -> FrameCodec.decodeClient(new byte[0])).isInstanceOf(ClientProtocolException.class);
    assertThatThrownBy(() -> FrameCodec.decodeClient(Arrays.copyOf(learn, learn.length - 1)))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("ends early");
    assertThatThrownBy(() -> FrameCodec.decodeClient(Arrays.copyOf(learn, learn.length + 1)))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("trailing");
  }

  @Test
  void malformedFieldsFail() {
    byte[] invalidUtf8 = {(byte) FrameCodec.C2S_LEARN, 2, (byte) 0xC3, (byte) 0x28, 1, 1};
    byte[] overlongVarint = {(byte) FrameCodec.C2S_PANEL_CLOSED, (byte) 0x81, 0};
    byte[] hugeVarint = {(byte) FrameCodec.C2S_PANEL_CLOSED, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F};
    byte[] badEnum = {(byte) FrameCodec.C2S_RESET_PREFERENCE, 7, 1, 'a'};
    byte[] badRoute = {(byte) FrameCodec.C2S_OPEN, 9};
    byte[] badClick = {(byte) FrameCodec.C2S_PANEL_CLICK, 1, 1, 1, 40};

    for (byte[] frame : List.of(invalidUtf8, overlongVarint, hugeVarint, badEnum, badRoute, badClick)) {
      assertThatThrownBy(() -> FrameCodec.decodeClient(frame)).isInstanceOf(ClientProtocolException.class);
    }
  }

  @Test
  void invalidBooleansAndNonFiniteNumbersFail() throws ClientProtocolException {
    byte[] status = FrameCodec.encodeServer(new ServerFrame.HudClear("x"));
    byte[] badOptional = {(byte) FrameCodec.S2C_TOAST, 0, 1, 'a', 0, 0, 0, 0, 2, 0, 0, 0};
    byte[] xp = FrameCodec.encodeServer(new ServerFrame.XpEarned("a", 1D, 1D, 1, 0D));
    byte[] nan = Arrays.copyOf(xp, xp.length);
    long bits = Double.doubleToRawLongBits(Double.NaN);
    for (int index = 0; index < 8; index++) {
      nan[3 + index] = (byte) (bits >>> (56 - (index * 8)));
    }

    assertThat(FrameCodec.decodeServer(status)).isEqualTo(new ServerFrame.HudClear("x"));
    assertThatThrownBy(() -> FrameCodec.decodeServer(badOptional)).isInstanceOf(ClientProtocolException.class)
        .hasMessageContaining("invalid boolean");
    assertThatThrownBy(() -> FrameCodec.decodeServer(nan)).isInstanceOf(ClientProtocolException.class)
        .hasMessageContaining("non-finite");
    assertThatThrownBy(() -> FrameCodec.encodeServer(new ServerFrame.XpEarned("a", Double.POSITIVE_INFINITY, 1D, 1, 0D)))
        .isInstanceOf(ClientProtocolException.class);
  }

  @Test
  void declaredCountsBeyondTheirBoundFailBeforeAllocating() {
    byte[] items = {(byte) FrameCodec.S2C_CUSTOM_ITEMS, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x07};
    byte[] skills = {(byte) FrameCodec.S2C_CATALOG, 1, 1, 'x', (byte) 0x90, 0x4E};

    assertThatThrownBy(() -> FrameCodec.decodeServer(items)).isInstanceOf(ClientProtocolException.class)
        .hasMessageContaining("exceeds");
    assertThatThrownBy(() -> FrameCodec.decodeServer(skills)).isInstanceOf(ClientProtocolException.class)
        .hasMessageContaining("exceeds");
  }

  @Test
  void encodingRefusesValuesOutsideTheLimits() {
    String longId = "a".repeat(FrameLimits.MAX_ID_BYTES + 1);
    List<PanelTile> tooManyLines = List.of(new PanelTile(0, IconRef.item("minecraft:paper"), 1, "x",
        Collections.nCopies(FrameLimits.MAX_LINES + 1, "line"), false, null, TileRole.ENTRY, "", "", null, List.of()));

    assertThatThrownBy(() -> FrameCodec.encodeClient(new ClientFrame.Learn(longId, 1, 1L)))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("exceeds");
    assertThatThrownBy(() -> FrameCodec.encodeClient(new ClientFrame.Learn("a", -1, 1L)))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("negative");
    assertThatThrownBy(() -> FrameCodec.encodeServer(new ServerFrame.PanelOpen(new PanelView(1, 1, "t", 1, PanelLayout.GRID, null, List.of(), tooManyLines))))
        .isInstanceOf(ClientProtocolException.class);
  }

  @Test
  void oversizedFramesAreRefusedInBothDirections() {
    byte[] inbound = new byte[FrameLimits.MAX_C2S_BYTES + 1];
    inbound[0] = (byte) FrameCodec.C2S_HELLO;
    List<SkillView> skills = new ArrayList<>();
    String text = "x".repeat(FrameLimits.MAX_TEXT_BYTES);
    for (int index = 0; index < 80; index++) {
      skills.add(new SkillView("s" + index, text, text, IconRef.item("minecraft:paper"), 0, true, List.of(),
          List.of(), List.of()));
    }

    assertThatThrownBy(() -> FrameCodec.decodeClient(inbound)).isInstanceOf(ClientProtocolException.class)
        .hasMessageContaining("exceeds");
    assertThatThrownBy(() -> FrameCodec.encodeServer(new ServerFrame.Catalog(new CatalogView(1L, "en_US", skills))))
        .isInstanceOf(ClientProtocolException.class).hasMessageContaining("exceeds");
  }

  @Test
  void panelTilesMustFitThePanel() {
    List<PanelTile> outside = List.of(new PanelTile(9, IconRef.item("minecraft:paper"), 1, "x", List.of(), false, null,
        TileRole.ENTRY, "", "", null, List.of()));

    assertThatThrownBy(() -> new PanelView(1, 1, "t", 1, PanelLayout.GRID, null, List.of(), outside))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PanelView(1, 1, "t", 7, PanelLayout.GRID, null, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PanelPaging(3, 3, 10)).isInstanceOf(IllegalArgumentException.class);
  }
}
