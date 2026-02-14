package art.arcane.adapt.util.project.redis;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class VelocityConfig extends RedisConfig {
    private boolean debug = false;
}
