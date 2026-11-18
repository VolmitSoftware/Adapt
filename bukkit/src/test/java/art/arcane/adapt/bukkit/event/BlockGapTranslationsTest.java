package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.FurnaceSmeltEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockGapTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void pistonRetractReportsBlocksDirectionAndWritesBackCancel() {
    Block piston = mock(Block.class);
    Block moved = mock(Block.class);
    org.bukkit.event.block.BlockPistonRetractEvent event = new org.bukkit.event.block.BlockPistonRetractEvent(
        piston, List.of(moved), org.bukkit.block.BlockFace.NORTH);
    AtomicReference<BlockPistonEvent> seen = new AtomicReference<>();
    bus.register(this, BlockPistonEvent.class, EventPriority.HIGHEST, core -> {
      seen.set(core);
      core.setCancelled(true);
    });

    BlockGapTranslations.PISTON_RETRACT.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(seen.get().extending()).isFalse();
    assertThat(seen.get().direction()).isEqualTo(BlockFace.NORTH);
    assertThat(seen.get().movement()).isEqualTo(BlockFace.SOUTH);
    assertThat(seen.get().blocks()).containsExactly(refs.wrap(moved, BlockRef.class));
    assertThat(event.isCancelled()).isTrue();
  }

  @Test
  void furnaceSmeltPostsSubtypeAndWritesBackResult() {
    Block furnace = mock(Block.class);
    ItemStack source = mock(ItemStack.class);
    ItemStack result = mock(ItemStack.class);
    ItemStack boosted = mock(ItemStack.class);
    org.bukkit.event.inventory.FurnaceSmeltEvent event = new org.bukkit.event.inventory.FurnaceSmeltEvent(furnace, source, result);
    AtomicReference<BlockCookEvent> seen = new AtomicReference<>();
    bus.register(this, BlockCookEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      core.setResult(refs.wrap(boosted, ItemRef.class));
    });

    assertThat(BlockGapTranslations.COOK.target(event)).isEqualTo(FurnaceSmeltEvent.class);
    BlockGapTranslations.COOK.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(seen.get()).isInstanceOf(FurnaceSmeltEvent.class);
    assertThat(seen.get().source()).isSameAs(refs.wrap(source, ItemRef.class));
    assertThat(event.getResult()).isSameAs(boosted);
  }

  @Test
  void brewResultsAreReplacedByIndex() {
    Block stand = mock(Block.class);
    BrewerInventory contents = mock(BrewerInventory.class);
    ItemStack bottle = mock(ItemStack.class);
    ItemStack ingredient = mock(ItemStack.class);
    when(contents.getItem(0)).thenReturn(bottle);
    when(contents.getItem(1)).thenReturn(bottle);
    when(contents.getItem(2)).thenReturn(bottle);
    when(contents.getIngredient()).thenReturn(ingredient);
    ItemStack first = mock(ItemStack.class);
    ItemStack second = mock(ItemStack.class);
    ItemStack splash = mock(ItemStack.class);
    List<ItemStack> results = new ArrayList<>(List.of(first, second));
    org.bukkit.event.inventory.BrewEvent event = new org.bukkit.event.inventory.BrewEvent(stand, contents, results, 12);
    AtomicReference<BrewEvent> seen = new AtomicReference<>();
    bus.register(this, BrewEvent.class, EventPriority.NORMAL, core -> {
      seen.set(core);
      core.results().set(1, refs.wrap(splash, ItemRef.class));
    });

    BlockGapTranslations.BREW.bridge(event, core -> bus.post(core, EventPriority.NORMAL));

    assertThat(seen.get().ingredient()).isSameAs(refs.wrap(ingredient, ItemRef.class));
    assertThat(seen.get().potions()).hasSize(3);
    assertThat(seen.get().fuelLevel()).isEqualTo(12);
    assertThat(event.getResults()).containsExactly(first, splash);
  }

  @Test
  void entityBlockFormCarriesTheEntity() {
    Block block = mock(Block.class);
    BlockState state = mock(BlockState.class);
    BlockData data = mock(BlockData.class);
    when(state.getBlockData()).thenReturn(data);
    Snowman golem = mock(Snowman.class);
    org.bukkit.event.block.EntityBlockFormEvent event = new org.bukkit.event.block.EntityBlockFormEvent(golem, block, state);
    AtomicReference<BlockFormEvent> seen = new AtomicReference<>();
    bus.register(this, BlockFormEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      core.setCancelled(true);
    });

    BlockGapTranslations.FORM.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(seen.get().entity()).isSameAs(refs.wrap(golem, EntityRef.class));
    assertThat(event.isCancelled()).isTrue();
  }
}
