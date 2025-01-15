package com.volmit.adapt.util.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StaticCredentialsProvider;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

@Data
@Accessors(chain = true)
public class RedisConfig {
    private @NonNull String host = "127.0.0.1";
    private int port = 6379;
    private @Nullable String username = "";
    private @Nullable String password = "";

    @Contract(" -> new")
    public RedisClient createClient() {
        var uri = RedisURI.create(host, port);
        if ((username != null && !username.isEmpty()) || (password != null && !password.isEmpty()))
            uri.setCredentialsProvider(new StaticCredentialsProvider(username, password != null ? password.toCharArray() : null));
        return RedisClient.create(uri);
    }
}
