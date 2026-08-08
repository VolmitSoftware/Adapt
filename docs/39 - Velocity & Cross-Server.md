# Velocity & Cross-Server

Adapt's Velocity companion coordinates backend cache invalidation and data requests over Redis while SQL remains the shared progression authority. It is not a replacement for SQL and does not transfer local JSON files between servers.

## Required topology

Every participating backend must use the same SQL database and the same Redis service, with both `sql.enabled=true` and `redis.enabled=true`. Install the Velocity artifact from `velocity/build/libs/velocity.jar` on the proxy; install the normal shaded Adapt plugin on each Paper/Folia backend.

| Component | Required configuration |
|---|---|
| Backend Adapt | Matching SQL connection/database plus `redis.enabled`, `redis.host`, `redis.port`, `redis.username`, and `redis.password` |
| Velocity companion | `config.toml` containing `debug`, `host`, `port`, `username`, and `password` |
| Redis | Reachable from the proxy and every backend using the configured ACL credentials |

The Velocity defaults are `debug=false`, host `127.0.0.1`, port `6379`, and empty credentials. `config.toml`, legacy `config.yml`, and downloaded `.libs/` live in Velocity's injected Adapt plugin data directory, normally `plugins/adapt/` from the plugin id. The plugin creates and canonicalizes `config.toml`; if only legacy `config.yml` exists, it migrates that file to TOML. `/velocity reload` closes the current Redis client, reloads/canonicalizes the file, and registers a new handler.

## Transfer behavior

Backends use a one-minute Redis cache around SQL-owned player data and exchange internal messages on the fixed `Adapt:data` pub/sub channel. Before a Velocity server connection, the proxy publishes a player data request synchronously and waits up to three seconds for Redis to report the publish result; it does not wait three seconds for a backend response. Backends receive requests asynchronously, reconcile against SQL/cache state, and publish the protocol's data messages.

If backend Redis is disabled, SQL is disabled, or Redis startup fails, that backend does not participate in this synchronization path. A successful proxy connection only proves Redis connectivity and publication, not that another backend responded or that the player's subsequent SQL load is current.

## Deployment procedure

1. Back up the shared Adapt database and each backend's `plugins/Adapt/` directory.
2. Configure and test SQL on one backend before enabling Redis.
3. Restrict Redis with network controls and ACL credentials; the current client settings expose no TLS, Redis database-number, or channel-name option.
4. Apply identical SQL/Redis settings to all backends, then install the Velocity companion.
5. Restart proxy and backends. Use `/velocity reload` only for companion `config.toml`; backend SQL/Redis changes require backend restarts.
6. Confirm every backend logs successful SQL and Redis initialization and no pending-write replay failures.
7. Move a disposable player between two backends and verify skill XP, knowledge, learned adaptations, effect preferences, and mutation equipment from the SQL authority after each handoff.

Because the channel is fixed, separate Adapt networks sharing one Redis service can receive each other's protocol traffic. Network isolation or a dedicated Redis instance is the available separation mechanism. Store `config.toml` and backend credentials with restricted filesystem permissions and do not publish them.

## Failure checks

| Symptom | Check |
|---|---|
| Proxy initialization/reload fails | Redis host, port, ACL credentials, reachability, and the complete Velocity exception |
| Proxy reports zero recipients | No backend subscriber is connected to `Adapt:data`, or Redis routing differs |
| Player sees stale data after switching | Shared SQL identity, backend Redis enablement, pending SQL files, and backend response logs |
| Only one backend synchronizes | Compare canonical `adapt.toml` across every backend and confirm each was restarted |
| Duplicate/cross-network traffic | Multiple networks share the fixed channel; isolate their Redis services |

## Related pages

- `01 - Installation & Configuration.md`
- `38 - Runtime Architecture.md`
- `40 - Operator Runbooks & Smoke Tests.md`
