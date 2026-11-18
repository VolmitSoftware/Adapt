package art.arcane.adapt.util.reflect.registries;

import art.arcane.volmlib.util.collection.Pair;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class RegistryUtilTest {
  @BeforeAll
  static void initializeRegistryConstants() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      assertThat(Registry.SOUNDS).isNotNull();
    }
  }

  @Test
  void pairKeepsBothHalvesIncludingNulls() {
    Pair<String, Integer> pair = new Pair<String, Integer>("a", 1);
    Pair<String, Integer> empty = new Pair<String, Integer>(null, null);

    assertThat(pair.getA()).isEqualTo("a");
    assertThat(pair.getB()).isEqualTo(1);
    assertThat(empty.getA()).isNull();
    assertThat(empty.getB()).isNull();
  }

  @Test
  void pairEqualityAndHashFollowBothHalves() {
    Pair<String, Integer> pair = new Pair<String, Integer>("a", 1);

    assertThat(pair).isEqualTo(new Pair<String, Integer>("a", 1)).hasSameHashCodeAs(new Pair<String, Integer>("a", 1));
    assertThat(pair).isNotEqualTo(new Pair<String, Integer>("a", 2));
    assertThat(pair).isNotEqualTo(new Pair<String, Integer>("b", 1));
    assertThat(pair).isNotEqualTo(new Pair<Integer, String>(1, "a"));
    assertThat(pair).isNotEqualTo(null);
    assertThat(pair).isNotEqualTo("a");
    assertThat(new Pair<String, Integer>("a", null)).isEqualTo(new Pair<String, Integer>("a", null))
        .hasSameHashCodeAs(new Pair<String, Integer>("a", null))
        .isNotEqualTo(new Pair<String, Integer>("a", 1));
    assertThat(new Pair<String, Integer>(null, null)).isEqualTo(new Pair<String, Integer>(null, null))
        .hasSameHashCodeAs(new Pair<String, Integer>(null, null));
  }

  @Test
  void pairWorksAsAHashMapKey() {
    Map<Pair<NamespacedKey, String>, String> values = new HashMap<>();
    values.put(new Pair<NamespacedKey, String>(NamespacedKey.minecraft("stone"), "block"), "first");
    values.put(new Pair<NamespacedKey, String>(NamespacedKey.minecraft("stone"), "block"), "second");
    values.put(new Pair<NamespacedKey, String>(NamespacedKey.minecraft("stone"), null), "third");

    assertThat(values).hasSize(2);
    assertThat(values.get(new Pair<NamespacedKey, String>(NamespacedKey.minecraft("stone"), "block"))).isEqualTo("second");
    assertThat(values.get(new Pair<NamespacedKey, String>(NamespacedKey.minecraft("stone"), null))).isEqualTo("third");
  }

  @Test
  void fieldLookupFindsPublicStaticKeyedConstants() {
    assertThat(byField(Catalog.class, minecraft("alpha"))).containsSame(Catalog.ALPHA);
    assertThat(byField(Catalog.class, minecraft("beta"))).containsSame(Catalog.BETA);
    assertThat(byField(Catalog.class, new NamespacedKey("adapt", "custom"))).containsSame(Catalog.CUSTOM);
  }

  @Test
  void fieldLookupKeysByTheValueKeyNotTheFieldName() {
    assertThat(byField(Catalog.class, minecraft("renamed"))).containsSame(Catalog.OLD_NAME);
    assertThat(byField(Catalog.class, minecraft("old_name"))).isEmpty();
  }

  @Test
  void fieldLookupReturnsTheFirstKeyThatResolves() {
    assertThat(byField(Catalog.class, minecraft("missing"), minecraft("beta"), minecraft("alpha"))).containsSame(Catalog.BETA);
    assertThat(byField(Catalog.class, minecraft("missing"), minecraft("also_missing"))).isEmpty();
    assertThat(byField(Catalog.class)).isEmpty();
  }

  @Test
  void fieldLookupMatchesNamespaceExactly() {
    assertThat(byField(Catalog.class, new NamespacedKey("adapt", "alpha"))).isEmpty();
    assertThat(byField(Catalog.class, minecraft("custom"))).isEmpty();
  }

  @Test
  void fieldLookupSkipsFieldsThatAreNotPublicStaticKeyed() {
    assertThat(byField(Catalog.class, minecraft("package_private"))).isEmpty();
    assertThat(byField(Catalog.class, minecraft("private"))).isEmpty();
    assertThat(byField(Catalog.class, minecraft("instance"))).isEmpty();
    assertThat(byField(Catalog.class, minecraft("text"))).isEmpty();
  }

  @Test
  void fieldLookupSkipsNullConstantsAndThrowingKeys() {
    assertThat(Faulty.NULL_CONSTANT).isNull();
    assertThat(byField(Faulty.class, minecraft("good"))).containsSame(Faulty.GOOD);
    assertThat(byField(Faulty.class, minecraft("after"))).containsSame(Faulty.AFTER);
    assertThat(byField(Faulty.class, minecraft("throwing"))).isEmpty();
  }

  @Test
  void fieldLookupToleratesAConstantWithANullKey() {
    assertThat(byField(NullKeyed.class, minecraft("good"))).containsSame(NullKeyed.GOOD);
    assertThat(byField(NullKeyed.class, minecraft("null"))).isEmpty();
  }

  @Test
  void fieldLookupRejectsTwoConstantsSharingAKeyEveryTime() {
    assertThatThrownBy(() -> byField(Duplicated.class, minecraft("same")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate key minecraft:same");
    assertThatThrownBy(() -> byField(Duplicated.class, minecraft("other")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate key minecraft:same");
  }

  @Test
  void fieldLookupReadsConstantsOnceAndServesLaterCallsFromTheCache() {
    Keyed original = Reassigned.SLOT;
    assertThat(byField(Reassigned.class, minecraft("original"))).containsSame(original);

    Reassigned.SLOT = keyed(minecraft("replacement"));

    assertThat(byField(Reassigned.class, minecraft("original"))).containsSame(original);
    assertThat(byField(Reassigned.class, minecraft("replacement"))).isEmpty();
  }

  @Test
  void fieldLookupAsksEachConstantForItsKeyOnlyOnce() {
    assertThat(byField(Counted.class, minecraft("counted"))).containsSame(Counted.ONLY);
    assertThat(byField(Counted.class, minecraft("counted"))).containsSame(Counted.ONLY);
    assertThat(byField(Counted.class, minecraft("missing"))).isEmpty();

    assertThat(Counted.ONLY.keyRequests).isEqualTo(1);
  }

  @Test
  void fieldLookupCachesEachTypeSeparately() {
    assertThat(byField(Catalog.class, minecraft("alpha"))).containsSame(Catalog.ALPHA);
    assertThat(byField(Shadow.class, minecraft("alpha"))).containsSame(Shadow.ALPHA);
    assertThat(Shadow.ALPHA).isNotSameAs(Catalog.ALPHA);
    assertThat(byField(Shadow.class, minecraft("beta"))).isEmpty();
  }

  @Test
  void enumLookupKeysConstantsByLowercaseFieldName() {
    assertThat(RegistryUtil.findByEnum(Legacy.class, minecraft("first_value"))).contains(Legacy.FIRST_VALUE);
    assertThat(RegistryUtil.findByEnum(Legacy.class, minecraft("missing"), minecraft("second"))).contains(Legacy.SECOND);
    assertThat(RegistryUtil.findByEnum(Legacy.class, minecraft("first"))).isEmpty();
    assertThat(RegistryUtil.findByEnum(Legacy.class, new NamespacedKey("adapt", "second"))).isEmpty();
  }

  @Test
  void defaultLookupPrefersFieldKeysOverFieldNames() {
    RegistryUtil.Lookup<Catalog> lookup = RegistryUtil.defaultLookup();

    assertThat(lookup.find(Catalog.class, minecraft("renamed"))).containsSame(Catalog.OLD_NAME);
    assertThat(lookup.find(Catalog.class, minecraft("old_name"))).containsSame(Catalog.OLD_NAME);
    assertThat(lookup.find(Catalog.class, minecraft("alpha"))).containsSame(Catalog.ALPHA);
    assertThat(lookup.find(Catalog.class, minecraft("missing"))).isEmpty();
  }

  @Test
  void combinedLookupStopsAtTheFirstHit() {
    List<String> asked = new ArrayList<>();
    RegistryUtil.Lookup<Catalog> first = (type, keys) -> {
      asked.add("first");
      return Optional.empty();
    };
    RegistryUtil.Lookup<Catalog> second = (type, keys) -> {
      asked.add("second");
      return Optional.of(Catalog.BETA);
    };
    RegistryUtil.Lookup<Catalog> third = (type, keys) -> {
      asked.add("third");
      return Optional.of(Catalog.ALPHA);
    };

    assertThat(RegistryUtil.Lookup.combine(first, second, third).find(Catalog.class, minecraft("any"))).containsSame(Catalog.BETA);
    assertThat(asked).containsExactly("first", "second");
    assertThatThrownBy(RegistryUtil.Lookup::combine).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findResolvesStringKeysInTheMinecraftNamespace() {
    assertThat(RegistryUtil.find(Catalog.class, "alpha")).isSameAs(Catalog.ALPHA);
    assertThat(RegistryUtil.find(Catalog.class, "missing", "beta")).isSameAs(Catalog.BETA);
    assertThat(RegistryUtil.findOptional(Catalog.class, "renamed")).containsSame(Catalog.OLD_NAME);
    assertThat(RegistryUtil.findNullable(Catalog.class, "alpha")).isSameAs(Catalog.ALPHA);
    assertThat(RegistryUtil.findNullable(Catalog.class, "missing")).isNull();
  }

  @Test
  void findResolvesNamespacedKeys() {
    assertThat(RegistryUtil.find(Catalog.class, new NamespacedKey("adapt", "custom"))).isSameAs(Catalog.CUSTOM);
    assertThat(RegistryUtil.findOptional(Catalog.class, minecraft("missing"), minecraft("alpha"))).containsSame(Catalog.ALPHA);
    assertThat(RegistryUtil.findOptional(Catalog.class, minecraft("missing"))).isEmpty();
  }

  @Test
  void findFallsBackToEnumStyleConstantsForTypesWithoutKeys() {
    assertThat(RegistryUtil.find(Legacy.class, "first_value")).isSameAs(Legacy.FIRST_VALUE);
    assertThat(RegistryUtil.findNullable(Legacy.class, "missing", "second")).isSameAs(Legacy.SECOND);
    assertThat(RegistryUtil.findNullable(Legacy.class, "missing")).isNull();
  }

  @Test
  void findWithoutALookupOnlyConsultsServerRegistries() {
    assertThat(RegistryUtil.findOptional(Catalog.class, (RegistryUtil.Lookup<Catalog>) null, minecraft("alpha"))).isEmpty();
    assertThat(RegistryUtil.findOptional(Catalog.class, (RegistryUtil.Lookup<Catalog>) null, "alpha")).isEmpty();
  }

  @Test
  void findUsesTheGivenLookupWithTheRequestedKeys() {
    List<NamespacedKey> seen = new ArrayList<>();
    RegistryUtil.Lookup<Catalog> lookup = (type, keys) -> {
      seen.addAll(List.of(keys));
      return Optional.of(Catalog.CUSTOM);
    };

    assertThat(RegistryUtil.find(Catalog.class, lookup, "alpha", "beta")).isSameAs(Catalog.CUSTOM);
    assertThat(seen).containsExactly(minecraft("alpha"), minecraft("beta"));
  }

  @Test
  void findReportsTheTypeAndKeysThatWereMissing() {
    assertThatThrownBy(() -> RegistryUtil.find(Catalog.class, "missing", "gone"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No Catalog found for keys: [missing, gone]");
    assertThatThrownBy(() -> RegistryUtil.find(Catalog.class, minecraft("missing")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No Catalog found for keys: [minecraft:missing]");
  }

  @Test
  void findNeedsAtLeastOneKey() {
    assertThatThrownBy(() -> RegistryUtil.findOptional(Catalog.class, new NamespacedKey[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Need at least one key");
    assertThatThrownBy(() -> RegistryUtil.findOptional(Catalog.class, new String[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Need at least one key");
  }

  private static NamespacedKey minecraft(String value) {
    return NamespacedKey.minecraft(value);
  }

  @SuppressWarnings("unchecked")
  private static Optional<Object> byField(Class<?> type, NamespacedKey... keys) {
    return (Optional<Object>) (Optional<?>) RegistryUtil.findByField(type, keys);
  }

  private static Keyed keyed(NamespacedKey key) {
    return new Entry(key);
  }

  private record Entry(NamespacedKey key) implements Keyed {
    @Override
    public @NotNull NamespacedKey getKey() {
      return key;
    }
  }

  public static final class Catalog implements Keyed {
    public static final Catalog ALPHA = new Catalog(minecraft("alpha"));
    public static final Catalog BETA = new Catalog(minecraft("beta"));
    public static final Catalog CUSTOM = new Catalog(new NamespacedKey("adapt", "custom"));
    public static final Catalog OLD_NAME = new Catalog(minecraft("renamed"));
    public static final String TEXT = "text";
    static final Catalog PACKAGE_PRIVATE = new Catalog(minecraft("package_private"));
    private static final Catalog PRIVATE = new Catalog(minecraft("private"));
    public final Catalog instance;
    private final NamespacedKey key;

    private Catalog(NamespacedKey key) {
      this.key = key;
      this.instance = key.getKey().equals("alpha") ? new Catalog(minecraft("instance")) : null;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
      return key;
    }
  }

  public static final class Shadow {
    public static final Keyed ALPHA = keyed(minecraft("alpha"));
  }

  public static final class Faulty {
    public static final Keyed GOOD = keyed(minecraft("good"));
    public static final Keyed NULL_CONSTANT = null;
    public static final Keyed THROWING = new Keyed() {
      @Override
      public @NotNull NamespacedKey getKey() {
        throw new IllegalStateException("key is not available");
      }
    };
    public static final Keyed AFTER = keyed(minecraft("after"));
  }

  public static final class NullKeyed {
    public static final Keyed GOOD = keyed(minecraft("good"));
    public static final Keyed NO_KEY = keyed(null);
  }

  public static final class Duplicated {
    public static final Keyed ONE = keyed(minecraft("same"));
    public static final Keyed TWO = keyed(minecraft("same"));
    public static final Keyed OTHER = keyed(minecraft("other"));
  }

  public static final class Reassigned {
    public static Keyed SLOT = keyed(minecraft("original"));
  }

  public static final class Counted implements Keyed {
    public static final Counted ONLY = new Counted();
    private int keyRequests;

    @Override
    public @NotNull NamespacedKey getKey() {
      keyRequests++;
      return minecraft("counted");
    }
  }

  public enum Legacy {
    FIRST_VALUE,
    SECOND
  }
}
