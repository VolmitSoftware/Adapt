package art.arcane.adapt.client.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameFragmentsTest {
  private static final int BUKKIT_LEGACY = 32766;

  @Test
  void framesUpToTheLimitTravelUnchanged() throws ClientProtocolException {
    byte[] frame = frame(BUKKIT_LEGACY);

    List<byte[]> payloads = FrameFragments.split(frame, BUKKIT_LEGACY);

    assertThat(payloads).hasSize(1);
    assertThat(payloads.get(0)).isSameAs(frame);
  }

  @Test
  void framesAtBoundarySizesRoundTrip() throws ClientProtocolException {
    int slice = BUKKIT_LEGACY - FrameFragments.HEADER_BYTES;
    for (int size : new int[] {BUKKIT_LEGACY + 1, slice * 2, slice * 2 + 1, FrameLimits.MAX_S2C_BYTES}) {
      byte[] frame = frame(size);
      List<byte[]> payloads = FrameFragments.split(frame, BUKKIT_LEGACY);
      assertThat(payloads).as("size " + size).allSatisfy((byte[] payload) -> assertThat(payload.length).isLessThanOrEqualTo(BUKKIT_LEGACY));
      assertThat(payloads.size()).as("size " + size).isEqualTo((size + slice - 1) / slice);
      assertThat(join(payloads, FrameLimits.MAX_S2C_BYTES)).as("size " + size).isEqualTo(frame);
    }
  }

  @Test
  void aWholeFramePassesThroughTheAssembler() throws ClientProtocolException {
    byte[] frame = frame(40);

    assertThat(new FrameFragments.Assembler(100).accept(frame)).isSameAs(frame);
  }

  @Test
  void outOfOrderFragmentsAreRejectedAndTheAssemblerRecovers() throws ClientProtocolException {
    byte[] frame = frame(300);
    List<byte[]> payloads = FrameFragments.split(frame, 100);
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);

    assertThatThrownBy(() -> assembler.accept(payloads.get(1))).isInstanceOf(ClientProtocolException.class);
    assertThat(assembler.accept(payloads.get(0))).isNull();
    assertThatThrownBy(() -> assembler.accept(payloads.get(2))).isInstanceOf(ClientProtocolException.class);

    assertThat(join(payloads, FrameLimits.MAX_S2C_BYTES)).isEqualTo(frame);
  }

  @Test
  void aWholeFrameBetweenFragmentsIsRejected() throws ClientProtocolException {
    List<byte[]> payloads = FrameFragments.split(frame(300), 100);
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);

    assembler.accept(payloads.get(0));

    assertThatThrownBy(() -> assembler.accept(frame(10))).isInstanceOf(ClientProtocolException.class);
    assertThat(assembler.accept(frame(10))).hasSize(10);
  }

  @Test
  void fragmentsOfAnotherFrameMayNotInterleave() throws ClientProtocolException {
    List<byte[]> first = FrameFragments.split(frame(300), 100);
    List<byte[]> second = FrameFragments.split(frame(500), 100);
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);

    assembler.accept(first.get(0));

    assertThatThrownBy(() -> assembler.accept(second.get(0))).isInstanceOf(ClientProtocolException.class);
  }

  @Test
  void aReassembledFrameOverTheLimitIsRejected() throws ClientProtocolException {
    List<byte[]> payloads = FrameFragments.split(frame(1000), 100);
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(999);

    assertThatThrownBy(() -> {
      for (byte[] payload : payloads) {
        assembler.accept(payload);
      }
    }).isInstanceOf(ClientProtocolException.class).hasMessageContaining("exceeds 999");
  }

  @Test
  void malformedEnvelopesAreRejected() {
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);

    assertThatThrownBy(() -> assembler.accept(new byte[] {0, 2, 0})).hasMessageContaining("empty");
    assertThatThrownBy(() -> assembler.accept(new byte[] {0, 1, 0, 7})).hasMessageContaining("count 1");
    assertThatThrownBy(() -> assembler.accept(new byte[] {0, (byte) (FrameLimits.MAX_FRAGMENTS + 1), 0, 7}))
        .hasMessageContaining("count " + (FrameLimits.MAX_FRAGMENTS + 1));
  }

  @Test
  void aFragmentInsideAFragmentedFrameIsRejected() throws ClientProtocolException {
    List<byte[]> payloads = new ArrayList<>(List.of(new byte[] {0, 2, 0, 0, 1, 2}, new byte[] {0, 2, 1, 3, 4}));
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);

    assertThat(assembler.accept(payloads.get(0))).isNull();
    assertThatThrownBy(() -> assembler.accept(payloads.get(1))).hasMessageContaining("inside");
  }

  @Test
  void splittingRefusesFramesThatNeedTooManyFragmentsOrALimitWithNoRoom() {
    assertThatThrownBy(() -> FrameFragments.split(frame(FrameLimits.MAX_FRAGMENTS * 10 + 1), 13))
        .isInstanceOf(ClientProtocolException.class);
    assertThatThrownBy(() -> FrameFragments.split(frame(10), FrameFragments.HEADER_BYTES))
        .isInstanceOf(ClientProtocolException.class);
  }

  @Test
  void aCatalogLargerThanALegacyPluginMessageReachesTheClientWhole() throws ClientProtocolException {
    ServerFrame.Feedback large = new ServerFrame.Feedback(FeedbackKind.INFO, "w".repeat(FrameLimits.MAX_TEXT_BYTES));
    byte[] encoded = FrameCodec.encodeServer(large);
    List<byte[]> payloads = FrameFragments.split(encoded, 3000);

    assertThat(payloads).hasSizeGreaterThan(2);
    assertThat(FrameCodec.decodeServer(join(payloads, FrameLimits.MAX_S2C_BYTES))).isEqualTo(large);
  }

  @Test
  void fragmentEnvelopesMatchTheirGoldenBytes() throws Exception {
    byte[] frame = FrameCodec.encodeServer(new ServerFrame.Feedback(FeedbackKind.FAILURE, "Not enough knowledge"));
    StringBuilder hex = new StringBuilder();
    for (byte[] payload : FrameFragments.split(frame, 12)) {
      hex.append(HexFormat.of().formatHex(payload)).append('\n');
    }
    try (InputStream in = FrameFragmentsTest.class.getResourceAsStream("/client-protocol/fragments-feedback.hex")) {
      assertThat(in).isNotNull();
      assertThat(hex.toString()).isEqualTo(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
    } catch (IOException exception) {
      throw new AssertionError(exception);
    }
  }

  private static byte[] join(List<byte[]> payloads, int max) throws ClientProtocolException {
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(max);
    byte[] whole = null;
    for (int index = 0; index < payloads.size(); index++) {
      whole = assembler.accept(payloads.get(index));
      if (index < payloads.size() - 1) {
        assertThat(whole).isNull();
      }
    }
    return whole;
  }

  private static byte[] frame(int size) {
    byte[] frame = new byte[size];
    new Random(size).nextBytes(frame);
    frame[0] = (byte) FrameCodec.S2C_CATALOG;
    return frame;
  }
}
