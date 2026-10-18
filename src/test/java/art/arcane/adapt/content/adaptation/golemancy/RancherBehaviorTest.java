package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class RancherBehaviorTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000025b1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000025b2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000025b3");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000025b4");
  private static final NodeKey KEY = new NodeKey(WORLD, 0, 63, 0, BlockFace.UP);
  private static final GolemState.Anchor CHEST = GolemState.Anchor.of(WORLD, 8, 64, 8);
  private static final long NOW = 90_000L;

  private final GolemRancherCore.Config config = new GolemRancherCore.Config();
  private final World world = mock(World.class, withSettings().strictness(Strictness.LENIENT));
  private final List<Animals> animals = new ArrayList<>();
  private final List<NodeIndex.Entry> nearby = new ArrayList<>();
  private final List<ItemStack> cargo = new ArrayList<>();
  private final List<ItemStack> dropped = new ArrayList<>();
  private final ItemStack[] chest = new ItemStack[27];
  private final Random random = mock(Random.class);
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).radii(6, 2, 6).build();
  private GolemRancherCore core;
  private RancherActions actions;
  private RancherBehavior behavior;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<RancherFeedback> feedback;
  private GolemState.Anchor homeChest = CHEST;
  private GolemBody body = GolemBody.COPPER;
  private int channels = ChannelMask.NONE;
  private int level = 5;
  private boolean mobLoot = true;
  private EntityDeathEvent death;
  private UUID current;

  @BeforeEach
  void setUp() {
    core = mock(GolemRancherCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.ownerLevel(OWNER)).thenAnswer(invocation -> level);
    lenient().when(core.species()).thenReturn(RancherRules.parseSpecies(config.species));
    lenient().when(random.nextInt(anyInt())).thenReturn(1);
    actions = new RancherActions(core, new RancherActions.Hooks(GolemTestItems::stack, () -> random, ignored -> mobLoot));
    behavior = new RancherBehavior(core, actions);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    lenient().when(world.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      BoundingBox box = invocation.getArgument(0);
      Predicate<Entity> filter = invocation.getArgument(1);
      List<Entity> found = new ArrayList<>();
      for (Animals animal : animals) {
        Location at = animal.getLocation();
        if (box.contains(at.getX(), at.getY(), at.getZ()) && (filter == null || filter.test(animal))) {
          found.add(animal);
        }
      }
      return found;
    });
    lenient().when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
      Block block = mock(Block.class);
      lenient().when(block.getX()).thenReturn(invocation.getArgument(0));
      lenient().when(block.getY()).thenReturn(invocation.getArgument(1));
      lenient().when(block.getZ()).thenReturn(invocation.getArgument(2));
      return block;
    });
    lenient().when(world.dropItemNaturally(any(Location.class), any(ItemStack.class))).thenAnswer(invocation -> {
      dropped.add(invocation.getArgument(1));
      return null;
    });
    NodeIndex index = mock(NodeIndex.class);
    lenient().when(index.near(eq(WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> List.copyOf(nearby));
    NodeService nodes = mock(NodeService.class);
    lenient().when(nodes.index()).thenReturn(index);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    feedback = mockStatic(RancherFeedback.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));
  }

  @AfterEach
  void close() {
    feedback.close();
    runtime.close();
  }

  @Test
  void itWorksForTheRancherCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
    assertThat(behavior.actIntervalTicks(golem(0), task("breed", cow(1, 64, 1)))).isEqualTo(config.actionIntervalTicks);
  }

  @Test
  void aReadyPairBelowTheKeepCountIsFedNearestFirst() {
    node();
    Cow far = cow(4, 64, 0);
    Cow near = cow(1, 64, 0);
    carry(Material.WHEAT, 2);

    assertThat(seek(golem(0))).isEqualTo("breed " + near.getUniqueId());

    cargo.clear();
    assertThat(seek(golem(0))).isEmpty();
    assertThat(far.getUniqueId()).isNotEqualTo(near.getUniqueId());
  }

  @Test
  void onlyLoveARancherStartedGetsAPartner() {
    node();
    Cow waiting = cow(1, 64, 0);
    when(waiting.isLoveMode()).thenReturn(true);
    Cow partner = cow(3, 64, 0);
    carry(Material.WHEAT, 1);

    assertThat(seek(golem(0))).isEqualTo("breed " + partner.getUniqueId());

    when(waiting.getBreedCause()).thenReturn(STRANGER);
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void breedingFoodCanComeFromTheLinkedHomeChest() {
    node();
    Cow near = cow(1, 64, 0);
    cow(4, 64, 0);
    stock(4, Material.WHEAT, 2);

    assertThat(seek(golem(0))).isEqualTo("breed " + near.getUniqueId());

    stock(4, Material.WHEAT, 1);
    assertThat(seek(golem(0))).isEmpty();

    stock(4, Material.WHEAT, 2);
    homeChest = null;
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void onlyTheOwnersPastureNodesOnAMatchingChannelCount() {
    cow(1, 64, 0);
    cow(2, 64, 0);
    carry(Material.WHEAT, 2);
    record = NodeRecord.builder(STRANGER, NodeType.PASTURE, BlockFace.UP).radii(6, 2, 6).build();
    node();
    assertThat(seek(golem(0))).isEmpty();

    nearby.clear();
    record = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).radii(6, 2, 6).channel(DyeColor.RED).build();
    node();
    assertThat(seek(golem(0))).isEmpty();

    channels = ChannelMask.of(DyeColor.RED);
    assertThat(seek(golem(0))).startsWith("breed ");
  }

  @Test
  void higherPriorityPasturesComeFirstThenNearerOnes() {
    NodeKey farKey = new NodeKey(WORLD, 20, 63, 0, BlockFace.UP);
    NodeRecord far = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).radii(6, 2, 6).priority(5).build();
    node();
    nearby.add(new NodeIndex.Entry(farKey, far));
    Cow near = cow(1, 64, 0);
    cow(2, 64, 0);
    Cow distant = cow(21, 64, 0);
    cow(22, 64, 0);
    carry(Material.WHEAT, 2);

    assertThat(behavior.seek(golem(0)).map(task -> task.target().entity())).contains(distant.getUniqueId());

    nearby.set(1, new NodeIndex.Entry(farKey, far.toBuilder().priority(NodeRecord.DEFAULT_PRIORITY).build()));
    assertThat(seek(golem(0))).isEqualTo("breed " + near.getUniqueId());
  }

  @Test
  void animalsOutsideTheAreaOrOfAnotherSpeciesAreLeftAlone() {
    node();
    cow(9, 64, 0);
    cow(10, 64, 0);
    carry(Material.WHEAT, 4);
    assertThat(seek(golem(0))).isEmpty();

    animals.clear();
    cow(1, 64, 0);
    cow(2, 64, 0);
    nearby.clear();
    record = record.toBuilder().data(Map.of("species", "SHEEP")).build();
    node();
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void skippedAnimalsAndAnimalsBeyondTheWorkRadiusArePassedOver() {
    node();
    Cow near = cow(1, 64, 0);
    Cow far = cow(3, 64, 0);
    carry(Material.WHEAT, 2);
    GolemContext golem = golem(0);
    when(golem.skips(any(GolemTask.class))).thenAnswer(invocation -> invocation.<GolemTask>getArgument(0).target().entity().equals(near.getUniqueId()));

    assertThat(seek(golem)).isEqualTo("breed " + far.getUniqueId());

    when(golem.inWorkRadius(any(Location.class))).thenAnswer(invocation -> invocation.<Location>getArgument(0).getX() < 2D);
    assertThat(seek(golem)).isEmpty();
  }

  @Test
  void ironRanchersCullTheNearestSpareAdultOnlyWhenCullingIsOn() {
    body = GolemBody.IRON;
    record = record.toBuilder().data(Map.of("keep", "2")).build();
    node();
    cow(3, 64, 0);
    Cow nearest = cow(1, 64, 0);
    cow(2, 64, 0);
    assertThat(seek(golem(0))).isEmpty();

    nearby.clear();
    record = RancherRules.toggle(record, RancherRules.Action.CULL);
    node();
    assertThat(seek(golem(0))).isEqualTo("cull " + nearest.getUniqueId());

    body = GolemBody.COPPER;
    assertThat(seek(golem(0))).isEmpty();
    body = GolemBody.IRON;
    level = 3;
    assertThat(seek(golem(0))).isEmpty();
  }

  @Test
  void feedingWindsUpThenTakesOneFoodFromCargoAndStartsLoveMode() {
    Cow target = cow(1, 64, 0);
    cow(2, 64, 0);
    carry(Material.WHEAT, 2);
    stock(3, Material.WHEAT, 5);

    assertThat(behavior.act(golem(0), task("breed", target))).isEqualTo(GolemActResult.WORKING);
    assertThat(count(cargo, Material.WHEAT)).isEqualTo(2);

    level = 4;
    GolemContext golem = golem(1);
    assertThat(behavior.act(golem, task("breed", target))).isEqualTo(GolemActResult.DONE);

    assertThat(count(cargo, Material.WHEAT)).isEqualTo(1);
    assertThat(chest[3].getAmount()).isEqualTo(5);
    verify(target).setBreedCause(null);
    verify(target).setLoveModeTicks(RancherRules.LOVE_TICKS);
    verify(golem).workXp(config.workXpPerPair / 2D);
    verify(core).markFed(target.getUniqueId(), NOW, false);
    feedback.verify(() -> RancherFeedback.play(eq(core), any(Location.class), eq(RancherFeedback.Kind.BREED)));
  }

  @Test
  void feedingFallsBackToTheHomeChestAndHusbandryMarksTheParent() {
    Cow target = cow(1, 64, 0);
    stock(3, Material.WHEAT, 3);

    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.DONE);

    assertThat(chest[3].getAmount()).isEqualTo(2);
    verify(core).markFed(target.getUniqueId(), NOW, true);
  }

  @Test
  void feedingWithoutFoodAtTheKeepCountOrUnderProtectionNeverFeeds() {
    Cow target = cow(1, 64, 0);
    cow(2, 64, 0);
    assertThat(behavior.act(golem(0), task("breed", target))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.FAILED);

    carry(Material.WHEAT, 2);
    record = record.toBuilder().data(Map.of("keep", "2")).build();
    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.FAILED);

    record = record.toBuilder().data(Map.of()).build();
    GolemContext denied = golem(1);
    when(denied.canInteract(any(Location.class))).thenReturn(false);
    assertThat(behavior.act(denied, task("breed", target))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(count(cargo, Material.WHEAT)).isEqualTo(2);
    verify(target, never()).setLoveModeTicks(anyInt());
  }

  @Test
  void theKeepCountCountsOnlyTheTargetsSpecies() {
    record = record.toBuilder().data(Map.of("keep", "3")).build();
    Cow target = cow(1, 64, 0);
    cow(2, 64, 0);
    sheep(3, 64, 0, DyeColor.WHITE);
    sheep(4, 64, 0, DyeColor.WHITE);
    sheep(5, 64, 0, DyeColor.WHITE);
    carry(Material.WHEAT, 1);

    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.DONE);
  }

  @Test
  void aCalfInTheAreaBlocksFeedingAtTheKeepCount() {
    record = record.toBuilder().data(Map.of("keep", "3")).build();
    Cow target = cow(1, 64, 0);
    cow(2, 64, 0);
    Cow calf = cow(3, 64, 0);
    when(calf.isAdult()).thenReturn(false);
    carry(Material.WHEAT, 2);

    assertThat(behavior.act(golem(1), task("breed", target))).isEqualTo(GolemActResult.FAILED);
    verify(target, never()).setLoveModeTicks(anyInt());
  }

  @Test
  void protectedAnimalsAreNeverChosenOrWorked() {
    node();
    Cow invulnerable = cow(1, 64, 0);
    when(invulnerable.isInvulnerable()).thenReturn(true);
    Cow near = cow(2, 64, 0);
    cow(3, 64, 0);
    carry(Material.WHEAT, 2);

    assertThat(seek(golem(0))).isEqualTo("breed " + near.getUniqueId());
    assertThat(behavior.act(golem(1), task("breed", invulnerable))).isEqualTo(GolemActResult.FAILED);
    verify(invulnerable, never()).setLoveModeTicks(anyInt());
  }

  @Test
  void shearingMarksTheSheepShornAndCarriesWoolOfItsColor() {
    Sheep sheep = sheep(1, 64, 0, DyeColor.RED);
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("shear", sheep))).isEqualTo(GolemActResult.DONE);

    verify(sheep).setSheared(true);
    assertThat(count(cargo, Material.RED_WOOL)).isEqualTo(2);
    verify(golem).workXp(config.workXpPerShear);
    feedback.verify(() -> RancherFeedback.play(eq(core), any(Location.class), eq(RancherFeedback.Kind.SHEAR)));
  }

  @Test
  void shearingNeedsItsLevelAnUnshornAdultAndTheToggle() {
    Sheep sheep = sheep(1, 64, 0, DyeColor.WHITE);
    level = 1;
    assertThat(behavior.act(golem(1), task("shear", sheep))).isEqualTo(GolemActResult.FAILED);

    level = 5;
    record = RancherRules.toggle(record, RancherRules.Action.SHEAR);
    assertThat(behavior.act(golem(1), task("shear", sheep))).isEqualTo(GolemActResult.FAILED);

    record = RancherRules.toggle(record, RancherRules.Action.SHEAR);
    when(sheep.isSheared()).thenReturn(true);
    assertThat(behavior.act(golem(1), task("shear", sheep))).isEqualTo(GolemActResult.FAILED);

    verify(sheep, never()).setSheared(true);
  }

  @Test
  void milkingFillsABucketFromTheHomeChest() {
    Cow cow = cow(1, 64, 0);
    stock(2, Material.BUCKET, 1);
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("milk", cow))).isEqualTo(GolemActResult.DONE);

    assertThat(chest[2]).isNull();
    assertThat(count(cargo, Material.MILK_BUCKET)).isEqualTo(1);
    verify(golem).workXp(config.workXpPerMilk);
    feedback.verify(() -> RancherFeedback.play(eq(core), any(Location.class), eq(RancherFeedback.Kind.MILK)));

    assertThat(behavior.act(golem(1), task("milk", cow))).isEqualTo(GolemActResult.FAILED);
    level = 2;
    stock(2, Material.BUCKET, 1);
    assertThat(behavior.act(golem(1), task("milk", cow))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void mooshroomsFillBowlsWithStewUnlessTheyHoldASuspiciousStew() {
    MushroomCow mooshroom = mooshroom(1, 64, 0);
    stock(5, Material.BOWL, 2);
    stock(6, Material.BUCKET, 1);

    assertThat(behavior.act(golem(1), task("milk", mooshroom))).isEqualTo(GolemActResult.DONE);
    assertThat(count(cargo, Material.MUSHROOM_STEW)).isEqualTo(1);
    assertThat(chest[5].getAmount()).isEqualTo(1);
    feedback.verify(() -> RancherFeedback.play(eq(core), any(Location.class), eq(RancherFeedback.Kind.STEW)));

    when(mooshroom.hasEffectsForNextStew()).thenReturn(true);
    assertThat(behavior.act(golem(1), task("milk", mooshroom))).isEqualTo(GolemActResult.DONE);
    assertThat(count(cargo, Material.MILK_BUCKET)).isEqualTo(1);
    assertThat(chest[5].getAmount()).isEqualTo(1);

    assertThat(behavior.act(golem(1), task("milk", mooshroom))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void cullingStrikesAsTheGolemAndCarriesTheDrops() {
    body = GolemBody.IRON;
    level = 4;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Cow victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    LootTable table = mock(LootTable.class);
    when(victim.getLootTable()).thenReturn(table);
    dies(victim, GolemTestItems.stack(Material.BEEF, 2));
    GolemContext golem = golem(1);

    assertThat(behavior.act(golem, task("cull", victim))).isEqualTo(GolemActResult.DONE);

    verify(table, never()).populateLoot(any(), any());

    Mob striker = golem.golem();
    verify(victim).damage(10D, striker);
    verify(death).setDroppedExp(0);
    assertThat(count(cargo, Material.BEEF)).isEqualTo(2);
    verify(golem).workXp(config.workXpPerCull);
    feedback.verify(() -> RancherFeedback.play(eq(core), any(Location.class), eq(RancherFeedback.Kind.CULL)));
  }

  @Test
  void husbandryAddsALootRollToEachCull() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Cow victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    LootTable table = mock(LootTable.class);
    when(victim.getLootTable()).thenReturn(table);
    ItemStack leather = GolemTestItems.stack(Material.LEATHER, 1);
    when(table.populateLoot(eq(random), any(LootContext.class))).thenReturn(List.of(leather));
    dies(victim, GolemTestItems.stack(Material.BEEF, 1));

    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.DONE);

    assertThat(count(cargo, Material.BEEF)).isEqualTo(1);
    assertThat(count(cargo, Material.LEATHER)).isEqualTo(1);
  }

  @Test
  void husbandryRollsNoExtraLootWhileMobsDropNothing() {
    body = GolemBody.IRON;
    mobLoot = false;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Cow victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    LootTable table = mock(LootTable.class);
    when(victim.getLootTable()).thenReturn(table);
    dies(victim, GolemTestItems.stack(Material.BEEF, 1));

    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.DONE);

    verify(table, never()).populateLoot(any(), any());
    assertThat(count(cargo, Material.BEEF)).isEqualTo(1);
  }

  @Test
  void tamedAnimalsAndAnimalsInAVehicleAreNeverCulled() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Cow tamed = animal(Cow.class, EntityType.COW, 1, 64, 0, Tameable.class);
    when(((Tameable) tamed).isTamed()).thenReturn(true);
    Cow riding = cow(2, 64, 0);
    when(riding.isInsideVehicle()).thenReturn(true);
    cow(3, 64, 0);
    cow(4, 64, 0);

    assertThat(behavior.act(golem(1), task("cull", tamed))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task("cull", riding))).isEqualTo(GolemActResult.FAILED);

    verify(tamed, never()).damage(anyDouble(), any(Entity.class));
    verify(riding, never()).damage(anyDouble(), any(Entity.class));
  }

  @Test
  void cullingSparesCopperGolemsGuardedAnimalsTheKeepCountAndProtectedPlaces() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Cow victim = cow(1, 64, 0);
    cow(2, 64, 0);
    Cow third = cow(3, 64, 0);

    GolemContext denied = golem(1);
    when(denied.canDamage(victim)).thenReturn(false);
    assertThat(behavior.act(denied, task("cull", victim))).isEqualTo(GolemActResult.BLOCKED);

    body = GolemBody.COPPER;
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);

    body = GolemBody.IRON;
    when(victim.customName()).thenReturn(Component.text("Bessie"));
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(victim.customName()).thenReturn(null);
    when(victim.isLeashed()).thenReturn(true);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(victim.isLeashed()).thenReturn(false);
    when(victim.getPassengers()).thenReturn(List.of(mock(Player.class)));
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(victim.getPassengers()).thenReturn(List.of());
    when(victim.isAdult()).thenReturn(false);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);
    when(victim.isAdult()).thenReturn(true);

    animals.remove(third);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.FAILED);

    verify(victim, never()).damage(anyDouble(), any(Entity.class));
  }

  @Test
  void saddledPigsAreNeverCulled() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Pig pig = animal(Pig.class, EntityType.PIG, 1, 64, 0);
    animal(Pig.class, EntityType.PIG, 2, 64, 0);
    animal(Pig.class, EntityType.PIG, 3, 64, 0);
    ItemStack saddle = GolemTestItems.stack(Material.SADDLE, 1);
    when(pig.getEquipment().getItem(EquipmentSlot.SADDLE)).thenReturn(saddle);

    assertThat(behavior.act(golem(1), task("cull", pig))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void aStrikeThatDoesNotKillKeepsWorkingAndARefusedOneIsBlocked() {
    body = GolemBody.IRON;
    record = RancherRules.toggle(record.toBuilder().data(Map.of("keep", "2")).build(), RancherRules.Action.CULL);
    Cow victim = cow(1, 64, 0);
    cow(2, 64, 0);
    cow(3, 64, 0);
    when(victim.getHealth()).thenReturn(10D, 4D);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.WORKING);

    when(victim.getHealth()).thenReturn(10D);
    assertThat(behavior.act(golem(1), task("cull", victim))).isEqualTo(GolemActResult.BLOCKED);
  }

  @Test
  void aTargetThatIsGoneOrLeftTheAreaFails() {
    Cow cow = cow(1, 64, 0);
    carry(Material.WHEAT, 2);
    GolemContext gone = golem(1);
    when(gone.targetEntity()).thenReturn(Optional.empty());
    assertThat(behavior.act(gone, task("breed", cow))).isEqualTo(GolemActResult.FAILED);

    when(cow.getLocation()).thenReturn(new Location(world, 20.5D, 64D, 0.5D));
    assertThat(behavior.act(golem(1), task("breed", cow))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1), task("tame", cow))).isEqualTo(GolemActResult.FAILED);
  }

  private void node() {
    nearby.add(new NodeIndex.Entry(KEY, record));
  }

  private void carry(Material type, int amount) {
    cargo.add(GolemTestItems.stack(type, amount));
  }

  private void stock(int slot, Material type, int amount) {
    chest[slot] = GolemTestItems.stack(type, amount);
  }

  private void dies(Animals victim, ItemStack drop) {
    AtomicBoolean dead = new AtomicBoolean();
    when(victim.isDead()).thenAnswer(invocation -> dead.get());
    doAnswer(invocation -> {
      List<ItemStack> drops = new ArrayList<>(List.of(drop));
      death = mock(EntityDeathEvent.class);
      when(death.getEntity()).thenReturn(victim);
      when(death.getDrops()).thenReturn(drops);
      actions.on(death);
      assertThat(drops).isEmpty();
      dead.set(true);
      return null;
    }).when(victim).damage(anyDouble(), any(Entity.class));
  }

  private Cow cow(int x, int y, int z) {
    return animal(Cow.class, EntityType.COW, x, y, z);
  }

  private Sheep sheep(int x, int y, int z, DyeColor color) {
    Sheep sheep = animal(Sheep.class, EntityType.SHEEP, x, y, z);
    lenient().when(sheep.getColor()).thenReturn(color);
    return sheep;
  }

  private MushroomCow mooshroom(int x, int y, int z) {
    return animal(MushroomCow.class, EntityType.MOOSHROOM, x, y, z);
  }

  private <T extends Animals> T animal(Class<T> type, EntityType species, int x, int y, int z, Class<?>... traits) {
    T animal = traits.length == 0
        ? mock(type, withSettings().strictness(Strictness.LENIENT))
        : mock(type, withSettings().strictness(Strictness.LENIENT).extraInterfaces(traits));
    UUID id = UUID.randomUUID();
    PersistentDataContainer data = mock(PersistentDataContainer.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(animal.getPersistentDataContainer()).thenReturn(data);
    lenient().when(animal.getUniqueId()).thenReturn(id);
    lenient().when(animal.getType()).thenReturn(species);
    lenient().when(animal.getWorld()).thenReturn(world);
    lenient().when(animal.getLocation()).thenReturn(new Location(world, x + 0.5D, y, z + 0.5D));
    lenient().when(animal.isValid()).thenReturn(true);
    lenient().when(animal.isAdult()).thenReturn(true);
    lenient().when(animal.canBreed()).thenReturn(true);
    lenient().when(animal.getHealth()).thenReturn(10D);
    lenient().when(animal.isBreedItem(any(Material.class))).thenAnswer(invocation -> {
      Material food = invocation.getArgument(0);
      return species == EntityType.PIG ? food == Material.CARROT : food == Material.WHEAT;
    });
    EntityEquipment equipment = mock(EntityEquipment.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(animal.getEquipment()).thenReturn(equipment);
    animals.add(animal);
    return animal;
  }

  private String seek(GolemContext golem) {
    return behavior.seek(golem).map(task -> {
      assertThat(task.node()).contains(KEY);
      assertThat(task.nodeType()).contains(NodeType.PASTURE);
      assertThat(task.target().kind()).isEqualTo(GolemTarget.Kind.ENTITY);
      return task.type() + " " + task.target().entity();
    }).orElse("");
  }

  private GolemTask task(String type, Animals animal) {
    current = animal.getUniqueId();
    return GolemTask.builder(KEY, NodeType.PASTURE, type, GolemTarget.entity(animal)).build();
  }

  private GolemContext golem(int actIndex) {
    GolemContext golem = mock(GolemContext.class);
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    IronGolem entity = mock(IronGolem.class);
    lenient().when(entity.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.channels()).thenAnswer(invocation -> channels);
    lenient().when(state.cores()).thenReturn(List.of(GolemCoreType.RANCHER));
    lenient().when(state.homeChest()).thenAnswer(invocation -> homeChest);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(GOLEM);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.body()).thenAnswer(invocation -> body);
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.handle()).thenReturn(handle);
    lenient().when(golem.golem()).thenReturn(entity);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.node()).thenAnswer(invocation -> Optional.of(record));
    lenient().when(golem.nowMillis()).thenReturn(NOW);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.home()).thenReturn(new Location(world, 0D, 64D, 0D));
    lenient().when(golem.workRadius()).thenReturn(24D);
    lenient().when(golem.inWorkRadius(any(Location.class))).thenReturn(true);
    lenient().when(golem.owns(any(Location.class))).thenReturn(true);
    lenient().when(golem.canInteract(any(Location.class))).thenReturn(true);
    lenient().when(golem.canDamage(any())).thenReturn(true);
    lenient().when(golem.room()).thenReturn(64);
    lenient().when(golem.carriedItems()).thenAnswer(invocation -> List.copyOf(cargo));
    lenient().when(golem.carry(any(ItemStack.class))).thenAnswer(invocation -> {
      cargo.add(invocation.getArgument(0));
      return Optional.empty();
    });
    lenient().when(golem.take(any(), anyInt())).thenAnswer(invocation -> take(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(golem.container(any(Block.class))).thenAnswer(invocation -> {
      Block block = invocation.getArgument(0);
      return homeChest != null && block.getX() == CHEST.x() && block.getY() == CHEST.y() && block.getZ() == CHEST.z()
          ? Optional.of(inventory()) : Optional.empty();
    });
    lenient().when(golem.targetEntity()).thenAnswer(invocation -> target());
    return golem;
  }

  private Optional<Entity> target() {
    for (Animals animal : animals) {
      if (animal.getUniqueId().equals(current)) {
        return Optional.of(animal);
      }
    }
    return Optional.empty();
  }

  private int take(Predicate<ItemStack> matches, int amount) {
    int taken = 0;
    for (ItemStack item : cargo) {
      while (taken < amount && item.getAmount() > 0 && matches.test(item.clone())) {
        item.setAmount(item.getAmount() - 1);
        taken++;
      }
    }
    cargo.removeIf(item -> item.getAmount() <= 0);
    return taken;
  }

  private Inventory inventory() {
    Inventory inventory = mock(Inventory.class);
    lenient().when(inventory.getStorageContents()).thenAnswer(invocation -> chest.clone());
    lenient().when(inventory.getItem(anyInt())).thenAnswer(invocation -> chest[invocation.<Integer>getArgument(0)]);
    doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
      return null;
    }).when(inventory).setItem(anyInt(), any());
    return inventory;
  }

  private static int count(Collection<ItemStack> items, Material type) {
    int total = 0;
    for (ItemStack item : items) {
      if (item != null && item.getType() == type) {
        total += item.getAmount();
      }
    }
    return total;
  }
}
