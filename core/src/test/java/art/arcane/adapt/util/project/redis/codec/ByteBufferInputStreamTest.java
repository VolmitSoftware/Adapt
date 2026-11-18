package art.arcane.adapt.util.project.redis.codec;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteBufferInputStreamTest {
  @Test
  void boundedReadsHonorInputStreamEndOfFileSemantics() {
    ByteBufferInputStream input = new ByteBufferInputStream(ByteBuffer.wrap(new byte[]{1, 2}));
    byte[] target = new byte[4];

    assertThat(input.read(target)).isEqualTo(2);
    assertThat(target).startsWith((byte) 1, (byte) 2);
    assertThat(input.read(target)).isEqualTo(-1);
    assertThat(input.read()).isEqualTo(-1);
  }

  @Test
  void negativeReadNBytesLengthIsRejected() {
    ByteBufferInputStream input = new ByteBufferInputStream(ByteBuffer.allocate(0));

    assertThatThrownBy(() -> input.readNBytes(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
