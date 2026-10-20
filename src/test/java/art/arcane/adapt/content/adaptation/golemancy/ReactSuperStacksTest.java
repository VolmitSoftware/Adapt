package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTestItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ReactSuperStacksTest {
  private final AtomicBoolean reactEnabled = new AtomicBoolean(true);
  private final AtomicInteger capacity = new AtomicInteger(64);
  private final AtomicInteger carried = new AtomicInteger();
  private final List<ItemStack> carriedStacks = new ArrayList<>();
  private MockedStatic<Bukkit> bukkit;

  @BeforeEach
  void setUp() {
    PluginManager plugins = mock(PluginManager.class);
    when(plugins.isPluginEnabled("React")).thenAnswer(invocation -> reactEnabled.get());
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
  }

  @AfterEach
  void close() {
    bukkit.close();
  }

  @Test
  void aBundleWithExactlyTheMarkerLoreIsASuperStackWhileReactIsEnabled() {
    ItemStack bundle = ReactBundles.superStack(wheat(4));

    assertThat(ReactSuperStacks.isSuperStack(bundle)).isTrue();
  }

  @Test
  void withoutReactEnabledNoBundleIsASuperStack() {
    ItemStack bundle = ReactBundles.superStack(wheat(4));
    reactEnabled.set(false);

    assertThat(ReactSuperStacks.isSuperStack(bundle)).isFalse();
    assertThat(ReactSuperStacks.contents(bundle)).isEmpty();
    assertThat(ReactSuperStacks.hollow(bundle)).isFalse();
    assertThat(ReactSuperStacks.accepts(bundle, type -> type == Material.WHEAT)).isFalse();
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
    ItemStack bundle = ReactBundles.superStack(wheat(4));
    BundleMeta meta = (BundleMeta) bundle.getItemMeta();
    doReturn(null).when(meta).getLore();

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
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 0), GolemTestItems.stack(Material.DIAMOND, 2));

    List<ItemStack> contents = ReactSuperStacks.contents(bundle);

    assertThat(ReactBundles.summary(contents)).containsExactly("WHEATx4", "DIAMONDx2");
    contents.getFirst().setAmount(1);
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle))).containsExactly("WHEATx4", "CARROTx0", "DIAMONDx2");
  }

  @Test
  void aSuperStackWithNothingToUnpackIsHollow() {
    assertThat(ReactSuperStacks.hollow(ReactBundles.superStack())).isTrue();
    assertThat(ReactSuperStacks.hollow(ReactBundles.superStack(GolemTestItems.stack(Material.WHEAT, 0)))).isTrue();
    assertThat(ReactSuperStacks.hollow(ReactBundles.bundle(List.of(ReactBundles.MARKER), 2, wheat(4)))).isTrue();
    assertThat(ReactSuperStacks.contents(ReactBundles.bundle(List.of(ReactBundles.MARKER), 2, wheat(4)))).isEmpty();
    assertThat(ReactSuperStacks.hollow(ReactBundles.superStack(wheat(4)))).isFalse();
  }

  @Test
  void aSuperStackIsAcceptedWhenAnyContentIsAndAPlainStackByItsOwnType() {
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.DIAMOND, 2));

    assertThat(ReactSuperStacks.accepts(bundle, type -> type == Material.DIAMOND)).isTrue();
    assertThat(ReactSuperStacks.accepts(bundle, type -> type == Material.BUNDLE)).isFalse();
    assertThat(ReactSuperStacks.accepts(bundle, type -> type == Material.COAL)).isFalse();
    assertThat(ReactSuperStacks.accepts(wheat(4), type -> type == Material.WHEAT)).isTrue();
    assertThat(ReactSuperStacks.accepts(wheat(4), type -> type == Material.CARROT)).isFalse();
    ItemStack real = ReactBundles.bundle(List.of("mine"), 1, wheat(4));
    assertThat(ReactSuperStacks.accepts(real, type -> type == Material.BUNDLE)).isTrue();
    assertThat(ReactSuperStacks.accepts(real, type -> type == Material.WHEAT)).isFalse();
  }

  @Test
  void aBundleWithRoomForEverythingIsUnpackedInOrderAndLeavesNothing() {
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 3), GolemTestItems.stack(Material.DIAMOND, 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> true);

    assertThat(unpacked.carried()).isEqualTo(9);
    assertThat(unpacked.leftovers()).isEmpty();
    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx3", "DIAMONDx2");
    assertThat(carriedStacks).noneMatch(stack -> stack.getType() == Material.BUNDLE);
  }

  @Test
  void aBundleBiggerThanTheRoomKeepsTheRestInOrder() {
    capacity.set(5);
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 4), GolemTestItems.stack(Material.DIAMOND, 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> true);

    assertThat(unpacked.carried()).isEqualTo(5);
    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx1");
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("CARROTx3", "DIAMONDx2");
  }

  @Test
  void aFullGolemUnpacksNothing() {
    carried.set(64);
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 4));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> true);

    assertThat(unpacked.carried()).isZero();
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("WHEATx4", "CARROTx4");
    assertThat(carriedStacks).isEmpty();
  }

  @Test
  void contentsTheAreaRejectsStayInTheBundleInTheirPlace() {
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 3), GolemTestItems.stack(Material.DIAMOND, 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> type != Material.CARROT);

    assertThat(unpacked.carried()).isEqualTo(6);
    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "DIAMONDx2");
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("CARROTx3");
  }

  @Test
  void whatTheCargoRefusesStaysInTheBundle() {
    GolemContext golem = golem();
    doAnswer(invocation -> {
      ItemStack stack = invocation.getArgument(0);
      if (stack.getType() == Material.CARROT) {
        return Optional.of(stack);
      }
      carried.addAndGet(stack.getAmount());
      carriedStacks.add(stack);
      return Optional.empty();
    }).when(golem).carry(any(ItemStack.class));
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 3), GolemTestItems.stack(Material.DIAMOND, 2));

    ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem, bundle, type -> true);

    assertThat(unpacked.carried()).isEqualTo(6);
    assertThat(ReactBundles.summary(unpacked.leftovers())).containsExactly("CARROTx3");
  }

  @Test
  void unpackingNeverDuplicatesOrLosesAnItem() {
    Material[] types = {Material.WHEAT, Material.CARROT, Material.DIAMOND, Material.COAL, Material.STICK};
    for (int seed = 0; seed < 150; seed++) {
      Random random = new Random(seed);
      carried.set(0);
      carriedStacks.clear();
      capacity.set(random.nextInt(80));
      int entries = 1 + random.nextInt(6);
      ItemStack[] items = new ItemStack[entries];
      int total = 0;
      for (int i = 0; i < entries; i++) {
        int amount = 1 + random.nextInt(64);
        total += amount;
        items[i] = GolemTestItems.stack(types[random.nextInt(types.length)], amount);
      }
      Material refused = types[random.nextInt(types.length)];
      boolean filtering = random.nextBoolean();
      ItemStack bundle = ReactBundles.superStack(items);

      ReactSuperStacks.Unpacked unpacked = ReactSuperStacks.unpack(golem(), bundle, type -> !filtering || type != refused);

      int left = 0;
      for (ItemStack leftover : unpacked.leftovers()) {
        left += leftover.getAmount();
      }
      assertThat(unpacked.carried()).as("seed %d", seed).isEqualTo(carried.get());
      assertThat(unpacked.carried() + left).as("seed %d", seed).isEqualTo(total);
      assertThat(unpacked.carried()).as("seed %d", seed).isLessThanOrEqualTo(capacity.get());
      assertThat(carried.get()).as("seed %d", seed).isLessThanOrEqualTo(capacity.get());
      assertThat(unpacked.leftovers()).as("seed %d", seed).allMatch(leftover -> leftover.getAmount() > 0);
      if (filtering) {
        assertThat(carriedStacks).as("seed %d", seed).noneMatch(stack -> stack.getType() == refused);
      }
      List<String> contents = ReactBundles.summary(ReactBundles.contents(bundle));
      assertThat(contents).as("seed %d", seed).isEqualTo(Arrays.stream(items).map(item -> item.getType() + "x" + item.getAmount()).toList());
    }
  }

  @Test
  void aRebuiltBundleKeepsTheSingleLoreLineAndTheLeftovers() {
    ItemStack bundle = ReactBundles.superStack(wheat(4), GolemTestItems.stack(Material.CARROT, 4));
    List<ItemStack> leftovers = List.of(GolemTestItems.stack(Material.CARROT, 3));

    Optional<ItemStack> rebuilt = ReactSuperStacks.rebuild(bundle, leftovers);

    assertThat(rebuilt).isPresent();
    assertThat(ReactSuperStacks.isSuperStack(rebuilt.get())).isTrue();
    assertThat(ReactBundles.lore(rebuilt.get())).containsExactly(ReactBundles.MARKER);
    assertThat(ReactBundles.summary(ReactBundles.contents(rebuilt.get()))).containsExactly("CARROTx3");
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle))).containsExactly("WHEATx4", "CARROTx4");
  }

  @Test
  void aBundleThatCannotHoldTheLeftoversIsNotRebuilt() {
    ItemStack bundle = ReactBundles.rejecting(wheat(4), GolemTestItems.stack(Material.CARROT, 4));

    assertThat(ReactSuperStacks.rebuild(bundle, List.of(GolemTestItems.stack(Material.CARROT, 3)))).isEmpty();
    assertThat(ReactSuperStacks.rebuild(ReactBundles.superStack(wheat(4)), List.of())).isEmpty();
  }

  private GolemContext golem() {
    GolemContext golem = mock(GolemContext.class, withSettings().strictness(Strictness.LENIENT));
    when(golem.room()).thenAnswer(invocation -> Math.max(0, capacity.get() - carried.get()));
    when(golem.carry(any(ItemStack.class))).thenAnswer(invocation -> {
      ItemStack stack = invocation.getArgument(0);
      int taking = Math.min(stack.getAmount(), Math.max(0, capacity.get() - carried.get()));
      if (taking > 0) {
        ItemStack taken = stack.clone();
        taken.setAmount(taking);
        carriedStacks.add(taken);
      }
      carried.addAndGet(taking);
      if (taking == stack.getAmount()) {
        return Optional.empty();
      }
      ItemStack rest = stack.clone();
      rest.setAmount(stack.getAmount() - taking);
      return Optional.of(rest);
    });
    return golem;
  }

  private static ItemStack wheat(int amount) {
    return GolemTestItems.stack(Material.WHEAT, amount);
  }
}
