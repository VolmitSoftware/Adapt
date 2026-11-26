package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneShrines;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockFadeEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tide Shrine, Salvage cast and Maelstrom's trident-only surge, each with the exploit it must refuse. */
class SeaborneDepthRuntimeTest {
  private static final Id NAUTILUS_SHELL = Id.minecraft("nautilus_shell");
  private static final Id SEA_LANTERN = Id.minecraft("sea_lantern");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");
  private static final Id SHIPWRECK = Id.minecraft("shipwreck");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Diver", 0.5D, 41D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSeaborne.class, SkillSwords.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    for (int y = 40; y <= 43; y++) {
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 3; z++) {
          platform.world().set(x, y, z, "water");
        }
      }
    }
    platform.world().set(0, 39, 2, "stone");
    platform.world().set(1, 39, 2, "stone");
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aTideShrineIsPlacedUnderwaterForOneNautilusShellAndKeptOnDisk() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    holdShrine(leviathan);
    player.inventory().setItem(20, new FakeItem(NAUTILUS_SHELL, 2));

    place(0, 39, 2);

    assertThat(platform.world().blockAt(0, 40, 2).type()).isEqualTo(SEA_LANTERN);
    assertThat(player.inventory().item(20).amount()).isEqualTo(1);
    assertThat(leviathan.shrinesOf(player)).hasSize(1);
    assertThat(new SeaborneShrines().of(player.uuid())).containsExactlyElementsOf(leviathan.shrinesOf(player));
  }

  @Test
  void noShellNoShrine() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    holdShrine(leviathan);

    place(0, 39, 2);

    assertThat(platform.world().blockAt(0, 40, 2).type()).isEqualTo(Id.minecraft("water"));
    assertThat(leviathan.shrinesOf(player)).isEmpty();
  }

  @Test
  void aShrineOverTheCapOnDryLandOrOnProtectedLandIsRefusedAndKeepsTheShell() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    holdShrine(leviathan);
    player.inventory().setItem(20, new FakeItem(NAUTILUS_SHELL, 3));
    place(0, 39, 2);

    place(1, 39, 2);
    platform.world().set(5, 40, 5, "stone");
    place(5, 40, 5);

    assertThat(platform.world().blockAt(1, 40, 2).type()).isEqualTo(Id.minecraft("water"));
    assertThat(leviathan.shrinesOf(player)).hasSize(1);
    assertThat(player.inventory().item(20).amount()).isEqualTo(2);

    learn(Leviathan.class, 3);
    platform.protection().deny(true);
    place(1, 39, 2);

    assertThat(platform.world().blockAt(1, 40, 2).type()).isEqualTo(Id.minecraft("water"));
    assertThat(leviathan.shrinesOf(player)).hasSize(1);
    assertThat(player.inventory().item(20).amount()).isEqualTo(2);
  }

  @Test
  void theShrineIsInertWithoutTheMutation() {
    Leviathan leviathan = upgrade(Leviathan.class);
    profile.getData().getSkillLine("seaborne").setAdaptation(leviathan, 3);
    holdShrine(leviathan);
    player.inventory().setItem(20, new FakeItem(NAUTILUS_SHELL, 2));

    place(0, 39, 2);

    assertThat(platform.world().blockAt(0, 40, 2).type()).isEqualTo(Id.minecraft("water"));
    assertThat(player.inventory().item(20).amount()).isEqualTo(2);
  }

  @Test
  void insideItsShrineTheOwnerSeesAndIsLeftAloneButAStrangerIsNot() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    placedShrine(leviathan);
    FakePlayer stranger = platform.player("Stranger", 0.5D, 41D, 1.5D);
    AdaptPlayer strangerProfile = adapt.join(stranger);
    mutate(strangerProfile);
    strangerProfile.getData().getSkillLine("seaborne").setAdaptation(leviathan, 1);
    player.setSwimming(true);
    stranger.setSwimming(true);
    FakeLiving drowned = platform.world().add(new FakeLiving(Id.minecraft("drowned"), new Loc(platform.world(), 2.5D, 41D, 0.5D)));

    tick(leviathan);
    EntityTargetEvent owner = adapt.post(new EntityTargetEvent(drowned, player, EntityTargetEvent.Reason.CLOSEST_PLAYER));
    EntityTargetEvent other = adapt.post(new EntityTargetEvent(drowned, stranger, EntityTargetEvent.Reason.CLOSEST_PLAYER));
    EntityTargetEvent retaliation = adapt.post(new EntityTargetEvent(drowned, player,
        EntityTargetEvent.Reason.TARGET_ATTACKED_ENTITY));

    assertThat(player.effect(NIGHT_VISION)).isNotNull();
    assertThat(stranger.effect(NIGHT_VISION)).isNull();
    assertThat(owner.isCancelled()).isTrue();
    assertThat(other.isCancelled()).isFalse();
    assertThat(retaliation.isCancelled()).isFalse();
  }

  @Test
  void coralNearAnActiveShrineNeverFadesAndCoralFarAwayDoes() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    placedShrine(leviathan);
    platform.world().set(3, 40, 3, "brain_coral");
    platform.world().set(30, 40, 30, "brain_coral");
    platform.host().tagBlocks(Id.minecraft("corals"), Id.minecraft("brain_coral"));
    adapt.post(new ResourcesReloadedEvent(ResourcesReloadedEvent.Cause.UNKNOWN));

    BlockFadeEvent near = adapt.post(new BlockFadeEvent(platform.world().blockAt(3, 40, 3),
        platform.items().blockState(Id.minecraft("dead_brain_coral"))));
    BlockFadeEvent far = adapt.post(new BlockFadeEvent(platform.world().blockAt(30, 40, 30),
        platform.items().blockState(Id.minecraft("dead_brain_coral"))));

    assertThat(near.isCancelled()).isTrue();
    assertThat(far.isCancelled()).isFalse();
  }

  @Test
  void breakingAShrineDropsNothingAndFreesTheSlot() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    placedShrine(leviathan);
    BlockRef shrine = platform.world().blockAt(0, 40, 2);

    BlockBreakEvent broken = adapt.post(new BlockBreakEvent(player, shrine, true, 0));

    assertThat(broken.dropItems()).isFalse();
    assertThat(leviathan.shrinesOf(player)).isEmpty();
  }

  @Test
  void aShrineThatVanishedWithoutABreakFreesItsSlot() {
    mutate(profile);
    Leviathan leviathan = learn(Leviathan.class, 1);
    placedShrine(leviathan);
    platform.world().set(0, 40, 2, "water");
    player.inventory().setItem(20, new FakeItem(NAUTILUS_SHELL, 1));

    place(1, 39, 2);

    assertThat(platform.world().blockAt(1, 40, 2).type()).isEqualTo(SEA_LANTERN);
    assertThat(leviathan.shrinesOf(player)).hasSize(1);
  }

  @Test
  void aSalvageCastOnLandOrAboveTheWreckPaysNothing() {
    mutate(profile);
    DeepFortune fortune = learn(DeepFortune.class, 2);
    fortune.getConfig().catchChanceAtLevelOne = 0D;
    fortune.getConfig().catchChanceAtMaxLevel = 0D;
    platform.world().structuresAt(new ChunkPos(0, 0), List.of(new GeneratedStructure(Id.minecraft("shipwreck_beached"),
        SHIPWRECK, new Box(0D, 30D, 0D, 12D, 45D, 12D))));
    player.holding(FakeItem.tool("fishing_rod", 64));
    platform.world().set(6, 50, 6, "water");

    reel(4.5D, 41D, 4.5D);
    reel(6.5D, 50D, 6.5D);

    assertThat(items()).isEqualTo(1);
    assertThat(player.inventory().mainHand().damage()).isZero();
  }

  @Test
  void aSalvageCastPaysOncePerWreckForTenRodDurabilityAndNothingAwayFromWrecks() {
    mutate(profile);
    DeepFortune fortune = learn(DeepFortune.class, 2);
    fortune.getConfig().catchChanceAtLevelOne = 0D;
    fortune.getConfig().catchChanceAtMaxLevel = 0D;
    platform.world().structuresAt(new ChunkPos(0, 0), List.of(new GeneratedStructure(Id.minecraft("shipwreck_beached"),
        SHIPWRECK, new Box(0D, 30D, 0D, 12D, 45D, 12D))));
    ItemRef rod = FakeItem.tool("fishing_rod", 64);
    player.holding(rod);
    platform.world().set(4, 41, 4, "water");
    platform.world().set(5, 41, 6, "water");
    platform.world().set(40, 41, 40, "water");

    reel(4.5D, 4.5D);
    int afterFirst = items();
    int damage = player.inventory().mainHand().damage();
    double xp = XpPaid.by(profile, fortune);
    reel(5.5D, 6.5D);
    reel(40.5D, 40.5D);

    assertThat(afterFirst).isEqualTo(2);
    assertThat(damage).isEqualTo(fortune.getConfig().salvageRodDurability);
    assertThat(xp).isPositive();
    assertThat(items()).isEqualTo(afterFirst);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(damage);
    assertThat(XpPaid.by(profile, fortune)).isEqualTo(xp);
  }

  @Test
  void aSalvageCastIsInertWithoutTheMutation() {
    DeepFortune fortune = upgrade(DeepFortune.class);
    profile.getData().getSkillLine("seaborne").setAdaptation(fortune, 3);
    platform.world().structuresAt(new ChunkPos(0, 0), List.of(new GeneratedStructure(Id.minecraft("shipwreck_beached"),
        SHIPWRECK, new Box(0D, 30D, 0D, 12D, 45D, 12D))));
    player.holding(FakeItem.tool("fishing_rod", 64));
    platform.world().set(4, 41, 4, "water");

    reel(4.5D, 4.5D);

    assertThat(items()).isEqualTo(1);
  }

  @Test
  void maelstromSurgesOnlyFromATridentSwingAndPaysNoXp() {
    mutate(profile);
    Maelstrom maelstrom = learn(Maelstrom.class, 1);
    player.setSwimming(true);

    player.holding(FakeItem.tool("diamond_pickaxe", 1561));
    swing();
    assertThat(player.velocity()).isEqualTo(Vec3.ZERO);
    player.holding(FakeItem.tool("diamond_sword", 1561));
    swing();
    assertThat(player.velocity()).isEqualTo(Vec3.ZERO);

    player.holding(FakeItem.tool("trident", 250));
    swing();
    assertThat(player.velocity().lengthSquared()).isGreaterThan(0D);
    assertThat(XpPaid.by(profile, maelstrom)).isZero();
  }

  private void swing() {
    player.setVelocity(Vec3.ZERO);
    adapt.post(new PlayerAnimationEvent(player, PlayerAnimationEvent.Animation.ARM_SWING));
  }

  private void reel(double x, double z) {
    reel(x, 41D, z);
  }

  private void reel(double x, double y, double z) {
    FakeProjectile hook = new FakeProjectile(Id.minecraft("fishing_bobber"), new Loc(platform.world(), x, y, z));
    adapt.post(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.REEL_IN, 0));
  }

  private int items() {
    int count = 0;
    for (int slot = 0; slot < 36; slot++) {
      ItemRef item = player.inventory().item(slot);
      if (item != null && !item.isEmpty()) {
        count++;
      }
    }
    return count + platform.world().items().size();
  }

  private void placedShrine(Leviathan leviathan) {
    holdShrine(leviathan);
    player.inventory().setItem(20, new FakeItem(NAUTILUS_SHELL, 1));
    place(0, 39, 2);
    assertThat(leviathan.shrinesOf(player)).hasSize(1);
  }

  private void holdShrine(Leviathan leviathan) {
    TideShrine tool = (TideShrine) leviathan.getTools().getFirst();
    player.holding(tool.create(new TideShrine.NoData()));
  }

  private void place(int x, int y, int z) {
    BlockRef clicked = platform.world().blockAt(x, y, z);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), clicked, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    platform.scheduler().advance(3);
  }

  private void tick(Leviathan leviathan) {
    leviathan.onTick();
    platform.scheduler().advance(2);
  }

  private void mutate(AdaptPlayer who) {
    who.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("seaborne", "swords")) {
      PlayerSkillLine line = who.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(who.getData(), registry.getSkill("seaborne"), registry.getSkill("swords"), "Tidecaller"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("seaborne").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("seaborne").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
