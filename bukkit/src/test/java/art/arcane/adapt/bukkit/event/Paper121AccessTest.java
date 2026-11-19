package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Paper121AccessTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;
  private Player player;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
    player = mock(Player.class);
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void anvilRepairCostAndResultAreWrittenBack() {
    AnvilView view = mock(AnvilView.class);
    AnvilInventory anvil = mock(AnvilInventory.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(anvil);
    when(view.getRepairCost()).thenReturn(9);
    when(view.getRenameText()).thenReturn("Heirloom");
    ItemStack result = mock(ItemStack.class);
    ItemStack marked = mock(ItemStack.class);
    org.bukkit.event.inventory.PrepareAnvilEvent event = new org.bukkit.event.inventory.PrepareAnvilEvent(view, result);
    AtomicReference<PrepareResultEvent> seen = new AtomicReference<>();
    bus.register(this, PrepareResultEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      PrepareAnvilEvent anvilEvent = (PrepareAnvilEvent) core;
      anvilEvent.setRepairCost(4);
      anvilEvent.setResult(refs.wrap(marked, ItemRef.class));
    });

    assertThat(InventoryGapTranslations.PREPARE_RESULT.target(event)).isEqualTo(PrepareAnvilEvent.class);
    InventoryGapTranslations.PREPARE_RESULT.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(((PrepareAnvilEvent) seen.get()).renameText()).isEqualTo("Heirloom");
    verify(view).setRepairCost(4);
    assertThat(event.getResult()).isSameAs(marked);
  }

  @Test
  void pearlLandingFallDamageReadsAsEnderPearlDamage() {
    Id fall = Id.minecraft("fall");
    assertThat(Paper121Access.damageType(fall, mock(EnderPearl.class))).isEqualTo(Id.minecraft("ender_pearl"));
    assertThat(Paper121Access.damageType(fall, mock(Snowball.class))).isEqualTo(fall);
    assertThat(Paper121Access.damageType(fall, null)).isEqualTo(fall);
    assertThat(Paper121Access.damageType(Id.minecraft("ender_pearl"), mock(EnderPearl.class))).isEqualTo(Id.minecraft("ender_pearl"));
    assertThat(Paper121Access.damageType(Id.minecraft("magic"), mock(EnderPearl.class))).isEqualTo(Id.minecraft("magic"));
  }
}
