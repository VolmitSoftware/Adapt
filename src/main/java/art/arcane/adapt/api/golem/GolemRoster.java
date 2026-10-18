package art.arcane.adapt.api.golem;

import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.block.BlockFace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GolemRoster {
  private static final String DIGEST_ALGORITHM = "SHA-256";

  private KMap<String, GolemEntry> golems = new KMap<>();
  private KMap<String, NodeEntry> nodes = new KMap<>();

  public static long chunkKey(int chunkX, int chunkZ) {
    return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
  }

  public static String digest(String itemState) {
    try {
      MessageDigest sha = MessageDigest.getInstance(DIGEST_ALGORITHM);
      return HexFormat.of().formatHex(sha.digest(itemState.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(DIGEST_ALGORITHM + " is not available on this Java runtime", missing);
    }
  }

  public static int chunkX(long chunkKey) {
    return (int) chunkKey;
  }

  public static int chunkZ(long chunkKey) {
    return (int) (chunkKey >>> 32);
  }

  public boolean add(Binding binding) {
    GolemEntry entry = GolemEntry.active(binding);
    return golemMap().putIfAbsent(entry.id.toString(), entry) == null;
  }

  public Optional<GolemEntry> golem(UUID id) {
    return id == null ? Optional.empty() : Optional.ofNullable(golemMap().get(id.toString()));
  }

  public List<GolemEntry> golems() {
    return List.copyOf(golemMap().values());
  }

  public int golemCount() {
    return golemMap().size();
  }

  public boolean transition(UUID id, Status from, Status to) {
    if (id == null || !from.canBecome(to)) {
      return false;
    }
    AtomicBoolean changed = new AtomicBoolean();
    golemMap().computeIfPresent(id.toString(), (key, entry) -> {
      if (entry.status() != from) {
        return entry;
      }
      changed.set(true);
      return entry.withStatus(to);
    });
    return changed.get();
  }

  public boolean stow(UUID id, Status to, String itemState) {
    if (id == null || itemState == null || (to != Status.PACKED && to != Status.SHATTERED)) {
      return false;
    }
    String sealed = digest(itemState);
    AtomicBoolean changed = new AtomicBoolean();
    golemMap().computeIfPresent(id.toString(), (key, entry) -> {
      if (entry.status() != Status.ACTIVE) {
        return entry;
      }
      changed.set(true);
      return entry.stowed(to, sealed);
    });
    return changed.get();
  }

  public boolean unstow(UUID id, Status from, String itemState) {
    if (id == null || itemState == null || (from != Status.PACKED && from != Status.SHATTERED)) {
      return false;
    }
    String sealed = digest(itemState);
    AtomicBoolean changed = new AtomicBoolean();
    golemMap().computeIfPresent(id.toString(), (key, entry) -> {
      if (entry.status() != from || !sealed.equals(entry.digest)) {
        return entry;
      }
      changed.set(true);
      return entry.withStatus(Status.ACTIVE);
    });
    return changed.get();
  }

  public boolean markMasterwork(UUID id, boolean masterwork) {
    if (id == null) {
      return false;
    }
    AtomicBoolean changed = new AtomicBoolean();
    golemMap().computeIfPresent(id.toString(), (key, entry) -> {
      if (entry.status() != Status.ACTIVE || entry.masterwork == masterwork) {
        return entry;
      }
      changed.set(true);
      return entry.withMasterwork(masterwork);
    });
    return changed.get();
  }

  public int masterworkCount() {
    int count = 0;
    for (GolemEntry entry : golemMap().values()) {
      if (entry.masterwork) {
        count++;
      }
    }
    return count;
  }

  public boolean seen(UUID id, UUID world, long chunkKey, long nowMillis) {
    if (id == null || world == null) {
      return false;
    }
    AtomicBoolean changed = new AtomicBoolean();
    golemMap().computeIfPresent(id.toString(), (key, entry) -> {
      Status status = entry.status();
      if (status != Status.ACTIVE && status != Status.MISSING) {
        return entry;
      }
      changed.set(true);
      return entry.seenAt(world, chunkKey, nowMillis);
    });
    return changed.get();
  }

  public boolean remove(UUID id) {
    return id != null && golemMap().remove(id.toString()) != null;
  }

  public boolean remove(UUID id, Status expected) {
    if (id == null) {
      return false;
    }
    AtomicBoolean removed = new AtomicBoolean();
    golemMap().computeIfPresent(id.toString(), (key, entry) -> {
      if (entry.status() != expected) {
        return entry;
      }
      removed.set(true);
      return null;
    });
    return removed.get();
  }

  public boolean addNode(NodeEntry node) {
    return nodeMap().putIfAbsent(node.key(), node) == null;
  }

  public boolean removeNode(GolemState.Anchor block, BlockFace face) {
    return nodeMap().remove(NodeEntry.key(block, face)) != null;
  }

  public List<NodeEntry> nodes() {
    return List.copyOf(nodeMap().values());
  }

  public int nodeCount() {
    return nodeMap().size();
  }

  private KMap<String, GolemEntry> golemMap() {
    KMap<String, GolemEntry> local = golems;
    if (local == null) {
      local = new KMap<>();
      golems = local;
    }
    return local;
  }

  private KMap<String, NodeEntry> nodeMap() {
    KMap<String, NodeEntry> local = nodes;
    if (local == null) {
      local = new KMap<>();
      nodes = local;
    }
    return local;
  }

  public enum Status {
    ACTIVE,
    PACKED,
    SHATTERED,
    MISSING;

    public boolean canBecome(Status next) {
      return switch (this) {
        case ACTIVE -> next == PACKED || next == SHATTERED || next == MISSING;
        case PACKED, SHATTERED, MISSING -> next == ACTIVE;
      };
    }
  }

  public record Binding(UUID id, GolemBody body, GolemCoreType core, UUID world, long chunkKey, long seenAt) {
    public Binding {
      Objects.requireNonNull(id);
      Objects.requireNonNull(body);
      Objects.requireNonNull(core);
      Objects.requireNonNull(world);
    }
  }

  public static final class GolemEntry {
    private UUID id;
    private String body;
    private String core;
    private String state;
    private UUID world;
    private long chunkKey;
    private long lastSeen;
    private String digest;
    private boolean masterwork;

    private GolemEntry() {
    }

    private static GolemEntry active(Binding binding) {
      GolemEntry entry = new GolemEntry();
      entry.id = binding.id();
      entry.body = binding.body().id();
      entry.core = binding.core().id();
      entry.state = Status.ACTIVE.name();
      entry.world = binding.world();
      entry.chunkKey = binding.chunkKey();
      entry.lastSeen = binding.seenAt();
      return entry;
    }

    public UUID id() {
      return id;
    }

    public GolemBody body() {
      return GolemBody.fromId(body).orElse(null);
    }

    public GolemCoreType core() {
      return GolemCoreType.fromId(core).orElse(GolemCoreType.BLANK);
    }

    public Status status() {
      for (Status status : Status.values()) {
        if (status.name().equals(state)) {
          return status;
        }
      }
      return Status.MISSING;
    }

    public UUID world() {
      return world;
    }

    public long chunkKey() {
      return chunkKey;
    }

    public long lastSeen() {
      return lastSeen;
    }

    public String digest() {
      return digest;
    }

    public boolean masterwork() {
      return masterwork;
    }

    public boolean holds(String itemState) {
      return digest != null && itemState != null && digest.equals(GolemRoster.digest(itemState));
    }

    private GolemEntry withStatus(Status next) {
      GolemEntry copy = copy();
      copy.state = next.name();
      copy.digest = null;
      return copy;
    }

    private GolemEntry stowed(Status next, String sealed) {
      GolemEntry copy = withStatus(next);
      copy.digest = sealed;
      return copy;
    }

    private GolemEntry withMasterwork(boolean next) {
      GolemEntry copy = copy();
      copy.masterwork = next;
      return copy;
    }

    private GolemEntry seenAt(UUID nextWorld, long nextChunkKey, long nowMillis) {
      GolemEntry copy = copy();
      copy.state = Status.ACTIVE.name();
      copy.world = nextWorld;
      copy.chunkKey = nextChunkKey;
      copy.lastSeen = nowMillis;
      copy.digest = null;
      return copy;
    }

    private GolemEntry copy() {
      GolemEntry copy = new GolemEntry();
      copy.id = id;
      copy.body = body;
      copy.core = core;
      copy.state = state;
      copy.world = world;
      copy.chunkKey = chunkKey;
      copy.lastSeen = lastSeen;
      copy.digest = digest;
      copy.masterwork = masterwork;
      return copy;
    }
  }

  public static final class NodeEntry {
    private UUID world;
    private int x;
    private int y;
    private int z;
    private String face;
    private String type;

    private NodeEntry() {
    }

    public static NodeEntry at(GolemState.Anchor block, BlockFace face, NodeType type) {
      NodeEntry node = new NodeEntry();
      node.world = block.world();
      node.x = block.x();
      node.y = block.y();
      node.z = block.z();
      node.face = face.name();
      node.type = type.id();
      return node;
    }

    public GolemState.Anchor block() {
      return GolemState.Anchor.of(world, x, y, z);
    }

    public BlockFace face() {
      for (BlockFace candidate : BlockFace.values()) {
        if (candidate.name().equals(face)) {
          return candidate;
        }
      }
      return BlockFace.SELF;
    }

    public NodeType type() {
      return NodeType.fromId(type).orElse(null);
    }

    private String key() {
      return key(block(), face());
    }

    private static String key(GolemState.Anchor block, BlockFace face) {
      return block + "@" + face.name();
    }
  }
}
