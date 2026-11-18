package art.arcane.adapt.util.project.redis;

import art.arcane.adapt.util.config.ConfigDoc;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

@Data
@Accessors(chain = true)
public class RedisConfig {
  @ConfigDoc(value = "Accelerates fenced player-data handoff between SQL-backed servers over Redis.", impact = "Ignored unless sql.enabled is also true; request-correlated transfer snapshots never replace SQL ownership, and the endpoint must be reachable at startup when enabled.")
  private boolean enabled = false;
  @ConfigDoc(value = "Hostname or address of the Redis server.", impact = "Used for both the pub/sub subscriber and the publisher connection.")
  private @NonNull String host = "127.0.0.1";
  @ConfigDoc(value = "TCP port the Redis server listens on.", impact = "Must match the server; 6379 is the Redis default.")
  private int port = 6379;
  @ConfigDoc(value = "Redis ACL username, or empty for none.", impact = "Credentials are only attached to the connection when the username or password is non-empty.")
  private @Nullable String username = "";
  @ConfigDoc(value = "Redis password, or empty for none.", impact = "Credentials are only attached to the connection when the username or password is non-empty.")
  private @Nullable String password = "";
}
