package com.volmit.adapt;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.volmit.adapt.util.redis.codec.Codec;
import com.volmit.adapt.util.redis.codec.DataRequest;
import com.volmit.adapt.util.redis.codec.Message;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;

public class RedisHandler implements AutoCloseable {
    private final RedisClient redisClient;
    private final RedisPubSubReactiveCommands<String, Message> pubSub;

    public RedisHandler(RedisClient redisClient) {
        this.redisClient = redisClient;
        this.pubSub = redisClient.connectPubSub(Codec.INSTANCE).reactive();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        pubSub.publish("Adapt:data", new DataRequest(event.getPlayer().getUniqueId()))
                .subscribe()
                .dispose();
    }

    public void close() throws Exception {
        redisClient.close();
    }
}
