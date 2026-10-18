package art.arcane.adapt.api.golem.node;

import org.bukkit.block.BlockFace;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class NodeFaces {
  public static final NodeFaces EMPTY = new NodeFaces(new EnumMap<>(BlockFace.class));

  private final Map<BlockFace, NodeRecord> records;

  private NodeFaces(EnumMap<BlockFace, NodeRecord> records) {
    this.records = Collections.unmodifiableMap(records);
  }

  public NodeRecord get(BlockFace face) {
    return records.get(face);
  }

  public boolean has(BlockFace face) {
    return records.containsKey(face);
  }

  public NodeFaces with(NodeRecord record) {
    EnumMap<BlockFace, NodeRecord> next = copy();
    next.put(record.face(), record);
    return new NodeFaces(next);
  }

  public NodeFaces without(BlockFace face) {
    if (!records.containsKey(face)) {
      return this;
    }
    EnumMap<BlockFace, NodeRecord> next = copy();
    next.remove(face);
    return next.isEmpty() ? EMPTY : new NodeFaces(next);
  }

  public boolean isEmpty() {
    return records.isEmpty();
  }

  public int size() {
    return records.size();
  }

  public Collection<NodeRecord> records() {
    return records.values();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof NodeFaces faces && records.equals(faces.records);
  }

  @Override
  public int hashCode() {
    return records.hashCode();
  }

  @Override
  public String toString() {
    return "NodeFaces" + records.values();
  }

  private EnumMap<BlockFace, NodeRecord> copy() {
    EnumMap<BlockFace, NodeRecord> next = new EnumMap<>(BlockFace.class);
    next.putAll(records);
    return next;
  }
}
