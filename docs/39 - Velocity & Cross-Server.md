# Velocity & Cross-Server

Adapt ships a small Velocity plugin whose only job is to give backends a heads-up over Redis when a player is about to switch servers. SQL stays the authority for progression. The companion does not move data by itself, it does not sync local JSON files, and it is not a replacement for a shared database.

The problem it solves is timing. Without it, a player who hops from backend A to backend B can arrive before A's last write has landed in SQL, and B loads a slightly stale profile. With it, the proxy fires a request the moment it knows where the player is headed, backend A answers with the profile it currently holds, and B has a fresh copy waiting in a short-lived Redis cache when the login arrives.

That is the whole design. It is a cache warm-up, not a transfer protocol. If Redis is down, or a backend has Redis off, the player still loads from SQL and nothing breaks; the profile is just as current as SQL happens to be.

## What the pieces do

The proxy plugin listens for a server pre-connect and publishes a data request on the fixed `Adapt:data` channel. It waits up to three seconds for Redis to report how many subscribers received the publish, then moves on. It never waits for a backend to answer, and a timeout produces no message unless `debug` is on. Because the listener is synchronous, a slow or unreachable Redis stalls the connection for up to those three seconds.

Each backend subscribes to the same channel. When it sees a request it looks the player up among the profiles it currently holds in memory, and publishes that profile if it has one. Backends also publish after every successful SQL write. Anything a backend receives is kept in a one-minute cache and used ahead of a SQL read on that player's next login.

The channel name is fixed. Two separate Adapt networks pointed at one Redis service will see each other's traffic. Isolate their Redis instances or their networks; there is no channel setting to change.

## Setting it up

1. Back up the shared Adapt database and every backend's `plugins/Adapt/` directory.
2. Get SQL working on one backend first. Confirm a full player round trip before you touch Redis.
3. Lock Redis down with network rules and ACL credentials. The client has no TLS option, no Redis database-number option, and no channel-name option, so the network is the only boundary you get.
4. Set identical SQL settings and identical Redis settings on every backend, with `sql.enabled` and `redis.enabled` both true.
5. Build the companion and drop `velocity/build/libs/velocity.jar` into the proxy's `plugins/`. Backends keep the normal shaded Adapt jar.
6. Restart the proxy and every backend. Backend SQL and Redis settings are read at enable, so config edits there need a restart.
7. Confirm every backend logs a successful SQL connection and a successful Redis subscription, and that no `.pending-sql` recovery file is failing to replay.
8. Move a disposable player between two backends and compare skill XP, knowledge, learned adaptations, effect preferences, and mutation equipment against the SQL authority after each hop.

`/velocity reload` is only for the companion's own `config.toml`. It unregisters the handler, closes the Redis client, reloads and rewrites the file, and registers a fresh handler.

## Reading the results honestly

A successful proxy connection proves only that Redis accepted a publish. It does not prove a backend answered, that the answer was current, or that the destination's SQL load is up to date. Verify the profile on the destination backend, not in the proxy log. A backend with Redis disabled, SQL disabled, or a failed Redis startup silently stays out of this path and falls back to plain SQL loads.

`config.toml` and the backend credentials are plain text. Keep them off world-readable paths.

## Reference

### Required topology

| Component | Requirement |
|---|---|
| Backend Adapt | Same SQL host, port, and database as every other backend, plus `redis.enabled`, `redis.host`, `redis.port`, `redis.username`, `redis.password` |
| Velocity companion | `config.toml` with `debug`, `host`, `port`, `username`, `password` |
| Redis | Reachable from the proxy and from every backend with the configured ACL credentials |

### Companion config

The companion's data directory comes from its plugin id, so it is normally `plugins/adapt/`. That folder holds `config.toml`, the legacy `config.yml` if one survives, and the `.libs/` download cache.

| Key | Default | What it does |
|---|---|---|
| `debug` | `false` | Prints a line per data request published, with the player name and the subscriber count |
| `host` | `"127.0.0.1"` | Redis address the proxy connects to |
| `port` | `6379` | Redis TCP port |
| `username` | `""` | Redis ACL username; credentials are attached only when the username or password is non-empty |
| `password` | `""` | Redis password; same rule as the username |

On load the companion rewrites `config.toml` in canonical form when the file differs from canonical output. If only a legacy `config.yml` exists it is read with the JSON parser and converted, so a `config.yml` that is genuine YAML rather than the JSON older builds wrote will fail to parse. If neither file exists, defaults are written.

Two message types travel on `Adapt:data` in both directions: `DataRequest`, which is a player UUID, and `DataMessage`, which is a player UUID plus that player's profile JSON.

### Failure checks

| Symptom | What to check |
|---|---|
| Proxy fails to initialize or reload | Redis host, port, ACL credentials, reachability, and the full Velocity exception |
| Proxy reports zero recipients | No backend is subscribed to `Adapt:data`, or Redis routing differs between proxy and backends |
| Stale data after a switch | Shared SQL identity, backend Redis enablement, leftover `.pending-sql` files, and the destination backend's load log |
| Only one backend syncs | Compare canonical `adapt.toml` across every backend and confirm each was restarted |
| Traffic from an unrelated network | Two Adapt networks share the fixed channel; give them separate Redis services |

## See also

- `01 - Installation & Configuration.md`
- `38 - Runtime Architecture.md`
- `40 - Operator Runbooks.md`
