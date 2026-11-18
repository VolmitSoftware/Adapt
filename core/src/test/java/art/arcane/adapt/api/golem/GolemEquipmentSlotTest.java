package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.entity.EquipmentSlot;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GolemEquipmentSlotTest {
  @Test
  void theSixSlotsMapToTheirSkeletonEquipmentSlotsAndStableIds() {
    assertThat(GolemEquipmentSlot.values()).containsExactly(GolemEquipmentSlot.HELMET, GolemEquipmentSlot.CHESTPLATE, GolemEquipmentSlot.LEGGINGS,
        GolemEquipmentSlot.BOOTS, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(GolemEquipmentSlot.HELMET.slot()).isEqualTo(EquipmentSlot.HEAD);
    assertThat(GolemEquipmentSlot.CHESTPLATE.slot()).isEqualTo(EquipmentSlot.CHEST);
    assertThat(GolemEquipmentSlot.LEGGINGS.slot()).isEqualTo(EquipmentSlot.LEGS);
    assertThat(GolemEquipmentSlot.BOOTS.slot()).isEqualTo(EquipmentSlot.FEET);
    assertThat(GolemEquipmentSlot.MAIN_HAND.slot()).isEqualTo(EquipmentSlot.MAIN_HAND);
    assertThat(GolemEquipmentSlot.OFF_HAND.slot()).isEqualTo(EquipmentSlot.OFF_HAND);
    for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
      assertThat(GolemEquipmentSlot.fromId(slot.id())).contains(slot);
    }
    assertThat(GolemEquipmentSlot.fromId("cape")).isEmpty();
    assertThat(GolemEquipmentSlot.fromId(null)).isEmpty();
  }

  @Test
  void armorPiecesFitOnlyTheirOwnSlot() {
    assertThat(accepting(Id.minecraft("iron_helmet"))).containsExactly(GolemEquipmentSlot.HELMET, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Id.minecraft("diamond_chestplate"))).containsExactly(GolemEquipmentSlot.CHESTPLATE, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Id.minecraft("netherite_leggings"))).containsExactly(GolemEquipmentSlot.LEGGINGS, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Id.minecraft("leather_boots"))).containsExactly(GolemEquipmentSlot.BOOTS, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Id.minecraft("turtle_helmet"))).contains(GolemEquipmentSlot.HELMET);
    assertThat(accepting(Id.minecraft("elytra"))).contains(GolemEquipmentSlot.CHESTPLATE).doesNotContain(GolemEquipmentSlot.HELMET);
  }

  @Test
  void carvedPumpkinsAndHeadsFitTheHelmetSlot() {
    for (Id head : new Id[] {Id.minecraft("carved_pumpkin"), Id.minecraft("skeleton_skull"), Id.minecraft("wither_skeleton_skull"), Id.minecraft("player_head"),
        Id.minecraft("zombie_head"), Id.minecraft("creeper_head"), Id.minecraft("dragon_head"), Id.minecraft("piglin_head")}) {
      assertThat(accepting(head)).as("slots for %s", head).contains(GolemEquipmentSlot.HELMET).doesNotContain(GolemEquipmentSlot.BOOTS);
    }
    assertThat(GolemEquipmentSlot.HELMET.accepts(Id.minecraft("pumpkin"))).isFalse();
  }

  @Test
  void anyItemFitsEitherHandButAirFitsNothing() {
    for (Id item : new Id[] {Id.minecraft("bow"), Id.minecraft("stone"), Id.minecraft("stick"), Id.minecraft("iron_sword"), Id.minecraft("shield")}) {
      assertThat(GolemEquipmentSlot.MAIN_HAND.accepts(item)).isTrue();
      assertThat(GolemEquipmentSlot.OFF_HAND.accepts(item)).isTrue();
      assertThat(GolemEquipmentSlot.HELMET.accepts(item)).isFalse();
      assertThat(GolemEquipmentSlot.CHESTPLATE.accepts(item)).isFalse();
      assertThat(GolemEquipmentSlot.LEGGINGS.accepts(item)).isFalse();
      assertThat(GolemEquipmentSlot.BOOTS.accepts(item)).isFalse();
    }
    assertThat(accepting(Id.minecraft("air"))).isEmpty();
  }

  private static Set<GolemEquipmentSlot> accepting(Id material) {
    Set<GolemEquipmentSlot> fits = EnumSet.noneOf(GolemEquipmentSlot.class);
    for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
      if (slot.accepts(material)) {
        fits.add(slot);
      }
    }
    return fits;
  }
}
