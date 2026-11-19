package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Strider;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Before 1.21.5 there is no saddle equipment slot: striders and pigs keep a saddle flag, horses an inventory slot. */
class BukkitLivingRefSaddleTest {
  @Test
  void steerableMobsTakeTheSaddleAsTheirSaddleFlag() {
    Strider strider = mock(Strider.class);
    EntityEquipment equipment = mock(EntityEquipment.class);
    lenient().when(strider.getUniqueId()).thenReturn(UUID.randomUUID());
    when(strider.getEquipment()).thenReturn(equipment);
    BukkitLivingRef living = new BukkitLivingRef(strider);

    living.setEquipment(EquipmentSlot.SADDLE, BukkitRefs.item(saddle()));
    verify(strider).setSaddle(true);

    living.setEquipment(EquipmentSlot.SADDLE, BukkitRefs.item(stack(Material.STICK)));
    verify(strider).setSaddle(false);
  }

  @Test
  void horsesUseTheirInventorySaddleSlot() {
    Horse horse = mock(Horse.class);
    HorseInventory inventory = mock(HorseInventory.class);
    lenient().when(horse.getUniqueId()).thenReturn(UUID.randomUUID());
    when(horse.getEquipment()).thenReturn(mock(EntityEquipment.class));
    when(horse.getInventory()).thenReturn(inventory);
    BukkitLivingRef living = new BukkitLivingRef(horse);

    ItemStack saddle = saddle();
    living.setEquipment(EquipmentSlot.SADDLE, BukkitRefs.item(saddle));

    verify(inventory).setSaddle(saddle);
  }

  private static ItemStack saddle() {
    return stack(Material.SADDLE);
  }

  private static ItemStack stack(Material type) {
    ItemStack stack = mock(ItemStack.class);
    lenient().when(stack.getType()).thenReturn(type);
    lenient().when(stack.getAmount()).thenReturn(1);
    return stack;
  }
}
