package com.volmit.adapt;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.volmit.adapt.util.redis.codec.Codec;
import com.volmit.adapt.util.redis.codec.DataRequest;
import com.volmit.adapt.util.redis.codec.Message;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import lombok.extern.java.Log;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Log(topic = "adapt")
public class RedisHandler implements AutoCloseable {
    private static final String SENDING = "Sending data request to servers for player %s(%s)";
    private static final String SENT = "Sent data request to servers for player %s(%s) to %s servers";

    private final boolean debug;
    private final RedisClient redisClient;
    private final RedisPubSubReactiveCommands<String, Message> pubSub;

    public RedisHandler(boolean debug, RedisClient redisClient) {
        this.debug = debug;
        this.redisClient = redisClient;
        this.pubSub = redisClient.connectPubSub(Codec.INSTANCE).reactive();
    }

    @Subscribe(async = false)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        var player = event.getPlayer();
        debug(SENDING, player.getUsername(), player.getUniqueId());
        Long received = pubSub.publish("Adapt:data", new DataRequest(player.getUniqueId()))
                .block(Duration.of(3, ChronoUnit.SECONDS));
        if (received == null) return;
        debug(SENT, player.getUsername(), player.getUniqueId(), received);
    }

    private void debug(String message, Object... args) {
        if (debug) log.info(message.formatted(args));
    }

    public void close() throws Exception {
        redisClient.close();
    }
}
