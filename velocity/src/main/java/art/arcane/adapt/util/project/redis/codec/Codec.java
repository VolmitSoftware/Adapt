package art.arcane.adapt.util.project.redis.codec;

import com.google.common.io.ByteStreams;
import io.lettuce.core.codec.RedisCodec;
import it.unimi.dsi.fastutil.Pair;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.jetbrains.annotations.Contract;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;

import static io.lettuce.core.codec.StringCodec.UTF8;

@Log
public final class Codec implements RedisCodec<String, Message> {
    public static final Codec INSTANCE = new Codec()
            .register(DataRequest.class, DataRequest::decode)
            .register(DataMessage.class, DataMessage::decode);

    private final Map<Class<? extends Message>, Pair<Message.Decoder<?>, Integer>> types = new HashMap<>();
    private final List<Message.Decoder<?>> messages = new ArrayList<>();

    private Codec() {}

    @Override
    public String decodeKey(ByteBuffer bytes) {
        return UTF8.decodeKey(bytes);
    }

    @Override
    public Message decodeValue(ByteBuffer bytes) {
        try (var in = new DataInputStream(new ByteBufferInputStream(bytes))){
            int id = in.readInt();
            if (id < 0 || id >= messages.size()) return null;
            return messages.get(id).decode(in);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error decoding message", e);
            return null;
        }
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return UTF8.encodeKey(key);
    }

    @Override
    public ByteBuffer encodeValue(Message value) {
        try {
            var out = ByteStreams.newDataOutput();
            int id = Optional.ofNullable(types.get(value.getClass()).value())
                    .orElse(-1);
            out.writeInt(id);
            value.encode(out);
            return ByteBuffer.wrap(out.toByteArray());
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error encoding message", e);
            return ByteBuffer.allocate(4)
                    .putInt(-1);
        }
    }

    @Contract("_, _ -> this")
    public <T extends Message> Codec register(@NonNull Class<T> type, @NonNull Message.Decoder<T> decoder) {
        if (types.containsKey(type))
            throw new IllegalArgumentException("Type " + type + " already registered");
        int id = messages.size();
        messages.add(decoder);
        types.put(type, Pair.of(decoder, id));
        return this;
    }
}
