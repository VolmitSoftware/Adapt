package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Rift with Swords in both orderings: each of Rift's six blocks through the five mutated adaptations. */
class RiftBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id COBBLESTONE = Id.minecraft("cobblestone");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Voidwalker", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillRift.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftAsLeadCutsGrassAfterAPearlButNotAfterACommandTeleport() {
    kit.mutate("rift", "swords");
    kit.learn("rift", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 1, "short_grass");

    teleport(TeleportCause.COMMAND);
    platform.scheduler().advance(2);
    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();

    teleport(TeleportCause.ENDER_PEARL);
    platform.scheduler().advance(6);

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isTrue();
    assertThat(player.heardSounds()).contains(SoundPalettes.of("rift").work(), SoundPalettes.of("swords").work());
  }

  @Test
  void echoAsPartnerBleedsANearbyHostileAfterABlinkButNotAfterAnotherPluginTeleport() {
    kit.mutate("swords", "rift");
    kit.learn("swords", "rift", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    Loc destination = player.location();

    teleport(TeleportCause.PLUGIN);
    platform.scheduler().advance(40);
    assertThat(zombie.health()).isEqualTo(zombie.maxHealth());

    RiftTeleports.blinking(player, destination);
    teleport(TeleportCause.PLUGIN);
    platform.scheduler().advance(40);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void graftAsPartnerBlinksTheTargetOfASwordCritFourBlocksAway() {
    kit.mutate("swords", "rift");
    kit.learn("swords", "rift", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.location().x()).isCloseTo(6.5D, within(1.0E-9D));
    assertThat(zombie.location().z()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void theBlinkNeverCarriesTheTargetThroughAWall() {
    kit.mutate("swords", "rift");
    kit.learn("swords", "rift", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    for (int y = 61; y <= 63; y++) {
      platform.world().set(4, y, 0, "stone");
    }

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.location().x()).isLessThan(4D);
  }

  @Test
  void aTargetThePlayerCannotSeeIsNeverBlinked() {
    kit.mutate("swords", "rift");
    kit.learn("swords", "rift", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    player.setLineOfSight(false);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.location().x()).isCloseTo(2.5D, within(1.0E-9D));
  }

  @Test
  void convergenceSendsNearbyDropsToTheEnderChestAfterASwordHit() {
    kit.mutate("rift", "swords");
    kit.learn("rift", "swords", Recipe.CONVERGENCE, 1);
    platform.world().dropItem(new Vec3(2.5D, 61D, 2.5D), new FakeItem(COBBLESTONE, 7));

    teleport(TeleportCause.ENDER_PEARL);
    platform.scheduler().advance(2);
    assertThat(player.enderChest().contains(COBBLESTONE, 1)).isFalse();

    FakeLiving far = MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D);
    kit.hit(player, far, 1D, false);
    teleport(TeleportCause.ENDER_PEARL);
    platform.scheduler().advance(2);

    assertThat(player.enderChest().contains(COBBLESTONE, 7)).isTrue();
    assertThat(player.inventory().contains(COBBLESTONE, 1)).isFalse();
    assertThat(platform.world().items()).isEmpty();
  }

  @Test
  void dropsThePlayerCouldNotPickUpStayWhereTheyAre() {
    kit.mutate("rift", "swords");
    kit.learn("rift", "swords", Recipe.CONVERGENCE, 1);
    FakeItemEntity display = spy(new FakeItemEntity(new Loc(platform.world(), 2.5D, 61D, 2.5D),
        new FakeItem(COBBLESTONE, 7)));
    when(display.canPlayerPickup()).thenReturn(false);
    platform.world().add(display);

    kit.hit(player, MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D), 1D, false);
    teleport(TeleportCause.ENDER_PEARL);
    platform.scheduler().advance(2);

    assertThat(player.enderChest().contains(COBBLESTONE, 1)).isFalse();
    assertThat(display.item().amount()).isEqualTo(7);
  }

  @Test
  void barterAsLeadSpendsSwordDurabilityForResistance() {
    kit.mutate("rift", "swords");
    kit.learn("rift", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.effect(RESISTANCE).durationTicks()).isEqualTo(60);
    assertThat(player.effect(RESISTANCE).amplifier()).isZero();
  }

  @Test
  void barterAsPartnerSpendsOneEnderPearlAndRefusesWithoutOne() {
    kit.mutate("swords", "rift");
    kit.learn("swords", "rift", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isFalse();

    player.inventory().addItem(new FakeItem(ENDER_PEARL, 2));
    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.inventory().contains(ENDER_PEARL, 2)).isFalse();
    assertThat(player.inventory().contains(ENDER_PEARL, 1)).isTrue();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementAsLeadHoldsOnlyAfterARecentTeleport() {
    kit.mutate("rift", "swords");
    kit.learn("rift", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1D, within(1.0E-9D));

    teleport(TeleportCause.CHORUS_FRUIT);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    platform.world().set(0, 61, 1, "short_grass");

    teleport(TeleportCause.ENDER_PEARL);
    platform.scheduler().advance(6);

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();
    kit.mutate("rift", "swords");
    teleport(TeleportCause.ENDER_PEARL);
    platform.scheduler().advance(6);
    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();
  }

  private void teleport(TeleportCause cause) {
    adapt.post(new PlayerTeleportEvent(player, player.location(), player.location(), cause));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
