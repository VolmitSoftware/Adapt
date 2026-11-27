package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.function.Supplier;

/** A player section a registrar handed out: the only kind {@link DeclaredData} accepts. */
public final class EnginePlayerSection<T> implements PlayerSection<T> {
  private final Id id;
  private final String owner;
  private final Class<T> type;
  private volatile Supplier<T> initial;

  EnginePlayerSection(Id id, String owner, Class<T> type, Supplier<T> initial) {
    this.id = Objects.requireNonNull(id, "id");
    this.owner = Objects.requireNonNull(owner, "owner");
    this.type = Objects.requireNonNull(type, "type");
    this.initial = Objects.requireNonNull(initial, "initial");
  }

  @Override
  public Id id() {
    return id;
  }

  @Override
  public Class<T> type() {
    return type;
  }

  /** The namespace of the addon that declared the section. */
  public String owner() {
    return owner;
  }

  /** The key the section is stored under in the player file: the id in full. */
  public String name() {
    return id.toString();
  }

  /**
   * A fresh value for a player whose save has none.
   *
   * @throws NullPointerException when the declared supplier returns null
   */
  public T create() {
    return Objects.requireNonNull(initial.get(), "initial value of player section " + id);
  }

  void redeclare(Supplier<T> initial) {
    this.initial = Objects.requireNonNull(initial, "initial");
  }

  @Override
  public String toString() {
    return "PlayerSection[" + id + "]";
  }
}
