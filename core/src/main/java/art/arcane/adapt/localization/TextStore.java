package art.arcane.adapt.localization;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The installed message bundles and the catalog composed from them. Every change publishes a new immutable composition
 * and then tells the language runtime to rebuild its snapshots; readers on any thread see one composition or the next,
 * never a mix.
 */
public final class TextStore {
  private static final int MAX_LISTED_CONFLICTS = 8;

  private final TextBundle builtIn;
  private final Runnable changed;
  private volatile Composition composition;

  TextStore(TextBundle builtIn, Runnable changed) {
    this.builtIn = Objects.requireNonNull(builtIn, "builtIn");
    this.changed = Objects.requireNonNull(changed, "changed");
    Map<String, TextBundle> bundles = new LinkedHashMap<>();
    bundles.put(builtIn.namespace(), builtIn);
    composition = new Composition(builtIn.catalog(), Collections.unmodifiableMap(bundles), 0L);
  }

  /**
   * Installs {@code bundle}, replacing an installed bundle of the same namespace. A bundle of the built-in namespace
   * joins the engine's own messages in the built-in bundle. Throws when any key is already declared by another bundle
   * or by the engine; nothing changes then.
   */
  public void install(TextBundle bundle) {
    Objects.requireNonNull(bundle, "bundle");
    synchronized (this) {
      Map<String, TextBundle> next = new LinkedHashMap<>(composition.bundles());
      if (TextBundle.BUILT_IN.equals(bundle.namespace())) {
        next.put(TextBundle.BUILT_IN, builtIn);
        requireNoConflicts(bundle, next);
        next.put(TextBundle.BUILT_IN, TextBundle.builtIn(builtIn.catalog(), bundle));
      } else {
        next.remove(bundle.namespace());
        requireNoConflicts(bundle, next);
        next.put(bundle.namespace(), bundle);
      }
      publish(next);
    }
  }

  /**
   * Removes the bundle of {@code namespace}; for the built-in namespace, the messages its addon added, leaving the
   * engine's. False when nothing was installed.
   */
  public boolean uninstall(String namespace) {
    synchronized (this) {
      Map<String, TextBundle> next = new LinkedHashMap<>(composition.bundles());
      if (TextBundle.BUILT_IN.equals(namespace)) {
        if (next.get(namespace) == builtIn) {
          return false;
        }
        next.put(namespace, builtIn);
      } else if (next.remove(namespace) == null) {
        return false;
      }
      publish(next);
      return true;
    }
  }

  /** Installed bundles, the built-in one first, then in install order. */
  public List<TextBundle> bundles() {
    return List.copyOf(composition.bundles().values());
  }

  public Optional<TextBundle> bundle(String namespace) {
    return Optional.ofNullable(composition.bundles().get(namespace));
  }

  /** The keys of the built-in namespace: the engine's and those its addon added. */
  public MessageCatalog builtInCatalog() {
    return composition.bundles().get(TextBundle.BUILT_IN).catalog();
  }

  /** Every key of every installed bundle. */
  public MessageCatalog catalog() {
    return composition.catalog();
  }

  /** Bumped by every install and uninstall. */
  public long revision() {
    return composition.revision();
  }

  Composition composition() {
    return composition;
  }

  private void publish(Map<String, TextBundle> bundles) {
    MessageCatalog catalog;
    if (bundles.size() == 1) {
      catalog = bundles.get(TextBundle.BUILT_IN).catalog();
    } else {
      MessageCatalog.Builder builder = MessageCatalog.builder(builtIn.catalog().englishLocale());
      for (TextBundle installed : bundles.values()) {
        builder.addAll(installed.keys());
      }
      catalog = builder.build();
    }
    composition = new Composition(catalog, Collections.unmodifiableMap(new LinkedHashMap<>(bundles)), composition.revision() + 1L);
    changed.run();
  }

  private static void requireNoConflicts(TextBundle bundle, Map<String, TextBundle> others) {
    List<String> conflicts = new ArrayList<>();
    for (MessageKey key : bundle.keys()) {
      for (TextBundle other : others.values()) {
        if (other.catalog().key(key.id()) != null) {
          conflicts.add(key.id() + " (" + other.namespace() + ")");
          break;
        }
      }
    }
    if (conflicts.isEmpty()) {
      return;
    }
    List<String> listed = conflicts.subList(0, Math.min(conflicts.size(), MAX_LISTED_CONFLICTS));
    throw new IllegalArgumentException("Message bundle " + bundle.namespace() + " declares " + conflicts.size()
        + " keys other bundles already declare: " + String.join(", ", listed));
  }

  /** One published state: the composed catalog and the bundles it came from. */
  record Composition(MessageCatalog catalog, Map<String, TextBundle> bundles, long revision) {
  }
}
