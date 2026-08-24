package art.arcane.adapt.util.project.redis.codec;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodecTest {
  @Test
  void roundTripsRequestCorrelatedTransferFrames() {
    UUID playerId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    List<Message> messages = List.of(
        new DataRequest(playerId, requestId, ownerToken, 5L),
        new DataMessage(playerId, requestId, ownerToken, 5L, 3L, "{\"player\":true}"),
        new ResetNotice(playerId, UUID.randomUUID(), 6L, false)
    );

    for (Message message : messages) {
      ByteBuffer encoded = Codec.INSTANCE.encodeValue(message);

      assertThat(Codec.INSTANCE.decodeValue(encoded)).isEqualTo(message);
    }
  }

  @Test
  void createsRequestScopedReplyChannels() {
    UUID requestId = UUID.randomUUID();
    String replyChannel = Codec.replyChannel(requestId);

    assertThat(replyChannel).isEqualTo(Codec.CHANNEL + ":reply:" + requestId);
    assertThat(Codec.isReplyChannel(replyChannel)).isTrue();
    assertThat(Codec.isReplyChannel(Codec.CHANNEL)).isFalse();
  }

  @Test
  void rejectsUnregisteredMessageTypesInsteadOfPublishingInvalidFrames() {
    Message unregistered = output -> {
    };

    assertThatThrownBy(() -> Codec.INSTANCE.encodeValue(unregistered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unregistered Redis message type");
  }
}
