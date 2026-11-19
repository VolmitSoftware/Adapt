package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.enchantments.Enchantment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry access through the server's registries. Id sets are computed once per kind and cached. Ids renamed after
 * the server's version are translated through {@link ContentAliases} both ways.
 */
public final class BukkitRegistryLookup implements RegistryLookup {
  private final Map<RegistryKind, Set<Id>> idCache = new ConcurrentHashMap<>();

  @Override
  public boolean exists(RegistryKind kind, Id id) {
    return nativeId(kind, id) != null;
  }

  @Override
  public Set<Id> ids(RegistryKind kind) {
    return idCache.computeIfAbsent(kind, (RegistryKind missing) -> modernIds(missing, BukkitRegistryVersion.ids(missing)));
  }

  @Override
  public boolean isTagged(RegistryKind kind, Id tag, Id value) {
    Id resolved = nativeId(kind, value);
    return resolved != null && BukkitRegistryVersion.isTagged(kind, tag, resolved);
  }

  @Override
  public Set<Id> tagValues(RegistryKind kind, Id tag) {
    return modernIds(kind, BukkitRegistryVersion.tagValues(kind, tag));
  }

  @Override
  public int enchantmentMaxLevel(Id enchantment) {
    Enchantment resolved = BukkitResolve.enchantment(enchantment);
    return resolved == null ? 0 : resolved.getMaxLevel();
  }

  @Override
  public String particleDataType(Id particle) {
    return BukkitRegistryVersion.particleDataType(particle);
  }

  @Override
  public EffectCategory effectCategory(Id effect) {
    return BukkitRegistryVersion.effectCategory(effect);
  }

  @Override
  public boolean isInstantEffect(Id effect) {
    return BukkitRegistryVersion.isInstantEffect(effect);
  }

  @Override
  public List<EffectInstance> potionEffects(Id potion) {
    return BukkitRegistryVersion.potionEffects(potion);
  }

  @Override
  public List<EffectModifier> effectModifiers(Id effect, int amplifier) {
    return BukkitRegistryVersion.effectModifiers(effect, amplifier);
  }

  private static Id nativeId(RegistryKind kind, Id id) {
    if (kind == null || id == null) {
      return null;
    }
    return ContentAliases.resolve(kind, id, (Id candidate) -> BukkitRegistryVersion.exists(kind, candidate));
  }

  private static Set<Id> modernIds(RegistryKind kind, Set<Id> nativeIds) {
    Set<Id> ids = new LinkedHashSet<>();
    for (Id id : nativeIds) {
      List<Id> modern = ContentAliases.modern(kind, id);
      if (modern.isEmpty()) {
        ids.add(id);
      } else {
        ids.addAll(modern);
      }
    }
    return Set.copyOf(ids);
  }
}
