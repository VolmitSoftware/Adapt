package art.arcane.adapt.api.golem;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
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
    assertThat(GolemEquipmentSlot.MAIN_HAND.slot()).isEqualTo(EquipmentSlot.HAND);
    assertThat(GolemEquipmentSlot.OFF_HAND.slot()).isEqualTo(EquipmentSlot.OFF_HAND);
    for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
      assertThat(GolemEquipmentSlot.fromId(slot.id())).contains(slot);
    }
    assertThat(GolemEquipmentSlot.fromId("cape")).isEmpty();
    assertThat(GolemEquipmentSlot.fromId(null)).isEmpty();
  }

  @Test
  void armorPiecesFitOnlyTheirOwnSlot() {
    assertThat(accepting(Material.IRON_HELMET)).containsExactly(GolemEquipmentSlot.HELMET, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Material.DIAMOND_CHESTPLATE)).containsExactly(GolemEquipmentSlot.CHESTPLATE, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Material.NETHERITE_LEGGINGS)).containsExactly(GolemEquipmentSlot.LEGGINGS, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Material.LEATHER_BOOTS)).containsExactly(GolemEquipmentSlot.BOOTS, GolemEquipmentSlot.MAIN_HAND, GolemEquipmentSlot.OFF_HAND);
    assertThat(accepting(Material.TURTLE_HELMET)).contains(GolemEquipmentSlot.HELMET);
    assertThat(accepting(Material.ELYTRA)).contains(GolemEquipmentSlot.CHESTPLATE).doesNotContain(GolemEquipmentSlot.HELMET);
  }

  @Test
  void carvedPumpkinsAndHeadsFitTheHelmetSlot() {
    for (Material head : new Material[] {Material.CARVED_PUMPKIN, Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL, Material.PLAYER_HEAD,
        Material.ZOMBIE_HEAD, Material.CREEPER_HEAD, Material.DRAGON_HEAD, Material.PIGLIN_HEAD}) {
      assertThat(accepting(head)).as("slots for %s", head).contains(GolemEquipmentSlot.HELMET).doesNotContain(GolemEquipmentSlot.BOOTS);
    }
    assertThat(GolemEquipmentSlot.HELMET.accepts(Material.PUMPKIN)).isFalse();
  }

  @Test
  void anyItemFitsEitherHandButAirFitsNothing() {
    for (Material item : new Material[] {Material.BOW, Material.STONE, Material.STICK, Material.IRON_SWORD, Material.SHIELD}) {
      assertThat(GolemEquipmentSlot.MAIN_HAND.accepts(item)).isTrue();
      assertThat(GolemEquipmentSlot.OFF_HAND.accepts(item)).isTrue();
      assertThat(GolemEquipmentSlot.HELMET.accepts(item)).isFalse();
      assertThat(GolemEquipmentSlot.CHESTPLATE.accepts(item)).isFalse();
      assertThat(GolemEquipmentSlot.LEGGINGS.accepts(item)).isFalse();
      assertThat(GolemEquipmentSlot.BOOTS.accepts(item)).isFalse();
    }
    assertThat(accepting(Material.AIR)).isEmpty();
  }

  private static Set<GolemEquipmentSlot> accepting(Material material) {
    Set<GolemEquipmentSlot> fits = EnumSet.noneOf(GolemEquipmentSlot.class);
    for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
      if (slot.accepts(material)) {
        fits.add(slot);
      }
    }
    return fits;
  }
}
