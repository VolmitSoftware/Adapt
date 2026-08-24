package art.arcane.adapt.util.project.redis.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResetNoticeTest {
  @Test
  void roundTripsResetIdentityAndFence() throws Exception {
    ResetNotice notice = new ResetNotice(
        UUID.randomUUID(), UUID.randomUUID(), 17L, true
    );
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    try (DataOutputStream output = new DataOutputStream(bytes)) {
      notice.encode(output);
    }

    ResetNotice decoded;
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      decoded = ResetNotice.decode(input);
    }

    assertThat(decoded).isEqualTo(notice);
  }
}
