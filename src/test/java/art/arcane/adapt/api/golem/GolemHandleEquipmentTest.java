package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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
import static org.mockito.Mockito.verify;

class GolemHandleEquipmentTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

  private FakeGolemPlatform platform;
  private Skeleton skeleton;
  private EntityEquipment worn;
  private GolemHandle bone;
  private Map<EquipmentSlot, ItemStack> visible;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    platform.join(OWNER, new GolemRoster());
    skeleton = entity(Skeleton.class);
    worn = mock(EntityEquipment.class);
    visible = new EnumMap<>(EquipmentSlot.class);
    lenient().doAnswer(invocation -> {
      visible.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(worn).setItem(any(EquipmentSlot.class), any());
    lenient().when(worn.getItem(any(EquipmentSlot.class))).thenAnswer(invocation -> visible.get(invocation.<EquipmentSlot>getArgument(0)));
    lenient().when(skeleton.getEquipment()).thenReturn(worn);
    bone = new GolemHandle(skeleton, state(GolemBody.BONE), platform);
  }

  @Test
  void equippingStoresTheItemWritesItAtOnceAndPutsItOnTheSkeletonWithoutADropChance() {
    ItemStack helmet = GolemTestItems.stack(Material.IRON_HELMET, 1);

    assertThat(bone.equip(GolemEquipmentSlot.HELMET, helmet)).isTrue();

    assertThat(bone.state().equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
    ArgumentCaptor<ItemStack> wornItem = ArgumentCaptor.forClass(ItemStack.class);
    verify(worn).setItem(eq(EquipmentSlot.HEAD), wornItem.capture());
    assertThat(wornItem.getValue().getType()).isEqualTo(Material.IRON_HELMET);
    verify(worn).setDropChance(EquipmentSlot.HEAD, 0F);
  }

  @Test
  void everySlotEquipsItsOwnPieceAndBothHands() {
    Material[] pieces = {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.BOW, Material.SHIELD};

    for (int index = 0; index < pieces.length; index++) {
      assertThat(bone.equip(GolemEquipmentSlot.values()[index], GolemTestItems.stack(pieces[index], 1))).isTrue();
    }

    assertThat(bone.state().equipment()).hasSize(6);
    assertThat(bone.equipmentItems()).extracting(ItemStack::getType).containsExactlyInAnyOrder(pieces);
    verify(worn).setDropChance(EquipmentSlot.OFF_HAND, 0F);
    verify(worn).setDropChance(EquipmentSlot.HAND, 0F);
  }

  @Test
  void anItemThatDoesNotFitTheSlotIsRefusedAndNothingChanges() {
    assertThat(bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.IRON_BOOTS, 1))).isFalse();
    assertThat(bone.equip(GolemEquipmentSlot.CHESTPLATE, GolemTestItems.stack(Material.STONE, 1))).isFalse();
    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Material.AIR, 1))).isFalse();

    assertThat(bone.state().equipment()).isEmpty();
    assertThat(platform.encoded).isEmpty();
    verify(worn, never()).setItem(any(EquipmentSlot.class), any(ItemStack.class));
  }

  @Test
  void aStackOfSeveralItemsIsRefusedSoNoneAreLost() {
    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Material.ARROW, 16))).isFalse();

    assertThat(bone.state().equipment()).isEmpty();
  }

  @Test
  void anOccupiedSlotRefusesASecondItemSoTheFirstIsNeverOverwritten() {
    ItemStack first = GolemTestItems.stack(Material.BOW, 1);
    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, first)).isTrue();
    String stored = bone.state().equipment().get(GolemEquipmentSlot.MAIN_HAND);

    assertThat(bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Material.IRON_SWORD, 1))).isFalse();

    assertThat(bone.state().equipment()).containsEntry(GolemEquipmentSlot.MAIN_HAND, stored);
    assertThat(platform.encoded).hasSize(1);
  }

  @Test
  void onlyBoneGolemsWearEquipment() {
    IronGolem iron = entity(IronGolem.class);
    EntityEquipment ironWorn = mock(EntityEquipment.class);
    lenient().when(iron.getEquipment()).thenReturn(ironWorn);
    GolemHandle plated = new GolemHandle(iron, state(GolemBody.IRON), platform);

    assertThat(plated.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.IRON_HELMET, 1))).isFalse();

    assertThat(plated.state().equipment()).isEmpty();
    verify(ironWorn, never()).setItem(any(EquipmentSlot.class), any(ItemStack.class));
  }

  @Test
  void takingAnItemBackReturnsTheStoredItemAndClearsTheSlotAndTheSkeleton() {
    ItemStack bow = GolemTestItems.stack(Material.BOW, 1);
    bone.equip(GolemEquipmentSlot.MAIN_HAND, bow);
    String stored = bone.state().equipment().get(GolemEquipmentSlot.MAIN_HAND);

    Optional<ItemStack> taken = bone.unequip(GolemEquipmentSlot.MAIN_HAND, stored);

    assertThat(taken).hasValueSatisfying(item -> assertThat(item.getType()).isEqualTo(Material.BOW));
    assertThat(bone.state().equipment()).isEmpty();
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).isEmpty();
    verify(worn).setItem(EquipmentSlot.HAND, null);
  }

  @Test
  void aSecondTakeOfTheSameItemYieldsNothingSoRapidClicksCannotDuplicateIt() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.CARVED_PUMPKIN, 1));
    String stored = bone.state().equipment().get(GolemEquipmentSlot.HELMET);

    Optional<ItemStack> first = bone.unequip(GolemEquipmentSlot.HELMET, stored);
    Optional<ItemStack> second = bone.unequip(GolemEquipmentSlot.HELMET, stored);

    assertThat(first).isPresent();
    assertThat(second).isEmpty();
  }

  @Test
  void aStaleTakeNeverRemovesAnItemThatReplacedTheOneItWasMeantFor() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.IRON_HELMET, 1));
    String stale = bone.state().equipment().get(GolemEquipmentSlot.HELMET);
    bone.unequip(GolemEquipmentSlot.HELMET, stale);
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.DIAMOND_HELMET, 1));
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
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.IRON_HELMET, 1));
    String stored = bone.state().equipment().get(GolemEquipmentSlot.HELMET);
    lenient().when(skeleton.isValid()).thenReturn(false);

    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, stored)).isEmpty();
    assertThat(bone.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Material.IRON_BOOTS, 1))).isFalse();

    lenient().when(skeleton.isValid()).thenReturn(true);
    bone.retire();

    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, stored)).isEmpty();
    assertThat(bone.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Material.IRON_BOOTS, 1))).isFalse();
    assertThat(bone.state().equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
  }

  @Test
  void equipmentItemsListsEveryWornItemInSlotOrder() {
    bone.equip(GolemEquipmentSlot.OFF_HAND, GolemTestItems.stack(Material.SHIELD, 1));
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.IRON_HELMET, 1));

    List<ItemStack> items = bone.equipmentItems();

    assertThat(items).extracting(ItemStack::getType).containsExactly(Material.IRON_HELMET, Material.SHIELD);
  }

  @Test
  void nativeDurabilityDamageIsSavedBeforeTheEquippedItemCanBeTakenBack() {
    bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.tool(Material.IRON_SWORD, 20, 250));
    doAnswer(invocation -> {
      visible.put(EquipmentSlot.HAND, GolemTestItems.tool(Material.IRON_SWORD, 21, 250));
      return null;
    }).when(skeleton).damageItemStack(EquipmentSlot.HAND, 1);

    bone.damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);

    String encoded = bone.state().equipment().get(GolemEquipmentSlot.MAIN_HAND);
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).containsEntry(GolemEquipmentSlot.MAIN_HAND, encoded);
    assertThat(bone.unequip(GolemEquipmentSlot.MAIN_HAND, encoded)).hasValueSatisfying(item -> assertThat(GolemTestItems.damage(item)).isEqualTo(21));
  }

  @Test
  void aBrokenItemIsRemovedFromStoredEquipmentAndCannotBeReturned() {
    bone.equip(GolemEquipmentSlot.HELMET, GolemTestItems.tool(Material.IRON_HELMET, 164, 165));
    String encoded = bone.state().equipment().get(GolemEquipmentSlot.HELMET);
    doAnswer(invocation -> {
      visible.remove(EquipmentSlot.HEAD);
      return null;
    }).when(skeleton).damageItemStack(EquipmentSlot.HEAD, 1);

    bone.damageEquipment(GolemEquipmentSlot.HELMET, 1);

    assertThat(bone.state().equipment()).isEmpty();
    assertThat(GolemState.read(skeleton).orElseThrow().equipment()).isEmpty();
    assertThat(bone.unequip(GolemEquipmentSlot.HELMET, encoded)).isEmpty();
  }

  @Test
  void aBoneWorkerUsesItsEquippedRodWithoutReplacingItWithAVirtualTool() {
    bone.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.tool(Material.FISHING_ROD, 12, 64));
    GolemHeldTool tool = new GolemHeldTool(bone);

    assertThat(tool.item()).hasValueSatisfying(item -> assertThat(GolemTestItems.damage(item)).isEqualTo(12));
    assertThat(tool.hold(GolemTestItems.stack(Material.DIAMOND_PICKAXE, 1))).isFalse();
    tool.sync();
    tool.wear(() -> 0D);

    verify(skeleton).damageItemStack(EquipmentSlot.HAND, 1);
    assertThat(bone.state().held()).isNull();
    assertThat(bone.equipped(GolemEquipmentSlot.MAIN_HAND)).hasValueSatisfying(item -> assertThat(item.getType()).isEqualTo(Material.FISHING_ROD));
    verify(worn, never()).setItemInMainHand(any());
  }

  @Test
  void nativeWearAndBreakageUpdatesOnlySlotsWithSuppliedEquipment() {
    bone.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.tool(Material.IRON_BOOTS, 5, 195));

    bone.equipmentChanged(GolemEquipmentSlot.BOOTS, GolemTestItems.tool(Material.IRON_BOOTS, 7, 195));
    bone.equipmentChanged(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Material.DIAMOND_HELMET, 1));

    assertThat(bone.equipped(GolemEquipmentSlot.BOOTS)).hasValueSatisfying(item -> assertThat(GolemTestItems.damage(item)).isEqualTo(7));
    assertThat(bone.state().equipment()).containsOnlyKeys(GolemEquipmentSlot.BOOTS);
    bone.equipmentChanged(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Material.AIR, 0));
    assertThat(bone.state().equipment()).isEmpty();
  }

  private <T extends Mob> T entity(Class<T> type) {
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    T entity = GolemTestEntities.entity(type);
    lenient().when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    return entity;
  }

  private static GolemState state(GolemBody body) {
    return GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 64, 0)));
  }
}
