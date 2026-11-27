package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.api.asset.AssetKind;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.platform.value.Id;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The engine's {@link AssetCatalog}. Bundles are installed and removed by namespace; each install reads and checks
 * every entry once and keeps the bytes. Icon sprites must be 16x16 PNGs, atlases PNGs and kits parse as a
 * {@link ThemeKit}; an entry that fails is skipped with one logged error and the rest of its bundle installs. Changes
 * swap one immutable snapshot, so reads never lock.
 */
public final class AdaptAssetCatalog implements AssetCatalog, AssetInstaller {
  /** Largest file a bundle may ship; the asset sync sends each file in one frame. */
  public static final int MAX_FILE_BYTES = FrameLimits.MAX_ASSET_BYTES;
  public static final int ICON_SIZE = 16;

  private final SkillLooks skills;
  private final Consumer<String> errors;
  private final Object lock = new Object();
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final Map<SkillLook, ThemeAssets> generated = new ConcurrentHashMap<>();
  private final Map<SkillLook, byte[]> generatedEmblems = new ConcurrentHashMap<>();
  private final Map<SkillLook, byte[]> generatedTrims = new ConcurrentHashMap<>();
  private volatile Snapshot snapshot = Snapshot.EMPTY;
  private List<SkillLook> seenSkills = List.of();

  /**
   * @param skills the registered skills and the colour and icon of each
   * @param errors where a skipped entry or a failing listener is reported
   */
  public AdaptAssetCatalog(SkillLooks skills, Consumer<String> errors) {
    this.skills = Objects.requireNonNull(skills, "skills");
    this.errors = Objects.requireNonNull(errors, "errors");
  }

  /** One accepted file: the resource id clients load it by, such as {@code adapt:textures/gui/skill/agility.png}. */
  public record StoredFile(String resource, AssetEntry entry, String hash, byte[] bytes) {
  }

  private record Stored(InstalledBundle bundle, List<StoredFile> files) {
  }

  private record Snapshot(long revision, List<Stored> bundles, Map<Id, byte[]> sprites, Map<Id, ThemeAssets> themes,
                          Map<String, StoredFile> files) {
    static final Snapshot EMPTY = new Snapshot(0L, List.of(), Map.of(), Map.of(), Map.of());
  }

  private record Listener(AssetListener listener) {
  }

  @Override
  public InstalledBundle install(AssetBundle bundle, String addon) {
    Objects.requireNonNull(bundle, "bundle");
    Objects.requireNonNull(addon, "addon");
    String namespace = bundle.namespace();
    List<StoredFile> files = new ArrayList<>();
    List<AssetEntry> accepted = new ArrayList<>();
    StringBuilder digest = new StringBuilder();
    for (AssetEntry entry : bundle.entries()) {
      byte[] bytes = read(bundle, entry);
      if (bytes == null) {
        continue;
      }
      String hash = AssetHashes.sha1(bytes);
      files.add(new StoredFile(namespace + ':' + entry.path(), entry, hash, bytes));
      accepted.add(entry);
      digest.append(entry.path()).append('\0').append(hash).append('\n');
    }
    InstalledBundle installed = new InstalledBundle(namespace, addon,
        AssetHashes.sha1(digest.toString().getBytes(StandardCharsets.UTF_8)), accepted);
    long revision;
    synchronized (lock) {
      List<Stored> bundles = new ArrayList<>(snapshot.bundles());
      Stored stored = new Stored(installed, List.copyOf(files));
      int at = indexOf(bundles, namespace);
      if (at >= 0) {
        bundles.set(at, stored);
      } else if (Id.ADAPT.equals(namespace)) {
        bundles.add(0, stored);
      } else {
        bundles.add(stored);
      }
      revision = publish(bundles);
    }
    notify(revision);
    return installed;
  }

  @Override
  public boolean uninstall(String namespace) {
    long revision;
    synchronized (lock) {
      List<Stored> bundles = new ArrayList<>(snapshot.bundles());
      int at = indexOf(bundles, namespace);
      if (at < 0) {
        return false;
      }
      bundles.remove(at);
      revision = publish(bundles);
    }
    notify(revision);
    return true;
  }

  @Override
  public void skillsChanged() {
    List<SkillLook> looks = new ArrayList<>();
    for (Id skill : skills.ids()) {
      skills.look(skill).ifPresent(looks::add);
    }
    long revision;
    synchronized (lock) {
      if (looks.equals(seenSkills)) {
        return;
      }
      seenSkills = List.copyOf(looks);
      revision = publish(new ArrayList<>(snapshot.bundles()));
    }
    notify(revision);
  }

  @Override
  public Map<String, StoredFile> files() {
    return snapshot.files();
  }

  @Override
  public long revision() {
    return snapshot.revision();
  }

  @Override
  public List<InstalledBundle> bundles() {
    List<InstalledBundle> bundles = new ArrayList<>();
    for (Stored stored : snapshot.bundles()) {
      bundles.add(stored.bundle());
    }
    return List.copyOf(bundles);
  }

  @Override
  public Optional<byte[]> sprite(Id sprite) {
    byte[] bytes = sprite == null ? null : snapshot.sprites().get(sprite);
    return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
  }

  @Override
  public Optional<ThemeAssets> theme(Id skill) {
    if (skill == null) {
      return Optional.empty();
    }
    ThemeAssets authored = snapshot.themes().get(skill);
    if (authored != null) {
      return Optional.of(authored);
    }
    return skills.look(skill).map((SkillLook look) -> generated.computeIfAbsent(look, ThemeGenerator::generate));
  }

  @Override
  public Optional<byte[]> emblem(Id skill) {
    return art(skill, "emblem", generatedEmblems, MutationArtGenerator::emblem);
  }

  @Override
  public Optional<byte[]> trim(Id skill) {
    return art(skill, "trim", generatedTrims, MutationArtGenerator::trim);
  }

  private Optional<byte[]> art(Id skill, String kind, Map<SkillLook, byte[]> cache, Function<SkillLook, byte[]> draw) {
    if (skill == null) {
      return Optional.empty();
    }
    Optional<byte[]> authored = sprite(Id.of(skill.namespace(), "gui/" + kind + "/" + IconSprites.file(skill)));
    if (authored.isPresent() || Id.ADAPT.equals(skill.namespace())) {
      return authored;
    }
    return skills.look(skill).map((SkillLook look) -> cache.computeIfAbsent(look, draw).clone());
  }

  @Override
  public List<Id> themes() {
    Set<Id> themed = new LinkedHashSet<>(skills.ids());
    themed.addAll(snapshot.themes().keySet().stream().sorted().toList());
    return List.copyOf(themed);
  }

  @Override
  public AssetSubscription subscribe(AssetListener listener) {
    Listener entry = new Listener(Objects.requireNonNull(listener, "listener"));
    listeners.add(entry);
    return () -> listeners.remove(entry);
  }

  private byte[] read(AssetBundle bundle, AssetEntry entry) {
    String namespace = bundle.namespace();
    if (!namespace.equals(entry.id().namespace())) {
      return skip(namespace, entry, "its id " + entry.id() + " is outside the bundle's namespace");
    }
    byte[] bytes;
    try {
      bytes = bundle.read(entry);
    } catch (RuntimeException failure) {
      return skip(namespace, entry, "it cannot be read: " + failure.getMessage());
    }
    if (bytes == null) {
      return skip(namespace, entry, "it has no content");
    }
    if (bytes.length > MAX_FILE_BYTES) {
      return skip(namespace, entry, "it is " + bytes.length + " bytes, over " + MAX_FILE_BYTES);
    }
    String problem = problem(entry.kind(), namespace, bytes);
    return problem == null ? bytes : skip(namespace, entry, problem);
  }

  /**
   * Why the bytes cannot serve as {@code kind} in {@code namespace}, or null when they can. Reads only the PNG header of
   * an image.
   */
  static String problem(AssetKind kind, String namespace, byte[] bytes) {
    switch (kind) {
      case SPRITE, PREFERENCE_ICON, EMBLEM, TRIM -> {
        int[] size = PngImages.size(bytes);
        if (size == null) {
          return "it is not a PNG";
        }
        if (size[0] != ICON_SIZE || size[1] != ICON_SIZE) {
          return "an icon must be " + ICON_SIZE + "x" + ICON_SIZE + ", it is " + size[0] + "x" + size[1];
        }
        return null;
      }
      case THEME_ATLAS -> {
        return PngImages.size(bytes) == null ? "it is not a PNG" : null;
      }
      case THEME_KIT -> {
        try {
          ThemeKit.parse(namespace, new String(bytes, StandardCharsets.UTF_8));
          return null;
        } catch (IllegalArgumentException broken) {
          return broken.getMessage();
        }
      }
      default -> {
        return null;
      }
    }
  }

  private byte[] skip(String namespace, AssetEntry entry, String reason) {
    errors.accept("Skipped asset " + entry.path() + " of bundle '" + namespace + "': " + reason);
    return null;
  }

  private static int indexOf(List<Stored> bundles, String namespace) {
    for (int index = 0; index < bundles.size(); index++) {
      if (bundles.get(index).bundle().namespace().equals(namespace)) {
        return index;
      }
    }
    return -1;
  }

  /** Builds and publishes the snapshot of {@code bundles}; call under the lock. */
  private long publish(List<Stored> bundles) {
    Map<Id, byte[]> sprites = new HashMap<>();
    Map<Id, byte[]> kits = new HashMap<>();
    Map<Id, byte[]> atlases = new HashMap<>();
    Map<String, StoredFile> files = new LinkedHashMap<>();
    for (Stored stored : bundles) {
      for (StoredFile file : stored.files()) {
        files.put(file.resource(), file);
        AssetEntry entry = file.entry();
        switch (entry.kind()) {
          case THEME_KIT -> kits.put(entry.id(), file.bytes());
          case THEME_ATLAS -> atlases.put(entry.id(), file.bytes());
          default -> {
            if (entry.path().endsWith(".png")) {
              sprites.put(entry.id(), file.bytes());
            }
          }
        }
      }
    }
    Map<Id, ThemeAssets> themes = new HashMap<>();
    for (Map.Entry<Id, byte[]> kit : kits.entrySet()) {
      byte[] atlas = atlases.get(kit.getKey());
      if (atlas != null) {
        themes.put(kit.getKey(), new ThemeAssets(kit.getKey(), kit.getValue(), atlas, false,
            AssetHashes.sha1(kit.getValue(), atlas)));
      }
    }
    long revision = snapshot.revision() + 1;
    snapshot = new Snapshot(revision, List.copyOf(bundles), Map.copyOf(sprites), Map.copyOf(themes),
        Collections.unmodifiableMap(files));
    return revision;
  }

  private void notify(long revision) {
    for (Listener entry : listeners) {
      try {
        entry.listener().changed(this);
      } catch (RuntimeException failure) {
        errors.accept("An asset listener failed at revision " + revision + ": " + failure);
      }
    }
  }
}
