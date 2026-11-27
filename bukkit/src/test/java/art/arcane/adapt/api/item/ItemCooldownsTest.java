package art.arcane.adapt.api.item;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemCooldownsTest extends AdaptTestBase {
  private static final long MINUTE = 60_000L;

  @BeforeEach
  void installBukkitStamping() throws ReflectiveOperationException {
    Class<? extends ItemCooldownGroupPort> type = Class
        .forName("art.arcane.adapt.bukkit.port.BukkitItemCooldownGroupPort")
        .asSubclass(ItemCooldownGroupPort.class);
    Constructor<? extends ItemCooldownGroupPort> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    AdaptPorts.install(ItemCooldownGroupPort.class, constructor.newInstance());
  }

  @AfterEach
  void resetRegistry() {
    AdaptPorts.uninstall(ItemCooldownGroupPort.class);
    PlayerStateRegistry.reset();
  }

  private PlayerRef playerWithId(UUID id) {
    PlayerRef p = mock(PlayerRef.class);
    lenient().when(p.uuid()).thenReturn(id);
    return p;
  }

  @Test
  void millisRoundUpToWholeTicksSoTheSweepNeverClearsBeforeTheGateOpens() {
    assertThat(ItemCooldowns.ticksFromMillis(0L)).isZero();
    assertThat(ItemCooldowns.ticksFromMillis(-500L)).isZero();
    assertThat(ItemCooldowns.ticksFromMillis(1L)).isEqualTo(1);
    assertThat(ItemCooldowns.ticksFromMillis(49L)).isEqualTo(1);
    assertThat(ItemCooldowns.ticksFromMillis(50L)).isEqualTo(1);
    assertThat(ItemCooldowns.ticksFromMillis(51L)).isEqualTo(2);
    assertThat(ItemCooldowns.ticksFromMillis(125L)).isEqualTo(3);
    assertThat(ItemCooldowns.ticksFromMillis(7500L)).isEqualTo(150);
  }

  @Test
  void aHugeCooldownClampsInsteadOfOverflowingTheTickField() {
    assertThat(ItemCooldowns.ticksFromMillis(Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void tickToMillisInvertsTheWholeTickCases() {
    assertThat(ItemCooldowns.millisFromTicks(0)).isZero();
    assertThat(ItemCooldowns.millisFromTicks(-3)).isZero();
    assertThat(ItemCooldowns.millisFromTicks(1)).isEqualTo(50L);
    assertThat(ItemCooldowns.millisFromTicks(150)).isEqualTo(7500L);
  }

  @Test
  void anItemsCooldownGroupIsItsOwnId() {
    assertThat(ItemCooldowns.forItem(BoundEyeOfEnder.ITEM).getGroup()).isEqualTo(Id.adapt("bound_eye_of_ender"));
    assertThat(BoundRedstoneTorch.ITEM.cooldownGroup()).contains(BoundRedstoneTorch.ITEM.id());
    assertThat(ChronoTimeBombItem.ITEM.cooldownGroup()).contains(ChronoTimeBombItem.ITEM.id());
  }

  @Test
  void twoAddonsItemsOfTheSameNameNeverShareACooldownGroup() {
    CustomItem.Definition<CustomItem.Plain> first = wand(Id.of("pyromancy", "fire_wand"));
    CustomItem.Definition<CustomItem.Plain> second = wand(Id.of("embers", "fire_wand"));

    assertThat(ItemCooldowns.forItem(first).getGroup()).isEqualTo(Id.of("pyromancy", "fire_wand"));
    assertThat(ItemCooldowns.forItem(second).getGroup()).isEqualTo(Id.of("embers", "fire_wand"));
  }

  @Test
  void anItemWithoutItsOwnGroupIsRefusedNamingIt() {
    CustomItem.Definition<CustomItem.Plain> plain = CustomItem.builder(Id.of("pyromancy", "ember"),
        Id.minecraft("stick")).build();

    assertThat(plain.cooldownGroup()).isEmpty();
    assertThatThrownBy(() -> ItemCooldowns.forItem(plain))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pyromancy:ember");
  }

  private static CustomItem.Definition<CustomItem.Plain> wand(Id id) {
    return CustomItem.builder(id, Id.minecraft("blaze_rod")).ownCooldownGroup().build();
  }

  @Test
  void aGroupGateReportsItsGroupAndAMaterialGateReportsItsMaterial() {
    ItemCooldowns group = ItemCooldowns.forGroup(BoundEyeOfEnder.ITEM.id());
    ItemCooldowns material = ItemCooldowns.forMaterial(Id.minecraft("amethyst_shard"));
    ItemCooldowns hidden = ItemCooldowns.hidden();

    assertThat(group.getGroup()).isEqualTo(BoundEyeOfEnder.ITEM.id());
    assertThat(group.getMaterial()).isNull();
    assertThat(group.isVisible()).isTrue();

    assertThat(material.getMaterial()).isEqualTo(Id.minecraft("amethyst_shard"));
    assertThat(material.getGroup()).isNull();
    assertThat(material.isVisible()).isTrue();

    assertThat(hidden.getGroup()).isNull();
    assertThat(hidden.getMaterial()).isNull();
    assertThat(hidden.isVisible()).isFalse();
  }

  @Test
  void aNullSurfaceIsRejectedAtConstructionInsteadOfSilentlyGoingInvisible() {
    assertThatThrownBy(() -> ItemCooldowns.forGroup(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ItemCooldowns.forMaterial(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFreshGateIsOpenAndClosesAsSoonAsItIsMarked() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    PlayerRef p = playerWithId(UUID.randomUUID());

    assertThat(cooldown.isReady(p, MINUTE)).isTrue();

    cooldown.mark(p, MINUTE);

    assertThat(cooldown.isReady(p, MINUTE)).isFalse();
    assertThat(cooldown.remaining(p, MINUTE)).isPositive().isLessThanOrEqualTo(MINUTE);
  }

  @Test
  void tryUseMarksOnTheFirstCallAndRefusesEverySubsequentCall() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    PlayerRef p = playerWithId(UUID.randomUUID());

    assertThat(cooldown.tryUse(p, MINUTE)).isTrue();
    assertThat(cooldown.tryUse(p, MINUTE)).isFalse();
    assertThat(cooldown.tryUse(p, MINUTE)).isFalse();
  }

  @Test
  void oneMarkedPlayerNeverClosesTheGateForAnother() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    PlayerRef marked = playerWithId(UUID.randomUUID());
    PlayerRef other = playerWithId(UUID.randomUUID());

    cooldown.mark(marked, MINUTE);

    assertThat(cooldown.isReady(marked, MINUTE)).isFalse();
    assertThat(cooldown.isReady(other, MINUTE)).isTrue();
  }

  @Test
  void clearingReopensTheGateAndZeroesTheRemainder() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    PlayerRef p = playerWithId(UUID.randomUUID());

    cooldown.mark(p, MINUTE);
    cooldown.clear(p);

    assertThat(cooldown.isReady(p, MINUTE)).isTrue();
    assertThat(cooldown.remaining(p, MINUTE)).isZero();
  }

  @Test
  void aZeroLengthCooldownLeavesTheGateOpen() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    PlayerRef p = playerWithId(UUID.randomUUID());

    cooldown.mark(p, 0L);

    assertThat(cooldown.isReady(p, 0L)).isTrue();
    assertThat(cooldown.remaining(p, 0L)).isZero();
  }

  @Test
  void aNullPlayerNeitherOpensTheGateNorThrows() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();

    assertThat(cooldown.isReady((PlayerRef) null, MINUTE)).isFalse();
    assertThat(cooldown.remaining((PlayerRef) null, MINUTE)).isZero();
    assertThat(cooldown.tryUse(null, MINUTE)).isFalse();
    cooldown.mark(null, MINUTE);
    cooldown.clear((PlayerRef) null);
  }

  @Test
  void theUuidGateAndThePlayerGateShareOneState() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    UUID id = UUID.randomUUID();
    PlayerRef p = playerWithId(id);

    cooldown.mark(p, MINUTE);

    assertThat(cooldown.isReady(id, MINUTE)).isFalse();
    long byId = cooldown.remaining(id, MINUTE);
    long byPlayer = cooldown.remaining(p, MINUTE);
    assertThat(byPlayer).isPositive();
    // Same underlying state read twice off a real clock; only time may elapse between the reads.
    assertThat(byId).isGreaterThanOrEqualTo(byPlayer).isCloseTo(byPlayer, within(5000L));

    cooldown.clear(id);

    assertThat(cooldown.isReady(p, MINUTE)).isTrue();
  }
}
