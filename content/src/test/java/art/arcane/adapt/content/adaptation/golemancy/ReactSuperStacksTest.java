package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.engine.integration.ReactSuperStackPort;

import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ReactSuperStacksTest {
  private final AtomicBoolean reactEnabled = new AtomicBoolean(true);
  private final AtomicInteger capacity = new AtomicInteger(64);
  private final AtomicInteger carried = new AtomicInteger();
  private final List<ItemRef> carriedStacks = new ArrayList<>();

  @BeforeEach
  void setUp() {
    TestCores.install();
    AdaptPorts.install(ReactSuperStackPort.class, ReactBundles.port(reactEnabled::get));
  }

  @AfterEach
  void close() {
    AdaptPorts.uninstall(ReactSuperStackPort.class);
    TestCores.clear();
  }

  @Test
  void aBundleWithExactlyTheMarkerLoreIsASuperStackWhileReactIsEnabled() {
    ItemRef bundle = ReactBundles.superStack(wheat(4));

    assertThat(ReactSuperStacks.isSuperStack(bundle)).isTrue();
  }

  @Test
  void withoutReactEnabledNoBundleIsASuperStack() {
    ItemRef bundle = ReactBundles.superStack(wheat(4));
    reactEnabled.set(false);

    assertThat(ReactSuperStacks.isSuperStack(bundle)).isFalse();
    assertThat(ReactSuperStacks.contents(bundle)).isEmpty();
    assertThat(ReactSuperStacks.hollow(bundle)).isFalse();
    assertThat(ReactSuperStacks.accepts(bundle, type -> type.equals(Id.minecraft("wheat")))).isFalse();
  }

  @Test
  void nearMissLoreIsNotASuperStack() {
    List<List<String>> nearMisses = List.of(
        List.of("REACT SUPER STACK "),
        List.of(" REACT SUPER STACK"),
        List.of("react super stack"),
        List.of("REACT SUPER STACKS"),
        List.of("REACT SUPER STACK", "REACT SUPER STACK"),
        List.of("REACT SUPER STACK", "extra"),
        List.of("extra", "REACT SUPER STACK"),
        List.of());
    for (List<String> lore : nearMisses) {
      assertThat(ReactSuperStacks.isSuperStack(ReactBundles.bundle(lore, 1, wheat(4)))).as("lore %s", lore).isFalse();
    }
  }

  @Test
  void aBundleWithNoLoreAtAllIsNotASuperStack() {
    ItemRef bundle = ReactBundles.superStack(wheat(4));
    bundle.setLore(List.of());

    assertThat(ReactSuperStacks.isSuperStack(bundle)).isFalse();
  }

  @Test
  void aNonBundleOrMissingStackIsNotASuperStack() {
    assertThat(ReactSuperStacks.isSuperStack(wheat(4))).isFalse();
    assertThat(ReactSuperStacks.isSuperStack(null)).isFalse();
    assertThat(ReactSuperStacks.hollow(wheat(4))).isFalse();
    assertThat(ReactSuperStacks.contents(wheat(4))).isEmpty();
  }

  @Test
  void contentsAreCopiesInBundleOrderWithoutEmptyEntries() {
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 0), GolemTestItems.stack(Id.minecraft("diamond"), 2));

    List<ItemRef> contents = ReactSuperStacks.contents(bundle);

    assertThat(ReactBundles.summary(contents)).containsExactly("WHEATx4", "DIAMONDx2");
    contents.getFirst().setAmount(1);
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle))).containsExactly("WHEATx4", "CARROTx0", "DIAMONDx2");
  }

  @Test
  void aSuperStackWithNothingToUnpackIsHollow() {
    assertThat(ReactSuperStacks.hollow(ReactBundles.superStack())).isTrue();
    assertThat(ReactSuperStacks.hollow(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("wheat"), 0)))).isTrue();
    assertThat(ReactSuperStacks.hollow(ReactBundles.bundle(List.of(ReactBundles.MARKER), 2, wheat(4)))).isTrue();
    assertThat(ReactSuperStacks.contents(ReactBundles.bundle(List.of(ReactBundles.MARKER), 2, wheat(4)))).isEmpty();
    assertThat(ReactSuperStacks.hollow(ReactBundles.superStack(wheat(4)))).isFalse();
  }

  @Test
  void aSuperStackIsAcceptedWhenAnyContentIsAndAPlainStackByItsOwnType() {
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("diamond"), 2));

    assertThat(ReactSuperStacks.accepts(bundle, type -> type.equals(Id.minecraft("diamond")))).isTrue();
    assertThat(ReactSuperStacks.accepts(bundle, type -> type.equals(Id.minecraft("bundle")))).isFalse();
    assertThat(ReactSuperStacks.accepts(bundle, type -> type.equals(Id.minecraft("coal")))).isFalse();
    assertThat(ReactSuperStacks.accepts(wheat(4), type -> type.equals(Id.minecraft("wheat")))).isTrue();
    assertThat(ReactSuperStacks.accepts(wheat(4), type -> type.equals(Id.minecraft("carrot")))).isFalse();
    ItemRef real = ReactBundles.bundle(List.of("mine"), 1, wheat(4));
    assertThat(ReactSuperStacks.accepts(real, type -> type.equals(Id.minecraft("bundle")))).isTrue();
    assertThat(ReactSuperStacks.accepts(real, type -> type.equals(Id.minecraft("wheat")))).isFalse();
  }

  @Test
  void aBundleWithRoomForEverythingIsUnpackedInOrderAndLeavesNothing() {
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 3), GolemTestItems.stack(Id.minecraft("diamond"), 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> true);

    assertThat(unpacked.carried()).isEqualTo(9);
    assertThat(unpacked.leftovers()).isEmpty();
    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx3", "DIAMONDx2");
    assertThat(carriedStacks).noneMatch(stack -> stack.type().equals(Id.minecraft("bundle")));
  }

  @Test
  void aBundleBiggerThanTheRoomKeepsTheRestInOrder() {
    capacity.set(5);
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 4), GolemTestItems.stack(Id.minecraft("diamond"), 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> true);

    assertThat(unpacked.carried()).isEqualTo(5);
    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx1");
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("CARROTx3", "DIAMONDx2");
  }

  @Test
  void aFullGolemUnpacksNothing() {
    carried.set(64);
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 4));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> true);

    assertThat(unpacked.carried()).isZero();
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("WHEATx4", "CARROTx4");
    assertThat(carriedStacks).isEmpty();
  }

  @Test
  void contentsTheAreaRejectsStayInTheBundleInTheirPlace() {
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 3), GolemTestItems.stack(Id.minecraft("diamond"), 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> !type.equals(Id.minecraft("carrot")));

    assertThat(unpacked.carried()).isEqualTo(6);
    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "DIAMONDx2");
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("CARROTx3");
  }

  @Test
  void whatTheCargoRefusesStaysInTheBundle() {
    GolemContext golem = golem();
    doAnswer(invocation -> {
      ItemRef stack = invocation.getArgument(0);
      if (stack.type().equals(Id.minecraft("carrot"))) {
        return Optional.of(stack);
      }
      carried.addAndGet(stack.amount());
      carriedStacks.add(stack);
      return Optional.empty();
    }).when(golem).carry(any(ItemRef.class));
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 3), GolemTestItems.stack(Id.minecraft("diamond"), 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem, bundle, type -> true);

    assertThat(unpacked.carried()).isEqualTo(6);
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("CARROTx3");
  }

  @Test
  void unpackingNeverDuplicatesOrLosesAnItem() {
    Id[] types = {Id.minecraft("wheat"), Id.minecraft("carrot"), Id.minecraft("diamond"), Id.minecraft("coal"), Id.minecraft("stick")};
    for (int seed = 0; seed < 150; seed++) {
      Random random = new Random(seed);
      carried.set(0);
      carriedStacks.clear();
      capacity.set(random.nextInt(80));
      int entries = 1 + random.nextInt(6);
      ItemRef[] items = new ItemRef[entries];
      int total = 0;
      for (int i = 0; i < entries; i++) {
        int amount = 1 + random.nextInt(64);
        total += amount;
        items[i] = GolemTestItems.stack(types[random.nextInt(types.length)], amount);
      }
      Id refused = types[random.nextInt(types.length)];
      boolean filtering = random.nextBoolean();
      ItemRef bundle = ReactBundles.superStack(items);

      ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> !filtering || !type.equals(refused));

      int left = 0;
      for (ItemRef leftover : unpacked.leftovers()) {
        left += leftover.amount();
      }
      assertThat(unpacked.carried()).as("seed %d", seed).isEqualTo(carried.get());
      assertThat(unpacked.carried() + left).as("seed %d", seed).isEqualTo(total);
      assertThat(unpacked.carried()).as("seed %d", seed).isLessThanOrEqualTo(capacity.get());
      assertThat(carried.get()).as("seed %d", seed).isLessThanOrEqualTo(capacity.get());
      assertThat(unpacked.leftovers()).as("seed %d", seed).allMatch(leftover -> leftover.amount() > 0);
      if (filtering) {
        assertThat(carriedStacks).as("seed %d", seed).noneMatch(stack -> stack.type().equals(refused));
      }
      List<String> contents = ReactBundles.summary(ReactBundles.contents(bundle));
      assertThat(contents).as("seed %d", seed).isEqualTo(Arrays.stream(items).map(item -> item.type().legacyName() + "x" + item.amount()).toList());
    }
  }

  @Test
  void aRebuiltBundleKeepsTheSingleLoreLineAndTheLeftovers() {
    ItemRef bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 4));
    List<ItemRef> leftovers = List.of(GolemTestItems.stack(Id.minecraft("carrot"), 3));

    Optional<ItemRef> rebuilt = ReactSuperStacks.rebuild(bundle, leftovers);

    assertThat(rebuilt).isPresent();
    assertThat(ReactSuperStacks.isSuperStack(rebuilt.get())).isTrue();
    assertThat(ReactBundles.lore(rebuilt.get())).containsExactly(ReactBundles.MARKER);
    assertThat(ReactBundles.summary(ReactBundles.contents(rebuilt.get()))).containsExactly("CARROTx3");
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle))).containsExactly("WHEATx4", "CARROTx4");
  }

  @Test
  void aBundleThatCannotHoldTheLeftoversIsNotRebuilt() {
    ItemRef bundle = ReactBundles.rejecting(wheat(4), GolemTestItems.stack(Id.minecraft("carrot"), 4));

    assertThat(ReactSuperStacks.rebuild(bundle, List.of(GolemTestItems.stack(Id.minecraft("carrot"), 3)))).isEmpty();
    assertThat(ReactSuperStacks.rebuild(ReactBundles.superStack(wheat(4)), List.of())).isEmpty();
  }

  private GolemContext golem() {
    GolemContext golem = mock(GolemContext.class, withSettings().strictness(Strictness.LENIENT));
    when(golem.room()).thenAnswer(invocation -> Math.max(0, capacity.get() - carried.get()));
    when(golem.carry(any(ItemRef.class))).thenAnswer(invocation -> {
      ItemRef stack = invocation.getArgument(0);
      int taking = Math.min(stack.amount(), Math.max(0, capacity.get() - carried.get()));
      if (taking > 0) {
        ItemRef taken = stack.copyWithAmount(taking);
        carriedStacks.add(taken);
      }
      carried.addAndGet(taking);
      if (taking == stack.amount()) {
        return Optional.empty();
      }
      ItemRef rest = stack.copyWithAmount(stack.amount() - taking);
      return Optional.of(rest);
    });
    return golem;
  }

  private static ItemRef wheat(int amount) {
    return GolemTestItems.stack(Id.minecraft("wheat"), amount);
  }
}
