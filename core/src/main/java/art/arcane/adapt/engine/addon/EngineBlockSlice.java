package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** A block slice a registrar handed out. Slices are matched by id and type name, never by instance. */
public record EngineBlockSlice<T>(Id id, Class<T> type, Codec<T> codec) implements BlockSlice<T> {
  public EngineBlockSlice {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(codec, "codec");
  }

  @Override
  public String toString() {
    return "BlockSlice[" + id + ", " + type.getName() + "]";
  }
}
