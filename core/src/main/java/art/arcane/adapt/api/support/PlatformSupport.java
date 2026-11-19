package art.arcane.adapt.api.support;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.attribute.AttributeEmulator;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks {@link PlatformNeed}s against the running server and remembers the content left out because of them. Each
 * left-out piece is logged once per server run, however often its skill is rebuilt by a config hotload.
 */
public final class PlatformSupport {
  private static final Map<String, LeftOut> DISABLED = new ConcurrentHashMap<>();

  private PlatformSupport() {
  }

  /** Verdict on the running server; everything is available while Adapt is not running (offline tooling). */
  public static Verdict check(Collection<PlatformNeed> needs) {
    AdaptCore core = AdaptCore.current();
    return core == null ? Verdict.AVAILABLE : check(needs, core.getPlatform().host());
  }

  public static Verdict check(Collection<PlatformNeed> needs, PlatformHost host) {
    if (needs.isEmpty()) {
      return Verdict.AVAILABLE;
    }
    List<PlatformNeed> emulated = new ArrayList<>();
    List<PlatformNeed> partial = new ArrayList<>();
    List<PlatformNeed> missing = new ArrayList<>();
    for (PlatformNeed need : needs) {
      switch (need) {
        case PlatformNeed.Content content -> {
          if (!host.registries().exists(content.kind(), content.id())) {
            missing.add(need);
          }
        }
        case PlatformNeed.Feature feature -> {
          if (!host.supports(feature.feature())) {
            missing.add(need);
          }
        }
        case PlatformNeed.EmulatedFeature feature -> {
          if (!host.supports(feature.feature())) {
            emulated.add(need);
          }
        }
        case PlatformNeed.PartialFeature feature -> {
          if (!host.supports(feature.feature())) {
            partial.add(need);
          }
        }
        case PlatformNeed.PartialAttribute attribute -> {
          if (!host.registries().exists(RegistryKind.ATTRIBUTE, attribute.attribute())) {
            partial.add(need);
          }
        }
        case PlatformNeed.Attribute attribute -> {
          if (!host.registries().exists(RegistryKind.ATTRIBUTE, attribute.attribute())) {
            if (AttributeEmulator.emulates(attribute.attribute())) {
              emulated.add(need);
            } else {
              missing.add(need);
            }
          }
        }
      }
    }
    return emulated.isEmpty() && partial.isEmpty() && missing.isEmpty()
        ? Verdict.AVAILABLE
        : new Verdict(emulated, partial, missing);
  }

  /**
   * {@code fallback} when the running server lacks {@code preferred} but has {@code fallback}, else {@code preferred}:
   * icons and base items that use an item newer than the oldest supported Minecraft version.
   */
  public static Id itemOr(Id preferred, Id fallback) {
    AdaptCore core = AdaptCore.current();
    RegistryLookup registries = core == null ? null : core.getPlatform().host().registries();
    return registries != null && !registries.exists(RegistryKind.ITEM, preferred) && registries.exists(RegistryKind.ITEM, fallback)
        ? fallback
        : preferred;
  }

  /**
   * {@link #itemOr} for the item and block names config defaults are written with, such as {@code COPPER_CHEST}:
   * {@code fallback} when the running server has neither an item nor a block named {@code preferred} but has one
   * named {@code fallback}, else {@code preferred}.
   */
  public static String materialOr(String preferred, String fallback) {
    AdaptCore core = AdaptCore.current();
    RegistryLookup registries = core == null ? null : core.getPlatform().host().registries();
    return registries != null && !material(registries, preferred) && material(registries, fallback) ? fallback : preferred;
  }

  private static boolean material(RegistryLookup registries, String name) {
    Id id = MaterialNames.exact(name);
    return id != null && (registries.exists(RegistryKind.ITEM, id) || registries.exists(RegistryKind.BLOCK, id));
  }

  /**
   * Records content left out on this server, logging it the first time. {@code kind}, {@code owner} and
   * {@code name} identify it, for example {@code adaptation}, {@code kinetics} and {@code kinetics-windburst}.
   */
  public static void leaveOut(String kind, String owner, String name, Verdict verdict) {
    if (DISABLED.put(kind + ":" + name, new LeftOut(kind, owner, name, verdict)) == null) {
      AdaptLog.info("Disabled " + kind + " " + name + " on Minecraft " + minecraftVersion() + ": missing "
          + verdict.missingText() + ".");
    }
  }

  /** Content left out on this server so far, sorted by kind and name. */
  public static List<LeftOut> leftOut() {
    return List.copyOf(new TreeMap<>(DISABLED).values());
  }

  /** Forgets what was left out; for tests that start several servers in one JVM. */
  public static void reset() {
    DISABLED.clear();
  }

  private static String minecraftVersion() {
    AdaptCore core = AdaptCore.current();
    return core == null ? "?" : core.getPlatform().host().minecraftVersion();
  }

  public record LeftOut(String kind, String owner, String name, Verdict verdict) {
  }

  /** Needs met only through emulation, needs whose absence costs part of the content, and needs not met at all. */
  public record Verdict(List<PlatformNeed> emulated, List<PlatformNeed> partial, List<PlatformNeed> missing) {
    public static final Verdict AVAILABLE = new Verdict(List.of(), List.of(), List.of());

    public Verdict {
      emulated = List.copyOf(emulated);
      partial = List.copyOf(partial);
      missing = List.copyOf(missing);
    }

    public boolean usable() {
      return missing.isEmpty();
    }

    public boolean isEmulated() {
      return missing.isEmpty() && partial.isEmpty() && !emulated.isEmpty();
    }

    public boolean isPartial() {
      return missing.isEmpty() && !partial.isEmpty();
    }

    public String missingText() {
      return describe(missing);
    }

    public String emulatedText() {
      return describe(emulated);
    }

    /** Partial needs first, then emulated ones: everything that differs from the newest version. */
    public String partialText() {
      List<PlatformNeed> differing = new ArrayList<>(partial);
      differing.addAll(emulated);
      return describe(differing);
    }

    private static String describe(List<PlatformNeed> needs) {
      List<String> parts = new ArrayList<>(needs.size());
      for (PlatformNeed need : needs) {
        parts.add(need.describe());
      }
      return String.join(", ", parts);
    }
  }
}
