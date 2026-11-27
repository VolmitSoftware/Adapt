package art.arcane.adapt.client.protocol;

import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Enums travel by name, so a constant added or moved on one side never turns into another constant on the other. */
class FrameEnumTest {
  private static final IconRef ICON = IconRef.item("minecraft:book");

  private enum Before {
    FIRST,
    SECOND
  }

  private enum After {
    ADDED,
    SECOND,
    FIRST
  }

  @Test
  void aConstantReadsBackByNameWhateverItsPosition() {
    FrameWriter out = new FrameWriter(16);
    out.enumValue(Before.SECOND);
    out.enumValue(Before.FIRST);

    FrameReader in = new FrameReader(out.toByteArray());

    assertThat(in.enumValue(After.class)).isEqualTo(After.SECOND);
    assertThat(in.enumValue(After.class)).isEqualTo(After.FIRST);
  }

  @Test
  void aConstantThisSideLacksReadsAsTheFallbackOrRejects() {
    FrameWriter out = new FrameWriter(16);
    out.enumValue(After.ADDED);
    out.enumValue(After.ADDED);

    FrameReader in = new FrameReader(out.toByteArray());

    assertThat(in.enumValue(Before.class, Before.FIRST)).isEqualTo(Before.FIRST);
    assertThatThrownBy(() -> in.enumValue(Before.class)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ADDED");
  }

  @Test
  void aToastOfAKindFromANewerServerShowsAsGenericWithItsTextIntact() throws Exception {
    byte[] frame = FrameCodec.encodeServer(new ServerFrame.Toast(ToastKind.UNLOCK, ICON, "Title", "Body", "rift"));

    ServerFrame decoded = FrameCodec.decodeServer(rename(frame, "UNLOCK", "FUTURE"));

    assertThat(decoded).isEqualTo(new ServerFrame.Toast(ToastKind.GENERIC, ICON, "Title", "Body", "rift"));
  }

  @Test
  void aHudGlyphFromANewerServerRejectsTheFrame() throws Exception {
    byte[] frame = FrameCodec.encodeServer(new ServerFrame.HudStatus("adapt:rift/blink", HudGlyph.TOGGLE, "rift", ICON,
        "Blink", 0L, 1000L, 0, 0, true));

    assertThatThrownBy(() -> FrameCodec.decodeServer(rename(frame, "TOGGLE", "FUTURE")))
        .isInstanceOf(ClientProtocolException.class);
  }

  private static byte[] rename(byte[] frame, String from, String to) {
    byte[] needle = from.getBytes(StandardCharsets.UTF_8);
    byte[] replacement = to.getBytes(StandardCharsets.UTF_8);
    assertThat(replacement).hasSameSizeAs(needle);
    for (int index = 0; index + needle.length <= frame.length; index++) {
      if (Arrays.equals(frame, index, index + needle.length, needle, 0, needle.length)) {
        byte[] copy = frame.clone();
        System.arraycopy(replacement, 0, copy, index, replacement.length);
        return copy;
      }
    }
    throw new AssertionError(from + " not in frame");
  }
}
