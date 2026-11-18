package art.arcane.adapt.api.golem.node;

public enum NodePlacementVerdict {
  READY,
  UNAVAILABLE,
  NOT_LOADED,
  OCCUPIED,
  PROTECTED,
  NEEDS_SIDE_FACE,
  NEEDS_CONTAINER,
  CONTAINER_PROTECTED,
  CHUNK_FULL,
  NODE_CAP
}
