package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Registry ids written as Bukkit constant names: {@code minecraft:block.amethyst_block.chime} reads as
 * {@code BLOCK_AMETHYST_BLOCK_CHIME}. Parsing accepts that name or the id itself.
 */
final class RegistryNames {
  private RegistryNames() {
  }

  static KList<Id> ids(RegistryKind kind) {
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      return new KList<>();
    }
    List<Id> ids = new ArrayList<>(core.getPlatform().host().registries().ids(kind));
    ids.sort((Id left, Id right) -> name(left).compareTo(name(right)));
    return new KList<>(ids);
  }

  static String name(Id id) {
    String path = id.namespace().equals(Id.MINECRAFT) ? id.path() : id.namespace() + "_" + id.path();
    return path.replace('.', '_').replace('/', '_').toUpperCase(Locale.ROOT);
  }

  static Id parse(RegistryKind kind, String in) {
    if (in == null || in.isBlank()) {
      return null;
    }
    String value = in.trim();
    Set<Id> known = AdaptCore.platform().host().registries().ids(kind);
    for (Id id : known) {
      if (name(id).equals(value)) {
        return id;
      }
    }
    Id parsed = Id.tryParse(value.toLowerCase(Locale.ROOT));
    return parsed != null && AdaptCore.platform().host().registries().exists(kind, parsed) ? parsed : null;
  }
}
