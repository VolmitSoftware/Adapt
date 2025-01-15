package com.volmit.adapt.util.redis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.util.redis.codec.Codec;
import com.volmit.adapt.util.redis.codec.DataMessage;
import com.volmit.adapt.util.redis.codec.DataRequest;
import com.volmit.adapt.util.redis.codec.Message;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.api.reactive.ChannelMessage;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Log
public class RedisSync implements AutoCloseable {
    private final RedisClient redisClient;
    private final RedisPubSubReactiveCommands<String, Message> pubSub;
    private final Cache<UUID, String> dataCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public RedisSync() {
        if (!AdaptConfig.get().isUseRedis() || !AdaptConfig.get().isUseSql()) {
            this.redisClient = null;
            this.pubSub = null;
            return;
        }

        this.redisClient = AdaptConfig.get().getRedis().createClient();
        this.pubSub = redisClient.connectPubSub(Codec.INSTANCE).reactive();
        pubSub.subscribe("Adapt:data").subscribe();
        pubSub.observeChannels().doOnNext(this::update).subscribe();
    }

    private void update(@NotNull ChannelMessage<@NotNull String, @Nullable Message> channelMessage) {
        if (!channelMessage.getChannel().equals("Adapt:data")) return;
        Message raw = channelMessage.getMessage();
        if (raw instanceof DataMessage message) {
            Adapt.verbose("Received player data for " + message.uuid());
            dataCache.put(message.uuid(), message.json());
        } else if (raw instanceof DataRequest message) {
            Adapt.instance.getAdaptServer()
                    .getPlayerData(message.uuid())
                    .map(PlayerData::toJson)
                    .ifPresent(data -> publish(message.uuid(), data));
        }
    }

    public void publish(@NonNull UUID uuid, @NonNull String playerData) {
        if (pubSub == null) return;
        Adapt.verbose("Publishing player data for " + uuid);
        pubSub.publish("Adapt:data", new DataMessage(uuid, playerData))
                .subscribe()
                .dispose();
    }

    @NonNull
    public Optional<PlayerData> cachedData(@NonNull UUID uuid) {
        if (pubSub == null) return Optional.empty();
        return Optional.ofNullable(dataCache.getIfPresent(uuid))
                .map(PlayerData::fromJson);
    }

    @Override
    public void close() throws Exception {
        if (redisClient != null)
            redisClient.close();
    }
}
