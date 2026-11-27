package art.arcane.adapt.content.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GolemHandleEquipmentTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

  private FakeGolemPlatform platform;
  private MobRef skeleton;
  private GolemHandle bone;
  private Map<EquipmentSlot, ItemRef> visible;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    platform.join(OWNER, new GolemRoster());
    skeleton = entity(GolemBody.BONE);
    visible = new EnumMap<>(EquipmentSlot.class);
    lenient().doAnswer(invocation -> {
      visible.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(skeleton).setEquipment(any(EquipmentSlot.class), any());
    lenient().when(skeleton.equipment(any(EquipmentSlot.class))).thenAnswer(invocation -> {
      ItemRef item = visible.get(invocation.<EquipmentSlot>getArgument(0));
      return item == null ? FakeItem.empty() : item;
    });
    bone = new GolemHandle(skeleton, state(GolemBody.BONE), platform);
  }

  @Test
  void equippingStoresTheItemWritesItAtOnceAndPutsItOnTheSkeletonWithoutADropChance() {
    ItemRef helmet = GolemTestItems.stack(Id.minecraft("iron_helmet"), 1);

    assertThat(bone.equip(GolemEquipmentSlot.HELMET, helmet)).isTrue();

    assertThat(bone.state().equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
    ArgumentCaptor<ItemRef> wornItem = ArgumentCaptor.forClass(ItemRef.class);
    verify(skeleton).setEquipment(eq(EquipmentSlot.HEAD), wornItem.capture());
    assertThat(wornItem.getValue().type()).isEqualTo(Id.minecraft("iron_helmet"));
    verify(skeleton).setEquipmentDropChance(EquipmentSlot.HEAD, 0F);
  }

  @Test
  void everySlotEquipsItsOwnPieceAndBothHands() {
    Id[] pieces = {Id.minecraft("iron_helmet"), Id.minecraft("iron_chestplate"), Id.minecraft("iron_leggings"), Id.minecraft("iron_boots"), Id.minecraft("bow"), Id.minecraft("shield")};

    for (int index = 0; index < pieces.length; index++) {
      assertThat(bone.equip(GolemEquipmentSlot.values()[index], GolemTestItems.stack(pieces[index], 1))).isTrue();
    }

    assertThat(bone.state().equipment()).hasSize(6);
    assertThat(bone.equipmentItems()).extracting(ItemRef::type).containsExactlyInAnyOrder(pieces);
    verify(skeleton).setEquipmentDropChance(EquipmentSlot.OFF_HAND, 0F);
    verify(skeleton).setEquipmentDropChance(EquipmentSlot.MAIN_HAND, 0F);
  }

  @Test
  void anItemThatDoesNotFitTheSlotIsRefusedAndNothingChanges() {
    assertThat(bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_boots"), 1))).isFalse();
    assertThat(bone.equip(GolemEquipmentSlot.CHESTPLATE, GolemTestItems.stack(Id.minecraft("stone"), 1))).isFalse();
    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Id.minecraft("air"), 1))).isFalse();

    assertThat(bone.state().equipment()).isEmpty();
    assertThat(platform.encoded).isEmpty();
    verify(skeleton, never()).setEquipment(any(EquipmentSlot.class), any());
  }

  @Test
  void aStackOfSeveralItemsIsRefusedSoNoneAreLost() {
    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Id.minecraft("arrow"), 16))).isFalse();

    assertThat(bone.state().equipment()).isEmpty();
  }

  @Test
  void anOccupiedSlotRefusesASecondItemSoTheFirstIsNeverOverwritten() {
    ItemRef first = GolemTestItems.stack(Id.minecraft("bow"), 1);
    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, first)).isTrue();
    String stored = bone.state().equipment().get(GolemEquipmentSlot.MAIN_HAND);

    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Id.minecraft("iron_sword"), 1))).isFalse();

    assertThat(bone.state().equipment()).containsEntry(GolemEquipmentSlot.MAIN_HAND, stored);
    assertThat(platform.encoded).hasSize(1);
  }

  @Test
  void onlyBoneGolemsWearEquipment() {
    MobRef iron = entity(GolemBody.IRON);
    GolemHandle plated = new GolemHandle(iron, state(GolemBody.IRON), platform);

    assertThat(plated.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_helmet"), 1))).isFalse();

    assertThat(plated.state().equipment()).isEmpty();
    verify(iron, never()).setEquipment(any(EquipmentSlot.class), any());
  }

  @Test
  void takingAnItemBackReturnsTheStoredItemAndClearsTheSlotAndTheSkeleton() {
    ItemRef bow = GolemTestItems.stack(Id.minecraft("bow"), 1);
    bone.equip(GolemEquipmentSlot.MAIN_HAND, bow);
    String stored = bone.state().equipment().get(GolemEquipmentSlot.MAIN_HAND);

    Optional<ItemRef> taken = bone.unequip(GolemEquipmentSlot.MAIN_HAND, stored);

    assertThat(taken).hasValueSatisfying(item -> assertThat(item.type()).isEqualTo(Id.minecraft("bow")));
    assertThat(bone.state().equipment()).isEmpty();
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).isEmpty();
    verify(skeleton).setEquipment(EquipmentSlot.MAIN_HAND, null);
  }

  @Test
  void aSecondTakeOfTheSameItemYieldsNothingSoRapidClicksCannotDuplicateIt() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("carved_pumpkin"), 1));
    String stored = bone.state().equipment().get(GolemEquipmentSlot.HELMET);

    Optional<ItemRef> first = bone.unequip(GolemEquipmentSlot.HELMET, stored);
    Optional<ItemRef> second = bone.unequip(GolemEquipmentSlot.HELMET, stored);

    assertThat(first).isPresent();
    assertThat(second).isEmpty();
  }

  @Test
  void aStaleTakeNeverRemovesAnItemThatReplacedTheOneItWasMeantFor() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_helmet"), 1));
    String stale = bone.state().equipment().get(GolemEquipmentSlot.HELMET);
    bone.unequip(GolemEquipmentSlot.HELMET, stale);
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("diamond_helmet"), 1));
    String current = bone.state().equipment().get(GolemEquipmentSlot.HELMET);

    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, stale)).isEmpty();

    assertThat(bone.state().equipment()).containsEntry(GolemEquipmentSlot.HELMET, current);
  }

  @Test
  void anEmptySlotOrAnUnreadableEntryGivesNothingAndKeepsTheEntry() {
    assertThat(bone.unequip(GolemEquipmentSlot.BOOTS, "item-0")).isEmpty();
    bone.state().setEquipment(GolemEquipmentSlot.BOOTS, "corrupt");

    assertThat(bone.unequip(GolemEquipmentSlot.BOOTS, "corrupt")).isEmpty();

    assertThat(bone.state().equipment()).containsEntry(GolemEquipmentSlot.BOOTS, "corrupt");
    assertThat(bone.equipmentItems()).isEmpty();
  }

  @Test
  void aRetiredOrRemovedGolemTakesAndGivesNothing() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_helmet"), 1));
    String stored = bone.state().equipment().get(GolemEquipmentSlot.HELMET);
    lenient().when(skeleton.isValid()).thenReturn(false);

    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, stored)).isEmpty();
    assertThat(bone.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Id.minecraft("iron_boots"), 1))).isFalse();

    lenient().when(skeleton.isValid()).thenReturn(true);
    bone.retire();

    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, stored)).isEmpty();
    assertThat(bone.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Id.minecraft("iron_boots"), 1))).isFalse();
    assertThat(bone.state().equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
  }

  @Test
  void equipmentItemsListsEveryWornItemInSlotOrder() {
    bone.equip(GolemEquipmentSlot.OFF_HAND, GolemTestItems.stack(Id.minecraft("shield"), 1));
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_helmet"), 1));

    List<ItemRef> items = bone.equipmentItems();

    assertThat(items).extracting(ItemRef::type).containsExactly(Id.minecraft("iron_helmet"), Id.minecraft("shield"));
  }

  @Test
  void nativeDurabilityDamageIsSavedBeforeTheEquippedItemCanBeTakenBack() {
    bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.tool(Id.minecraft("iron_sword"), 20, 250));
    doAnswer(invocation -> {
      visible.put(EquipmentSlot.MAIN_HAND, GolemTestItems.tool(Id.minecraft("iron_sword"), 21, 250));
      return null;
    }).when(skeleton).damageEquipment(EquipmentSlot.MAIN_HAND, 1);

    bone.damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);

    String encoded = bone.state().equipment().get(GolemEquipmentSlot.MAIN_HAND);
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).containsEntry(GolemEquipmentSlot.MAIN_HAND, encoded);
    assertThat(bone.unequip(GolemEquipmentSlot.MAIN_HAND, encoded)).hasValueSatisfying(item -> assertThat(GolemTestItems.damage(item)).isEqualTo(21));
  }

  @Test
  void aBrokenItemIsRemovedFromStoredEquipmentAndCannotBeReturned() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.tool(Id.minecraft("iron_helmet"), 164, 165));
    String encoded = bone.state().equipment().get(GolemEquipmentSlot.HELMET);
    doAnswer(invocation -> {
      visible.remove(EquipmentSlot.HEAD);
      return null;
    }).when(skeleton).damageEquipment(EquipmentSlot.HEAD, 1);

    bone.damageEquipment(GolemEquipmentSlot.HELMET, 1);

    assertThat(bone.state().equipment()).isEmpty();
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).isEmpty();
    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, encoded)).isEmpty();
  }

  @Test
  void aBoneWorkerUsesItsEquippedRodWithoutReplacingItWithAVirtualTool() {
    bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.tool(Id.minecraft("fishing_rod"), 12, 64));
    GolemHeldTool tool = new GolemHeldTool(bone);

    assertThat(tool.item()).hasValueSatisfying(item -> assertThat(GolemTestItems.damage(item)).isEqualTo(12));
    assertThat(tool.hold(GolemTestItems.stack(Id.minecraft("diamond_pickaxe"), 1))).isFalse();
    tool.sync();
    tool.wear(() -> 0D);

    verify(skeleton).damageEquipment(EquipmentSlot.MAIN_HAND, 1);
    assertThat(bone.state().held()).isNull();
    assertThat(bone.equipped(GolemEquipmentSlot.MAIN_HAND)).hasValueSatisfying(item -> assertThat(item.type()).isEqualTo(Id.minecraft("fishing_rod")));
    verify(skeleton, times(1)).setEquipment(eq(EquipmentSlot.MAIN_HAND), any());
  }

  @Test
  void nativeWearAndBreakageUpdatesOnlySlotsWithSuppliedEquipment() {
    bone.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.tool(Id.minecraft("iron_boots"), 5, 195));

    bone.equipmentChanged(GolemEquipmentSlot.BOOTS, GolemTestItems.tool(Id.minecraft("iron_boots"), 7, 195));
    bone.equipmentChanged(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("diamond_helmet"), 1));

    assertThat(bone.equipped(GolemEquipmentSlot.BOOTS)).hasValueSatisfying(item -> assertThat(GolemTestItems.damage(item)).isEqualTo(7));
    assertThat(bone.state().equipment()).containsOnlyKeys(GolemEquipmentSlot.BOOTS);
    bone.equipmentChanged(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Id.minecraft("air"), 0));
    assertThat(bone.state().equipment()).isEmpty();
  }

  private MobRef entity(GolemBody body) {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    MobRef entity = GolemTestEntities.golem(body);
    lenient().when(entity.location()).thenReturn(new Loc(world, 0, 64, 0));
    lenient().when(entity.world()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    return entity;
  }

  private static GolemState state(GolemBody body) {
    return GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK, GolemAnchor.of(WORLD, 0, 64, 0)));
  }
}
