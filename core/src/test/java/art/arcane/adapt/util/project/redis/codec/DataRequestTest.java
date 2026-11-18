package art.arcane.adapt.util.project.redis.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataRequestTest {
  @Test
  void roundTripsPlayerAndRequestIdentity() throws Exception {
    DataRequest request = new DataRequest(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 14L);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    try (DataOutputStream output = new DataOutputStream(bytes)) {
      request.encode(output);
    }

    DataRequest decoded;
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      decoded = DataRequest.decode(input);
    }

    assertThat(decoded).isEqualTo(request);
  }
}
