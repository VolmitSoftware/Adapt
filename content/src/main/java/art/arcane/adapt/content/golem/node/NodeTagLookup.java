package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.platform.value.Id;

public interface NodeTagLookup {
  boolean exists(String tag);

  boolean tagged(String tag, Id material);
}
