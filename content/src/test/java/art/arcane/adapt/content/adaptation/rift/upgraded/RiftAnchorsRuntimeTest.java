package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.adaptation.rift.RiftAnchors;
import art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeEntity;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** Rift Anchors: honest placement, channelled trips for a pearl, sends that never duplicate, and owner-only use. */
class RiftAnchorsRuntimeTest {
  private static final Id PEARL = Id.minecraft("ender_pearl");
  private static final Id STONE = Id.minecraft("stone");
  private static final Id LODESTONE = Id.minecraft("lodestone");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private PocketRealm realm;
  private final Menus menus = new Menus();

  private final Predicate<PlayerRef> keeper = (PlayerRef p) -> true;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).menus(menus).build();
    player = platform.player("Voidwalker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRift.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    realm = SkillHost.of(registry.getSkill("rift")).getUpgrades().stream().filter(PocketRealm.class::isInstance)
        .map(PocketRealm.class::cast).findFirst().orElseThrow();
    PocketRealm.addWaystoneKeeper(keeper);
    for (int x = -4; x <= 40; x++) {
      for (int z = -4; z <= 4; z++) {
        platform.world().set(x, 60, z, "stone");
      }
    }
  }

  @AfterEach
  void stop() {
    PocketRealm.removeWaystoneKeeper(keeper);
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void anAnchorIsPaidFromTheHandAndTheLevelCapsHowManyStand() {
    mutate(1);
    player.holding(anchorItem(3));

    place(2);
    place(4);
    place(6);

    assertThat(platform.world().blockAt(2, 61, 0).type()).isEqualTo(LODESTONE);
    assertThat(platform.world().blockAt(4, 61, 0).type()).isEqualTo(LODESTONE);
    assertThat(platform.world().blockAt(6, 61, 0).isAir()).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(1);
    assertThat(anchors()).hasSize(2);
  }

  @Test
  void theAnchorItemDoesNothingWithoutPocketRealm() {
    player.holding(anchorItem(1));

    place(2);

    assertThat(platform.world().blockAt(2, 61, 0).isAir()).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(1);
  }

  @Test
  void aTripChannelsThenStepsOntoTheAnchorForOnePlainPearl() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    player.inventory().setItem(10, new FakeItem(PEARL, 2));

    realm.travel(player, anchors().get(1));
    platform.scheduler().advance(1);
    assertThat(player.location().x()).isLessThan(5D);
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().blockX()).isEqualTo(30);
    assertThat(player.location().blockY()).isEqualTo(62);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(1);
  }

  @Test
  void damageBreaksTheChannelAndThePearlStays() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    player.inventory().setItem(10, new FakeItem(PEARL, 1));

    realm.travel(player, anchors().get(1));
    adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 1D, FinalDamage.IDENTITY));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().x()).isLessThan(5D);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(1);
  }

  @Test
  void noPlainPearlMeansNoTrip() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);

    realm.travel(player, anchors().get(1));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().x()).isLessThan(5D);
  }

  @Test
  void sendingWithNothingBesideTheAnchorSpendsNoPearl() throws InterruptedException {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    player.inventory().setItem(10, new FakeItem(PEARL, 3));

    realm.send(player, anchors().get(0), anchors().get(1));
    settle(() -> false);

    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(3);
  }

  @Test
  void sendingFromFarFromYourAnchorMovesNothing() throws InterruptedException {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    InventoryRef source = chest(2, 61, 1);
    InventoryRef target = chest(30, 61, 1);
    source.addItem(new FakeItem(STONE, 64));
    player.inventory().setItem(10, new FakeItem(PEARL, 3));
    player.moveTo(new Loc(platform.world(), 20.5D, 61D, 0.5D));

    realm.send(player, anchors().get(0), anchors().get(1));
    settle(() -> false);

    assertThat(count(source.contents(), STONE)).isEqualTo(64);
    assertThat(count(target.contents(), STONE)).isZero();
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(3);
  }

  @Test
  void aSendThatDeliversNothingRefundsItsPearlsAndPutsTheItemsBack() throws InterruptedException {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    InventoryRef source = chest(2, 61, 1);
    for (int stack = 0; stack < 5; stack++) {
      source.addItem(new FakeItem(STONE, 64));
    }
    player.inventory().setItem(10, new FakeItem(PEARL, 3));

    realm.send(player, anchors().get(0), anchors().get(1));
    settle(() -> false);

    assertThat(count(source.contents(), STONE)).isEqualTo(5 * 64);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(3);
  }

  @Test
  void aSendPaysForWhatItMoved() throws InterruptedException {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    InventoryRef source = chest(2, 61, 1);
    InventoryRef target = chest(30, 61, 1);
    for (int stack = 0; stack < 5; stack++) {
      source.addItem(new FakeItem(STONE, 64));
    }
    player.inventory().setItem(10, new FakeItem(PEARL, 3));

    realm.send(player, anchors().get(0), anchors().get(1));
    settle(() -> count(target.contents(), STONE) > 0 && count(player.inventory().contents(), PEARL) < 3);

    assertThat(count(target.contents(), STONE)).isEqualTo(5 * 64);
    assertThat(count(source.contents(), STONE)).isZero();
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(1);
  }

  @Test
  void aWitherCannotTakeAnAnchor() {
    mutate(1);
    player.holding(anchorItem(1));
    place(2);
    FakeEntity wither = platform.world().add(new FakeEntity(Id.minecraft("wither"), new Loc(platform.world(), 3D, 62D, 0D)));

    EntityChangeBlockEvent change = adapt.post(new EntityChangeBlockEvent(wither, platform.world().blockAt(2, 61, 0),
        FakeBlockState.of("air")));

    assertThat(change.isCancelled()).isTrue();
    assertThat(anchors()).hasSize(1);
  }

  @Test
  void aLodestonePlacedWhereAnAnchorVanishedIsAnOrdinaryLodestone() {
    mutate(1);
    player.holding(anchorItem(1));
    place(2);
    platform.world().set(2, 61, 0, "air");
    FakePlayer other = platform.player("Other", 4.5D, 61D, 0.5D);
    adapt.join(other);
    BlockRef spot = platform.world().blockAt(2, 61, 0);
    platform.world().set(2, 61, 0, "lodestone");
    adapt.post(new BlockPlaceEvent(other, spot, FakeBlockState.of("air"), platform.world().blockAt(2, 60, 0),
        FakeItem.of("lodestone"), Hand.MAIN_HAND));

    BlockBreakEvent broken = adapt.post(new BlockBreakEvent(other, spot, true, 0));

    assertThat(broken.dropItems()).isTrue();
    assertThat(anchors()).isEmpty();
  }

  @Test
  void aTripToAVanishedAnchorForgetsIt() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    platform.world().set(30, 61, 0, "air");
    player.inventory().setItem(10, new FakeItem(PEARL, 2));

    realm.travel(player, anchors().get(1));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().x()).isLessThan(5D);
    assertThat(anchors()).hasSize(1);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(2);
  }

  @Test
  void theOwnerGetsTheAnchorBackAndAnyoneElseBreaksItForNothing() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(4);
    FakePlayer other = platform.player("Other", 6.5D, 61D, 0.5D);
    adapt.join(other);

    BlockBreakEvent own = adapt.post(new BlockBreakEvent(player, platform.world().blockAt(2, 61, 0), true, 0));
    BlockBreakEvent theirs = adapt.post(new BlockBreakEvent(other, platform.world().blockAt(4, 61, 0), true, 0));

    assertThat(own.dropItems()).isFalse();
    assertThat(theirs.dropItems()).isFalse();
    assertThat(anchors()).isEmpty();
    assertThat(droppedAnchors()).isEqualTo(1);
  }

  @Test
  void explosionsLeaveAnchorsStanding() {
    mutate(1);
    player.holding(anchorItem(1));
    place(2);
    BlockRef anchor = platform.world().blockAt(2, 61, 0);
    BlockRef other = platform.world().blockAt(3, 60, 0);
    FakeEntity creeper = platform.world().add(new FakeEntity(Id.minecraft("creeper"), new Loc(platform.world(), 3D, 61D, 0D)));

    EntityExplodeEvent blast = adapt.post(new EntityExplodeEvent(creeper, creeper.location(), List.of(anchor, other), 1F));

    assertThat(blast.blocks()).containsExactly(other);
  }

  @Test
  void theOwnerOpensTheAnchorListFromTheAnchorAndOthersDoNot() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);
    place(30);
    FakePlayer other = platform.player("Other", 3.5D, 61D, 1.5D);
    adapt.join(other);
    BlockRef anchor = platform.world().blockAt(2, 61, 0);

    PlayerInteractEvent stranger = adapt.post(rightClick(other, anchor));
    assertThat(menus.opened).isEmpty();
    player.holding(FakeItem.empty());
    PlayerInteractEvent owner = adapt.post(rightClick(player, anchor));

    assertThat(stranger.isCancelled()).isFalse();
    assertThat(owner.isCancelled()).isTrue();
    assertThat(menus.opened).hasSize(1);
    assertThat(menus.opened.getFirst().buttons().values()).filteredOn(button -> button.clickable()).hasSize(1);
  }

  @Test
  void anAnchorIsFoundByItsSpotAndForgottenOnceItIsNoLongerALodestone() {
    RiftAnchors store = RiftAnchors.load();
    for (int x = 0; x < 40; x++) {
      platform.world().set(x, 61, 2, "lodestone");
      assertThat(store.add(player.uuid(), platform.world().blockAt(x, 61, 2), 64)).isNotNull();
    }

    assertThat(store.at(platform.world().blockAt(17, 61, 2)).x()).isEqualTo(17);
    platform.world().set(17, 62, 2, "lodestone");
    assertThat(store.at(platform.world().blockAt(17, 62, 2))).isNull();
    platform.world().set(17, 61, 2, "stone");
    assertThat(store.at(platform.world().blockAt(17, 61, 2))).isNull();
    platform.world().set(17, 61, 2, "lodestone");

    assertThat(store.at(platform.world().blockAt(17, 61, 2))).isNull();
    assertThat(store.of(player.uuid())).hasSize(39);
    assertThat(RiftAnchors.load().of(player.uuid())).hasSize(39);
  }

  @Test
  void theMagnetControlIsReadAgainOnlyAfterThePreferenceChanges() {
    mutate(1);
    Loc from = player.location();

    adapt.post(new PlayerMoveEvent(player, from, from.add(0.2D, 0D, 0D)));
    assertThat(realm.hasTickDemand()).isFalse();
    PlayerPreferences.set(realm, profile, RiftVoidMagnet.CONTROL, RiftVoidMagnet.Control.ARMED);
    adapt.post(new PlayerMoveEvent(player, from, from.add(0.2D, 0D, 0D)));

    assertThat(realm.hasTickDemand()).isTrue();
  }

  @Test
  void aWaystoneCountsAgainstTheAnchorCapAndIsSetOncePerPlace() {
    mutate(1);
    player.holding(anchorItem(2));
    place(2);

    assertThat(realm.addWaystone(player, new Loc(platform.world(), 20D, 61D, 0D), "structure:a", "Village")).isNotNull();
    assertThat(realm.addWaystone(player, new Loc(platform.world(), 22D, 61D, 0D), "structure:b", "Temple")).isNull();
    place(6);

    assertThat(anchors()).hasSize(2);
    assertThat(platform.world().blockAt(6, 61, 0).isAir()).isTrue();
    assertThat(realm.waystone(player, "structure:a").blockless()).isTrue();
    assertThat(realm.removeWaystone(player, realm.waystone(player, "structure:a"))).isTrue();
    assertThat(realm.addWaystone(player, new Loc(platform.world(), 20D, 61D, 0D), "structure:a", "Village")).isNotNull();
    assertThat(realm.addWaystone(player, new Loc(platform.world(), 20D, 61D, 0D), "structure:a", "Village")).isNull();
  }

  @Test
  void aWaystoneSurvivesAReloadAndAnAnchorBlockCannotBeRemovedAsOne() {
    mutate(2);
    player.holding(anchorItem(1));
    place(2);
    realm.addWaystone(player, new Loc(platform.world(), 20D, 61D, 0D), "structure:a", "Village");

    List<RiftAnchors.Anchor> loaded = anchors();

    assertThat(loaded).hasSize(2);
    assertThat(loaded.get(1).blockless()).isTrue();
    assertThat(loaded.get(1).name()).isEqualTo("Village");
    assertThat(realm.removeWaystone(player, loaded.getFirst())).isFalse();
  }

  @Test
  void aTripToAWaystoneChannelsAndLandsOnTheNearestSafeStandForOnePearl() {
    mutate(1);
    realm.addWaystone(player, new Loc(platform.world(), 30D, 66D, 0D), "structure:a", "Village");
    player.inventory().setItem(10, new FakeItem(PEARL, 2));

    realm.travel(player, realm.waystone(player, "structure:a"));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().blockX()).isEqualTo(30);
    assertThat(player.location().blockY()).isEqualTo(61);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(1);
  }

  @Test
  void aWaystoneRecordedFromTheAirLandsOnTheGroundBelow() {
    mutate(1);
    realm.addWaystone(player, new Loc(platform.world(), 30D, 95D, 0D), "structure:a", "Village");
    player.inventory().setItem(10, new FakeItem(PEARL, 2));

    realm.travel(player, realm.waystone(player, "structure:a"));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().blockX()).isEqualTo(30);
    assertThat(player.location().blockY()).isEqualTo(61);
  }

  @Test
  void withoutTheWaystonesSignatureATripToAWaystoneFizzles() {
    mutate(1);
    PocketRealm.removeWaystoneKeeper(keeper);
    realm.addWaystone(player, new Loc(platform.world(), 30D, 66D, 0D), "structure:a", "Village");
    player.inventory().setItem(10, new FakeItem(PEARL, 2));

    realm.travel(player, realm.waystone(player, "structure:a"));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().blockX()).isZero();
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(2);
  }

  @Test
  void aWaystoneWithNowhereToStandIsBlockedAndCostsNothing() {
    mutate(1);
    realm.addWaystone(player, new Loc(platform.world(), 100D, 61D, 100D), "structure:a", "Void");
    player.inventory().setItem(10, new FakeItem(PEARL, 2));

    realm.travel(player, realm.waystone(player, "structure:a"));
    platform.scheduler().advance(realm.getChannelTicks(1) + 2);

    assertThat(player.location().blockX()).isZero();
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(2);
  }

  @Test
  void aDeliveryFillsTheAnchorsContainersForItsPearlsAndHandsBackTheRest() throws InterruptedException {
    mutate(1);
    player.holding(anchorItem(1));
    place(30);
    InventoryRef target = chest(30, 61, 1);
    for (int slot = 0; slot < 35; slot++) {
      target.setItem(slot, new FakeItem(STONE, 64));
    }
    player.inventory().setItem(10, new FakeItem(PEARL, 3));
    List<ItemRef> rest = new ArrayList<>();

    realm.deliver(player, anchors().getFirst(), List.of(new FakeItem(Id.minecraft("wheat"), 64),
        new FakeItem(Id.minecraft("wheat"), 64)), rest::addAll);
    settle(() -> !rest.isEmpty());

    assertThat(count(target.contents(), Id.minecraft("wheat"))).isEqualTo(64);
    assertThat(count(rest, Id.minecraft("wheat"))).isEqualTo(64);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(2);
  }

  @Test
  void aDeliveryIsRefusedWithoutPearlsOrToAWaystoneAndReturnsEverything() {
    mutate(1);
    player.holding(anchorItem(1));
    place(30);
    chest(30, 61, 1);
    List<ItemRef> rest = new ArrayList<>();

    realm.deliver(player, anchors().getFirst(), List.of(new FakeItem(Id.minecraft("wheat"), 64)), rest::addAll);
    realm.addWaystone(player, new Loc(platform.world(), 20D, 61D, 0D), "structure:a", "Village");
    player.inventory().setItem(10, new FakeItem(PEARL, 3));
    realm.deliver(player, realm.waystone(player, "structure:a"), List.of(new FakeItem(Id.minecraft("wheat"), 8)),
        rest::addAll);

    assertThat(count(rest, Id.minecraft("wheat"))).isEqualTo(72);
    assertThat(count(player.inventory().contents(), PEARL)).isEqualTo(3);
  }

  private void place(int x) {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), platform.world().blockAt(x, 60, 0), BlockFace.UP, null, UseResult.DEFAULT,
        UseResult.DEFAULT));
    platform.scheduler().advance(2);
  }

  private void settle(BooleanSupplier done) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000L;
    while (!done.getAsBoolean() && System.currentTimeMillis() < deadline) {
      platform.scheduler().advance(1);
      Thread.sleep(10L);
    }
  }

  private static PlayerInteractEvent rightClick(PlayerRef who, BlockRef block) {
    return new PlayerInteractEvent(who, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, who.inventory().mainHand(),
        block, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT);
  }

  private InventoryRef chest(int x, int y, int z) {
    platform.world().set(x, y, z, "chest");
    FakeInventory inventory = new FakeInventory();
    platform.world().blockInventory(new BlockPos(x, y, z), inventory);
    return inventory;
  }

  private ItemRef anchorItem(int amount) {
    MutationTool<?> tool = realm.getTools().getFirst();
    ItemRef item = ((RiftAnchor) tool).create(CustomItem.Plain.INSTANCE);
    item.setMaxStackSize(64);
    item.setAmount(amount);
    return item;
  }

  private List<RiftAnchors.Anchor> anchors() {
    return RiftAnchors.load().of(player.uuid());
  }

  private long droppedAnchors() {
    RiftAnchor tool = (RiftAnchor) realm.getTools().getFirst();
    return platform.world().entities().stream()
        .filter((EntityRef entity) -> entity instanceof ItemEntityRef item && tool.is(item.item())).count();
  }

  private static int count(List<ItemRef> stacks, Id type) {
    int count = 0;
    for (ItemRef stack : stacks) {
      if (stack != null && stack.type().equals(type)) {
        count += stack.amount();
      }
    }
    return count;
  }

  private void mutate(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("rift", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("rift"), registry.getSkill("stealth"),
        "Voidwalker")).isEqualTo(SkillMutations.Result.MUTATED);
    profile.getData().getSkillLine("rift").setAdaptation(realm, level);
  }

  private static final class Menus implements HostMenus {
    private final List<ChestMenu> opened = new ArrayList<>();

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu menu) {
      opened.add(menu);
      return new MenuSession() {
        @Override
        public PlayerRef viewer() {
          return viewer;
        }

        @Override
        public ChestMenu menu() {
          return menu;
        }

        @Override
        public boolean isOpen() {
          return true;
        }

        @Override
        public void update(ChestMenu updated) {
        }

        @Override
        public void close() {
        }
      };
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
      return null;
    }

    @Override
    public void closeAll() {
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
      return null;
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
      return null;
    }
  }
}
