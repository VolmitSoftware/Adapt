package art.arcane.adapt.util.project.redis.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataMessageTest {
  @Test
  void roundTripsPlayerDataLargerThanModifiedUtfLimit() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    String json = "{\"payload\":\"" + "é".repeat(100_000) + "\"}";
    DataMessage message = new DataMessage(playerId, requestId, ownerToken, 4L, 9L, json);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    try (DataOutputStream output = new DataOutputStream(bytes)) {
      message.encode(output);
    }

    DataMessage decoded;
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      decoded = DataMessage.decode(input);
    }

    assertThat(decoded).isEqualTo(message);
  }

  @Test
  void rejectsNegativePayloadLength() throws Exception {
    byte[] encoded = encodedLength(-1);

    assertThatThrownBy(() -> DataMessage.decode(new DataInputStream(new ByteArrayInputStream(encoded))))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Invalid Redis player data length");
  }

  @Test
  void rejectsPayloadAboveBoundedLimitBeforeAllocation() throws Exception {
    byte[] encoded = encodedLength(DataMessage.MAX_JSON_BYTES + 1);

    assertThatThrownBy(() -> DataMessage.decode(new DataInputStream(new ByteArrayInputStream(encoded))))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Invalid Redis player data length");
  }

  @Test
  void rejectsMalformedUtf8() throws Exception {
    byte[] encoded = encodedPayload(new byte[]{(byte) 0xC3, 0x28});

    assertThatThrownBy(() -> DataMessage.decode(new DataInputStream(new ByteArrayInputStream(encoded))))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Invalid Redis player data UTF-8");
  }

  @Test
  void acceptsTheExactMediumTextByteLimit() throws Exception {
    DataMessage message = message("a".repeat(DataMessage.MAX_JSON_BYTES));

    try (DataOutputStream output = new DataOutputStream(OutputStream.nullOutputStream())) {
      message.encode(output);
    }
  }

  @Test
  void oversizePayloadFailsCodecEncodingInsteadOfReturningASentinelFrame() {
    DataMessage message = message("a".repeat(DataMessage.MAX_JSON_BYTES + 1));

    assertThatThrownBy(() -> Codec.INSTANCE.encodeValue(message))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IOException.class)
        .hasMessageContaining("Error encoding Redis message");
  }

  private static byte[] encodedLength(int length) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeLong(0L);
      output.writeLong(0L);
      output.writeLong(0L);
      output.writeLong(0L);
      output.writeLong(1L);
      output.writeLong(2L);
      output.writeLong(1L);
      output.writeLong(1L);
      output.writeInt(length);
    }
    return bytes.toByteArray();
  }

  private static byte[] encodedPayload(byte[] payload) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeLong(0L);
      output.writeLong(0L);
      output.writeLong(0L);
      output.writeLong(0L);
      output.writeLong(1L);
      output.writeLong(2L);
      output.writeLong(1L);
      output.writeLong(1L);
      output.writeInt(payload.length);
      output.write(payload);
    }
    return bytes.toByteArray();
  }

  private static DataMessage message(String json) {
    return new DataMessage(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L, 1L, json);
  }
}
