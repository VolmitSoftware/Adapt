package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The player sections and block slices of the applied addons. A section is accepted only as the instance a registrar
 * handed out for an applied addon, so holding it is the permission to read it; a slice is accepted by id and type
 * name, and resolves to the latest declaration so an earlier install's slice object reads with the new codec.
 */
public final class DeclaredData {
  private static final Map<Id, EnginePlayerSection<?>> ISSUED = new ConcurrentHashMap<>();
  private static final Map<Id, EnginePlayerSection<?>> SECTIONS = new ConcurrentHashMap<>();
  private static final Map<Id, BlockSlice<?>> SLICES = new ConcurrentHashMap<>();
  private static final StackWalker STACK = StackWalker.getInstance();

  private DeclaredData() {
  }

  /** The section object for {@code id}: the one handed out before when the type is the same class, else a new one. */
  @SuppressWarnings("unchecked")
  static synchronized <T> EnginePlayerSection<T> issue(Id id, String owner, Class<T> type, Supplier<T> initial) {
    EnginePlayerSection<?> earlier = ISSUED.get(id);
    if (earlier != null && earlier.type() == type && earlier.owner().equals(owner)) {
      EnginePlayerSection<T> same = (EnginePlayerSection<T>) earlier;
      same.redeclare(initial);
      return same;
    }
    EnginePlayerSection<T> issued = new EnginePlayerSection<>(id, owner, type, initial);
    ISSUED.put(id, issued);
    return issued;
  }

  /** Makes the declarations of an addon that was just applied the live ones of its namespace. */
  static synchronized void commit(String namespace, List<EnginePlayerSection<?>> sections, List<BlockSlice<?>> slices) {
    retire(namespace);
    for (EnginePlayerSection<?> section : sections) {
      SECTIONS.put(section.id(), section);
    }
    for (BlockSlice<?> slice : slices) {
      SLICES.put(slice.id(), slice);
    }
  }

  /** Drops the declarations of a removed addon. */
  static synchronized void retire(String namespace) {
    SECTIONS.keySet().removeIf((Id id) -> id.namespace().equals(namespace));
    SLICES.keySet().removeIf((Id id) -> id.namespace().equals(namespace));
  }

  /** Drops every declaration; the runtime stopped. */
  static synchronized void retireAll() {
    SECTIONS.clear();
    SLICES.clear();
  }

  /**
   * The engine form of a section handed out by a registrar, whether or not its addon is applied.
   *
   * @throws IllegalStateException for a section no registrar made
   */
  @SuppressWarnings("unchecked")
  public static <T> EnginePlayerSection<T> issued(PlayerSection<T> section) {
    Objects.requireNonNull(section, "section");
    if (section instanceof EnginePlayerSection<T> issued) {
      return issued;
    }
    EnginePlayerSection<?> live = SECTIONS.get(section.id());
    String owner = live == null ? section.id().namespace() : live.owner();
    throw new IllegalStateException("Player section " + section.id() + " of addon " + owner + " was not handed out by"
        + " its registrar; refused for " + caller() + " (" + section.getClass().getName() + ")");
  }

  /**
   * The section, accepted only as the instance a registrar handed out for an applied addon.
   *
   * @throws IllegalStateException naming the section, its owner and the caller, for any other section
   */
  public static <T> EnginePlayerSection<T> requireSection(PlayerSection<T> section) {
    EnginePlayerSection<T> issued = issued(section);
    if (SECTIONS.get(issued.id()) != issued) {
      throw new IllegalStateException("Player section " + issued.id() + " of addon " + issued.owner()
          + " is not declared by an installed addon; refused for " + caller());
    }
    return issued;
  }

  /**
   * The live declaration of the slice's id.
   *
   * @throws IllegalStateException when no applied addon declares the id, or declares it with another type name
   */
  @SuppressWarnings("unchecked")
  public static <T> BlockSlice<T> requireSlice(BlockSlice<T> slice) {
    Objects.requireNonNull(slice, "slice");
    BlockSlice<?> live = SLICES.get(slice.id());
    if (live == null) {
      throw new IllegalStateException("Block slice " + slice.id() + " is not declared by an installed addon");
    }
    if (!live.type().getName().equals(slice.type().getName())) {
      throw new IllegalStateException("Block slice " + slice.id() + " is declared with type " + live.type().getName()
          + ", not " + slice.type().getName());
    }
    return (BlockSlice<T>) live;
  }

  private static String caller() {
    return STACK.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
        .filter((String name) -> !name.startsWith("art.arcane.adapt.engine.") && !name.startsWith("art.arcane.adapt.api."))
        .findFirst().orElse("unknown caller"));
  }
}
