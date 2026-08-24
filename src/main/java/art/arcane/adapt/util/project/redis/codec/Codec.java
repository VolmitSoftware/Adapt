package art.arcane.adapt.util.project.redis.codec;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.lettuce.core.codec.RedisCodec;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.jetbrains.annotations.Contract;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import static io.lettuce.core.codec.StringCodec.UTF8;

@Log
public final class Codec implements RedisCodec<String, Message> {
  public static final String CHANNEL = "Adapt:data:v2";
  private static final String REPLY_CHANNEL_PREFIX = CHANNEL + ":reply:";
  public static final Codec INSTANCE = new Codec()
      .register(DataRequest.class, DataRequest::decode)
      .register(DataMessage.class, DataMessage::decode)
      .register(ResetNotice.class, ResetNotice::decode);

  private final Map<Class<? extends Message>, Integer> types = new HashMap<>();
  private final List<Message.Decoder<?>> messages = new ArrayList<>();

  private Codec() {
  }

  public static String replyChannel(UUID requestId) {
    return REPLY_CHANNEL_PREFIX + Objects.requireNonNull(requestId);
  }

  public static boolean isReplyChannel(String channel) {
    return channel != null && channel.startsWith(REPLY_CHANNEL_PREFIX);
  }

  @Override
  public String decodeKey(ByteBuffer bytes) {
    return UTF8.decodeKey(bytes);
  }

  @Override
  public Message decodeValue(ByteBuffer bytes) {
    try (DataInputStream input = new DataInputStream(new ByteBufferInputStream(bytes))) {
      int id = input.readInt();
      if (id < 0 || id >= messages.size()) {
        return null;
      }
      return messages.get(id).decode(input);
    } catch (IOException | RuntimeException error) {
      log.log(Level.SEVERE, "Error decoding message", error);
      return null;
    }
  }

  @Override
  public ByteBuffer encodeKey(String key) {
    return UTF8.encodeKey(key);
  }

  @Override
  public ByteBuffer encodeValue(Message value) {
    Objects.requireNonNull(value);
    Integer registration = types.get(value.getClass());
    if (registration == null) {
      throw new IllegalArgumentException("Unregistered Redis message type: " + value.getClass().getName());
    }
    try {
      ByteArrayDataOutput output = ByteStreams.newDataOutput();
      output.writeInt(registration);
      value.encode(output);
      return ByteBuffer.wrap(output.toByteArray());
    } catch (IOException error) {
      throw new IllegalStateException(
          "Error encoding Redis message " + value.getClass().getName(), error);
    }
  }

  @Contract("_, _ -> this")
  public <T extends Message> Codec register(@NonNull Class<T> type,
                                            @NonNull Message.Decoder<T> decoder) {
    if (types.containsKey(type)) {
      throw new IllegalArgumentException("Type " + type + " already registered");
    }
    int id = messages.size();
    messages.add(decoder);
    types.put(type, id);
    return this;
  }
}
