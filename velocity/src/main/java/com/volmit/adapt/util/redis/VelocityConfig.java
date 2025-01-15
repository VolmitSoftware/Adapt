package com.volmit.adapt.util.redis;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class VelocityConfig extends RedisConfig {
    private boolean debug = false;
}
