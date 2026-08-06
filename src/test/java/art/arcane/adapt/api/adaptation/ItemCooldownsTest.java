package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemCooldownsTest extends AdaptTestBase {
  private static final long MINUTE = 60_000L;

  @AfterEach
  void resetRegistry() {
    PlayerStateRegistry.reset();
  }

  private Player playerWithId(UUID id) {
    Player p = mock(Player.class);
    lenient().when(p.getUniqueId()).thenReturn(id);
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
  void groupIdsAreDerivedFromTheItemClassAsLowercaseSnakeCase() {
    assertThat(ItemCooldowns.groupIdFor(BoundEyeOfEnder.class)).isEqualTo("item_bound_eye_of_ender");
    assertThat(ItemCooldowns.groupIdFor(BoundRedstoneTorch.class)).isEqualTo("item_bound_redstone_torch");
    assertThat(ItemCooldowns.groupIdFor(ChronoTimeBombItem.class)).isEqualTo("item_chrono_time_bomb_item");
  }

  @Test
  void groupKeysLandInTheAdaptNamespaceAndStayValid() {
    NamespacedKey key = ItemCooldowns.groupKeyFor(BoundEyeOfEnder.class);

    assertThat(key.getNamespace()).isEqualTo(ItemCooldowns.NAMESPACE);
    assertThat(key.getKey()).isEqualTo("item_bound_eye_of_ender");
    assertThat(key.toString()).isEqualTo("adapt:item_bound_eye_of_ender");
  }

  @Test
  void everyDeclaredItemGroupIsDistinctSoOneItemNeverGraysOutAnother() {
    assertThat(BoundEyeOfEnder.COOLDOWN_GROUP)
        .isNotEqualTo(BoundRedstoneTorch.COOLDOWN_GROUP)
        .isNotEqualTo(ChronoTimeBombItem.COOLDOWN_GROUP);
    assertThat(BoundRedstoneTorch.COOLDOWN_GROUP).isNotEqualTo(ChronoTimeBombItem.COOLDOWN_GROUP);
  }

  @Test
  void snakeCaseCollapsesSeparatorsAndNeverLeavesATrailingUnderscore() {
    assertThat(ItemCooldowns.toSnakeCase("BoundEyeOfEnder")).isEqualTo("bound_eye_of_ender");
    assertThat(ItemCooldowns.toSnakeCase("rift-ender-taglock")).isEqualTo("rift_ender_taglock");
    assertThat(ItemCooldowns.toSnakeCase("already_snake")).isEqualTo("already_snake");
    assertThat(ItemCooldowns.toSnakeCase("Trailing   ")).isEqualTo("trailing");
    assertThat(ItemCooldowns.toSnakeCase("HTTPServer")).isEqualTo("h_t_t_p_server");
  }

  @Test
  void anEmptyGroupIdIsRejectedRatherThanProducingAnInvalidKey() {
    assertThatThrownBy(() -> ItemCooldowns.groupKey(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ItemCooldowns.groupKey(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ItemCooldowns.groupIdFor(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aGroupGateReportsItsGroupAndAMaterialGateReportsItsMaterial() {
    ItemCooldowns group = ItemCooldowns.forGroup(BoundEyeOfEnder.COOLDOWN_GROUP);
    ItemCooldowns material = ItemCooldowns.forMaterial(Material.AMETHYST_SHARD);
    ItemCooldowns hidden = ItemCooldowns.hidden();

    assertThat(group.getGroup()).isEqualTo(BoundEyeOfEnder.COOLDOWN_GROUP);
    assertThat(group.getMaterial()).isNull();
    assertThat(group.isVisible()).isTrue();

    assertThat(material.getMaterial()).isEqualTo(Material.AMETHYST_SHARD);
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
    Player p = playerWithId(UUID.randomUUID());

    assertThat(cooldown.isReady(p, MINUTE)).isTrue();

    cooldown.mark(p, MINUTE);

    assertThat(cooldown.isReady(p, MINUTE)).isFalse();
    assertThat(cooldown.remaining(p, MINUTE)).isPositive().isLessThanOrEqualTo(MINUTE);
  }

  @Test
  void tryUseMarksOnTheFirstCallAndRefusesEverySubsequentCall() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    Player p = playerWithId(UUID.randomUUID());

    assertThat(cooldown.tryUse(p, MINUTE)).isTrue();
    assertThat(cooldown.tryUse(p, MINUTE)).isFalse();
    assertThat(cooldown.tryUse(p, MINUTE)).isFalse();
  }

  @Test
  void oneMarkedPlayerNeverClosesTheGateForAnother() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    Player marked = playerWithId(UUID.randomUUID());
    Player other = playerWithId(UUID.randomUUID());

    cooldown.mark(marked, MINUTE);

    assertThat(cooldown.isReady(marked, MINUTE)).isFalse();
    assertThat(cooldown.isReady(other, MINUTE)).isTrue();
  }

  @Test
  void clearingReopensTheGateAndZeroesTheRemainder() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    Player p = playerWithId(UUID.randomUUID());

    cooldown.mark(p, MINUTE);
    cooldown.clear(p);

    assertThat(cooldown.isReady(p, MINUTE)).isTrue();
    assertThat(cooldown.remaining(p, MINUTE)).isZero();
  }

  @Test
  void aZeroLengthCooldownLeavesTheGateOpen() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    Player p = playerWithId(UUID.randomUUID());

    cooldown.mark(p, 0L);

    assertThat(cooldown.isReady(p, 0L)).isTrue();
    assertThat(cooldown.remaining(p, 0L)).isZero();
  }

  @Test
  void aNullPlayerNeitherOpensTheGateNorThrows() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();

    assertThat(cooldown.isReady((Player) null, MINUTE)).isFalse();
    assertThat(cooldown.remaining((Player) null, MINUTE)).isZero();
    assertThat(cooldown.tryUse(null, MINUTE)).isFalse();
    cooldown.mark(null, MINUTE);
    cooldown.clear((Player) null);
  }

  @Test
  void theUuidGateAndThePlayerGateShareOneState() {
    ItemCooldowns cooldown = ItemCooldowns.hidden();
    UUID id = UUID.randomUUID();
    Player p = playerWithId(id);

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

  @Test
  void stampingWritesTheGroupOntoTheUseCooldownComponent() {
    ItemMeta meta = mock(ItemMeta.class);
    UseCooldownComponent component = mock(UseCooldownComponent.class);
    when(meta.hasUseCooldown()).thenReturn(false);
    when(meta.getUseCooldown()).thenReturn(component);

    assertThat(ItemCooldowns.stampGroup(meta, BoundEyeOfEnder.COOLDOWN_GROUP)).isTrue();

    verify(component).setCooldownGroup(BoundEyeOfEnder.COOLDOWN_GROUP);
    verify(component).setCooldownSeconds(org.mockito.ArgumentMatchers.floatThat(seconds -> seconds > 0F));
    verify(meta).setUseCooldown(component);
  }

  @Test
  void stampingIsIdempotentSoRepeatedUseDoesNotRewriteTheStack() {
    ItemMeta meta = mock(ItemMeta.class);
    UseCooldownComponent component = mock(UseCooldownComponent.class);
    when(meta.hasUseCooldown()).thenReturn(true);
    when(meta.getUseCooldown()).thenReturn(component);
    when(component.getCooldownGroup()).thenReturn(BoundEyeOfEnder.COOLDOWN_GROUP);

    assertThat(ItemCooldowns.stampGroup(meta, BoundEyeOfEnder.COOLDOWN_GROUP)).isFalse();

    verify(meta, never()).setUseCooldown(any());
  }

  @Test
  void stampingReplacesAForeignGroupRatherThanLeavingTheWrongSweep() {
    ItemMeta meta = mock(ItemMeta.class);
    UseCooldownComponent component = mock(UseCooldownComponent.class);
    when(meta.hasUseCooldown()).thenReturn(true);
    when(meta.getUseCooldown()).thenReturn(component);
    when(component.getCooldownGroup()).thenReturn(BoundRedstoneTorch.COOLDOWN_GROUP);

    assertThat(ItemCooldowns.stampGroup(meta, BoundEyeOfEnder.COOLDOWN_GROUP)).isTrue();

    verify(component).setCooldownGroup(BoundEyeOfEnder.COOLDOWN_GROUP);
    verify(meta).setUseCooldown(component);
  }

  @Test
  void stampingNullsIsANoOpSoCallersCanPassAnUndeclaredGroup() {
    ItemMeta meta = mock(ItemMeta.class);

    assertThat(ItemCooldowns.stampGroup(meta, null)).isFalse();
    assertThat(ItemCooldowns.stampGroup((ItemMeta) null, BoundEyeOfEnder.COOLDOWN_GROUP)).isFalse();

    verify(meta, never()).setUseCooldown(any());
  }
}
