package art.arcane.adapt.content.adaptation.rift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class RiftAccessViewRegistry {
  private final Map<UUID, Session> sessionsByPlayer = new HashMap<>();
  private final Map<BlockKey, Set<UUID>> playersByBlock = new HashMap<>();
  private final Map<ChunkKey, Set<UUID>> playersByChunk = new HashMap<>();
  private final Map<ChunkKey, Integer> chunkReferences = new HashMap<>();
  private boolean accepting = true;
  private long sequence;

  synchronized Registration register(UUID playerId, Set<BlockKey> blockKeys, Set<ChunkKey> chunkKeys) {
    Objects.requireNonNull(playerId);
    Set<BlockKey> normalizedBlockKeys = immutableKeys(blockKeys, "blockKeys");
    Set<ChunkKey> normalizedChunkKeys = immutableKeys(chunkKeys, "chunkKeys");
    if (!accepting) {
      return null;
    }

    Session retired = sessionsByPlayer.remove(playerId);
    if (retired != null) {
      removeSessionIndexes(retired);
    }

    Session session = new Session(++sequence, playerId, normalizedBlockKeys, normalizedChunkKeys);
    sessionsByPlayer.put(playerId, session);
    indexSession(session);
    return new Registration(session, retired);
  }

  synchronized boolean activate(Session session, Object viewIdentity) {
    Objects.requireNonNull(viewIdentity);
    Session current = sessionsByPlayer.get(session.playerId());
    if (current != session || current.viewIdentity() != null) {
      return false;
    }
    current.activate(viewIdentity);
    return true;
  }

  synchronized boolean isCurrent(Session session) {
    return sessionsByPlayer.get(session.playerId()) == session;
  }

  synchronized boolean isPending(Session session) {
    return sessionsByPlayer.get(session.playerId()) == session && session.viewIdentity() == null;
  }

  synchronized boolean tracksView(UUID playerId, Object viewIdentity) {
    Session current = sessionsByPlayer.get(playerId);
    return viewIdentity != null && current != null && current.viewIdentity() == viewIdentity;
  }

  synchronized Session closeView(UUID playerId, Object viewIdentity) {
    if (viewIdentity == null) {
      return null;
    }
    Session current = sessionsByPlayer.get(playerId);
    if (current == null || current.viewIdentity() != viewIdentity) {
      return null;
    }
    return removeCurrent(current);
  }

  synchronized Session closePlayer(UUID playerId) {
    Session current = sessionsByPlayer.get(playerId);
    return current == null ? null : removeCurrent(current);
  }

  synchronized Session cancel(Session session) {
    return sessionsByPlayer.get(session.playerId()) == session ? removeCurrent(session) : null;
  }

  synchronized List<Session> closeBlock(BlockKey blockKey) {
    return closeIndexed(playersByBlock.get(blockKey), blockKey, null);
  }

  synchronized List<Session> closeChunk(ChunkKey chunkKey) {
    return closeIndexed(playersByChunk.get(chunkKey), null, chunkKey);
  }

  synchronized List<Session> drain() {
    accepting = false;
    List<Session> sessions = new ArrayList<>(sessionsByPlayer.values());
    sessionsByPlayer.clear();
    playersByBlock.clear();
    playersByChunk.clear();
    chunkReferences.clear();
    return sessions;
  }

  synchronized boolean requiresTicket(ChunkKey chunkKey) {
    return chunkReferences.getOrDefault(chunkKey, 0) > 0;
  }

  synchronized int activeCount() {
    return sessionsByPlayer.size();
  }

  synchronized int chunkReferenceCount(ChunkKey chunkKey) {
    return chunkReferences.getOrDefault(chunkKey, 0);
  }

  private List<Session> closeIndexed(Set<UUID> indexedPlayers, BlockKey blockKey, ChunkKey chunkKey) {
    if (indexedPlayers == null || indexedPlayers.isEmpty()) {
      return List.of();
    }

    List<UUID> playerIds = new ArrayList<>(indexedPlayers);
    List<Session> removed = new ArrayList<>(playerIds.size());
    for (UUID playerId : playerIds) {
      Session current = sessionsByPlayer.get(playerId);
      if (current == null) {
        continue;
      }
      if (blockKey != null && !current.blockKeys().contains(blockKey)) {
        continue;
      }
      if (chunkKey != null && !current.chunkKeys().contains(chunkKey)) {
        continue;
      }
      removed.add(removeCurrent(current));
    }
    return removed;
  }

  private Session removeCurrent(Session session) {
    sessionsByPlayer.remove(session.playerId(), session);
    removeSessionIndexes(session);
    return session;
  }

  private void indexSession(Session session) {
    for (BlockKey blockKey : session.blockKeys()) {
      playersByBlock.computeIfAbsent(blockKey, ignored -> new HashSet<>()).add(session.playerId());
    }
    for (ChunkKey chunkKey : session.chunkKeys()) {
      playersByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(session.playerId());
      incrementChunk(chunkKey);
    }
  }

  private void removeSessionIndexes(Session session) {
    for (BlockKey blockKey : session.blockKeys()) {
      removeIndexedPlayer(playersByBlock, blockKey, session.playerId());
    }
    for (ChunkKey chunkKey : session.chunkKeys()) {
      removeIndexedPlayer(playersByChunk, chunkKey, session.playerId());
      decrementChunk(chunkKey);
    }
  }

  private <K> void removeIndexedPlayer(Map<K, Set<UUID>> index, K key, UUID playerId) {
    Set<UUID> players = index.get(key);
    if (players == null) {
      return;
    }
    players.remove(playerId);
    if (players.isEmpty()) {
      index.remove(key);
    }
  }

  private void incrementChunk(ChunkKey chunkKey) {
    int references = chunkReferences.getOrDefault(chunkKey, 0) + 1;
    chunkReferences.put(chunkKey, references);
  }

  private void decrementChunk(ChunkKey chunkKey) {
    int references = chunkReferences.getOrDefault(chunkKey, 0) - 1;
    if (references <= 0) {
      chunkReferences.remove(chunkKey);
      return;
    }
    chunkReferences.put(chunkKey, references);
  }

  private static <T> Set<T> immutableKeys(Set<T> keys, String name) {
    Objects.requireNonNull(keys, name);
    if (keys.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    LinkedHashSet<T> copy = new LinkedHashSet<>(keys.size());
    for (T key : keys) {
      copy.add(Objects.requireNonNull(key, name + " must not contain null"));
    }
    return Collections.unmodifiableSet(copy);
  }

  record BlockKey(UUID worldId, int x, int y, int z) {
    BlockKey {
      Objects.requireNonNull(worldId);
    }
  }

  record ChunkKey(UUID worldId, int x, int z) {
    ChunkKey {
      Objects.requireNonNull(worldId);
    }
  }

  record Registration(Session session, Session retired) {
  }

  static final class Session {
    private final long token;
    private final UUID playerId;
    private final Set<BlockKey> blockKeys;
    private final Set<ChunkKey> chunkKeys;
    private volatile Object viewIdentity;

    private Session(long token, UUID playerId, Set<BlockKey> blockKeys, Set<ChunkKey> chunkKeys) {
      this.token = token;
      this.playerId = playerId;
      this.blockKeys = blockKeys;
      this.chunkKeys = chunkKeys;
    }

    long token() {
      return token;
    }

    UUID playerId() {
      return playerId;
    }

    Set<BlockKey> blockKeys() {
      return blockKeys;
    }

    Set<ChunkKey> chunkKeys() {
      return chunkKeys;
    }

    Object viewIdentity() {
      return viewIdentity;
    }

    private void activate(Object viewIdentity) {
      this.viewIdentity = viewIdentity;
    }
  }
}
