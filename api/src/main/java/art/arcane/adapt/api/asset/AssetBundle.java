package art.arcane.adapt.api.asset;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The art an addon ships: icons, theme kits, atlases and models. One declaration feeds both deliveries: native
 * clients get the files over the Adapt channel, vanilla clients get them baked into the server resource pack.
 *
 * <p>Files are laid out as in a resource pack, below {@code assets/<namespace>/} in the addon jar:
 *
 * <pre>
 * textures/gui/skill/&lt;path&gt;.png        {@link AssetKind#SPRITE}, 16x16
 * textures/gui/adaptation/&lt;path&gt;.png   {@link AssetKind#SPRITE}, 16x16
 * textures/gui/preference/&lt;path&gt;.png   {@link AssetKind#PREFERENCE_ICON}, 16x16
 * textures/gui/emblem/&lt;path&gt;.png       {@link AssetKind#EMBLEM}
 * textures/gui/trim/&lt;path&gt;.png         {@link AssetKind#TRIM}
 * textures/gui/theme/&lt;path&gt;.png        {@link AssetKind#THEME_ATLAS}
 * textures/gui/&lt;other&gt;/&lt;path&gt;.png      {@link AssetKind#GUI_TEXTURE}, any size: kits, toasts, HUD, glyphs
 * textures/font/&lt;path&gt;.png             {@link AssetKind#GUI_TEXTURE}
 * textures/&lt;other&gt;/&lt;path&gt;.png          {@link AssetKind#OTHER}, such as item textures
 * themes/&lt;path&gt;.json                   {@link AssetKind#THEME_KIT}
 * models/&lt;path&gt;.json                   {@link AssetKind#MODEL}
 * </pre>
 *
 * <p>A PNG below {@code textures/} has the id of its path below {@code textures/} without the extension, except a
 * theme atlas, whose id is its skill's. A theme kit's id is also its skill's, a model's is its path below
 * {@code models/}, and any other file's is its path without the extension.
 *
 * <p>A theme kit and its atlas share the skill's path: {@code themes/alchemy.json} and
 * {@code textures/gui/theme/alchemy.png} theme the skill {@code <namespace>:alchemy}. An icon that is not 16x16 is
 * rejected when the addon installs.
 */
@ApiStatus.Experimental
public interface AssetBundle {
  /** The namespace every entry's id is in; must be the namespace of the addon that registers the bundle. */
  String namespace();

  /** Every file of the bundle, classified by the layout above. */
  List<AssetEntry> entries();

  /** The bytes of one of this bundle's {@link #entries()}. */
  byte[] read(AssetEntry entry);

  /**
   * The files below {@code root} in the jar that holds {@code owner}. The last segment of {@code root} is the
   * bundle's namespace, so the root is normally {@code assets/<namespace>}.
   *
   * @param owner any class of the addon, used to find its jar
   */
  static AssetBundle resources(Class<?> owner, String root) {
    Objects.requireNonNull(owner, "owner");
    String normalizedRoot = JarResources.normalizeRoot(root);
    String namespace = normalizedRoot.substring(normalizedRoot.lastIndexOf('/') + 1);
    if (!ContentIds.validNamespace(namespace)) {
      throw new IllegalArgumentException("Asset root must end in the addon namespace, got '" + root + "'");
    }
    List<AssetEntry> found = new ArrayList<>();
    for (String path : JarResources.list(owner, normalizedRoot)) {
      found.add(classify(namespace, path));
    }
    List<AssetEntry> entries = List.copyOf(found);
    return new AssetBundle() {
      @Override
      public String namespace() {
        return namespace;
      }

      @Override
      public List<AssetEntry> entries() {
        return entries;
      }

      @Override
      public byte[] read(AssetEntry entry) {
        if (!entries.contains(entry)) {
          throw new IllegalArgumentException("Not an entry of the '" + namespace + "' asset bundle: " + entry);
        }
        return JarResources.read(owner, normalizedRoot + "/" + entry.path());
      }
    };
  }

  /** The entry a file at {@code path} below a bundle root stands for, following the layout above. */
  static AssetEntry classify(String namespace, String path) {
    int dot = path.lastIndexOf('.');
    String stem = dot < 0 ? path : path.substring(0, dot);
    if (path.endsWith(".png") && stem.startsWith("textures/")) {
      String sprite = stem.substring("textures/".length());
      if (sprite.startsWith("gui/theme/")) {
        return new AssetEntry(AssetKind.THEME_ATLAS, Id.of(namespace, sprite.substring("gui/theme/".length())), path);
      }
      AssetKind kind = sprite.startsWith("gui/emblem/") ? AssetKind.EMBLEM
          : sprite.startsWith("gui/trim/") ? AssetKind.TRIM
          : sprite.startsWith("gui/preference/") ? AssetKind.PREFERENCE_ICON
          : sprite.startsWith("gui/skill/") || sprite.startsWith("gui/adaptation/") ? AssetKind.SPRITE
          : sprite.startsWith("gui/") || sprite.startsWith("font/") ? AssetKind.GUI_TEXTURE
          : AssetKind.OTHER;
      return new AssetEntry(kind, Id.of(namespace, sprite), path);
    }
    if (path.endsWith(".json") && stem.startsWith("themes/")) {
      return new AssetEntry(AssetKind.THEME_KIT, Id.of(namespace, stem.substring("themes/".length())), path);
    }
    if (path.endsWith(".json") && stem.startsWith("models/")) {
      return new AssetEntry(AssetKind.MODEL, Id.of(namespace, stem.substring("models/".length())), path);
    }
    return new AssetEntry(AssetKind.OTHER, Id.of(namespace, stem), path);
  }
}
