package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
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
    InventoryView view = mock(InventoryView.class);
    AnvilInventory anvil = mock(AnvilInventory.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(anvil);
    when(anvil.getRepairCost()).thenReturn(9);
    when(anvil.getRenameText()).thenReturn("Heirloom");
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
    verify(anvil).setRepairCost(4);
    assertThat(event.getResult()).isSameAs(marked);
  }

  @Test
  @SuppressWarnings("deprecation")
  void pearlLandingFallDamageReadsAsEnderPearlDamage() {
    EnderPearl pearl = mock(EnderPearl.class);
    when(pearl.getShooter()).thenReturn(player);
    EntityDamageByEntityEvent landing = new EntityDamageByEntityEvent(pearl, player, EntityDamageEvent.DamageCause.FALL, 5.0D);
    EntityDamageEvent fall = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 5.0D);

    assertThat(Paper121Access.damageType(landing)).isEqualTo(Id.minecraft("ender_pearl"));
    assertThat(Paper121Access.causingEntity(landing)).isSameAs(player);
    assertThat(Paper121Access.damageType(fall)).isEqualTo(Id.minecraft("fall"));
    EntityDamageByEntityEvent projectileLanding = new EntityDamageByEntityEvent(pearl, player, EntityDamageEvent.DamageCause.PROJECTILE, 5.0D);
    EntityDamageByEntityEvent pearlHitsOther = new EntityDamageByEntityEvent(pearl, mock(Zombie.class), EntityDamageEvent.DamageCause.PROJECTILE, 0.0D);
    assertThat(Paper121Access.damageType(projectileLanding)).isEqualTo(Id.minecraft("ender_pearl"));
    assertThat(Paper121Access.damageType(pearlHitsOther)).isEqualTo(Id.minecraft("thrown"));
  }

  @Test
  @SuppressWarnings("deprecation")
  void shotArrowDamageResolvesArrowTypeAndShooter() {
    Arrow arrow = mock(Arrow.class);
    when(arrow.getShooter()).thenReturn(player);
    EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, mock(Zombie.class),
        EntityDamageEvent.DamageCause.PROJECTILE, 4.0D);

    assertThat(Paper121Access.damageType(event)).isEqualTo(Id.minecraft("arrow"));
    assertThat(Paper121Access.causingEntity(event)).isSameAs(player);
  }

  @Test
  void strongPotionCloudMapsToVanillaPotionId() {
    AreaEffectCloud cloud = mock(AreaEffectCloud.class);
    when(cloud.getBasePotionData()).thenReturn(new PotionData(PotionType.SPEED, false, true));

    assertThat(Paper121Access.basePotion(cloud)).isEqualTo(Id.minecraft("strong_swiftness"));
  }
}
