package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.discovery.upgraded.Omniscience;
import art.arcane.adapt.content.adaptation.rift.upgraded.PocketRealm;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.GeneratedStructure;
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

/** Discovery and Rift, either leading, replace Convergence with Waystones. */
class WaystonesTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;
  private Omniscience omniscience;
  private PocketRealm realm;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Seeker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class, SkillRift.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    omniscience = upgrade("discovery", Omniscience.class);
    realm = upgrade("rift", PocketRealm.class);
    platform.world().structuresAt(new ChunkPos(0, 0), List.of(
        new GeneratedStructure(Id.minecraft("village_plains"), Id.minecraft("jigsaw"), new Box(-8D, 50D, -8D, 8D, 80D, 8D)),
        new GeneratedStructure(Id.minecraft("desert_pyramid"), Id.minecraft("jigsaw"), new Box(9D, 50D, -8D, 15D, 80D, 8D))));
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aFoundStructureBecomesAWaystoneAndCanBeRemovedWithEitherLead() {
    prepare("discovery", "rift", 1);
    Omniscience.Structure village = found().getFirst();
    Waystones waystones = (Waystones) kit.adaptation("discovery", "rift", Recipe.CONVERGENCE).getSignature();
    assertThat(kit.mutated("rift", "discovery").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isInstanceOf(Waystones.class);

    assertThat(waystones.label(player, village)).isEqualTo(DiscoveryMessages.ATLAS_MAKE_WAYSTONE);
    waystones.run(player, village);

    assertThat(realm.waystone(player, village.id())).isNotNull();
    assertThat(realm.waystone(player, village.id()).name()).isEqualTo(village.name());
    assertThat(waystones.label(player, village)).isEqualTo(DiscoveryMessages.ATLAS_REMOVE_WAYSTONE);
    waystones.run(player, village);
    assertThat(realm.waystone(player, village.id())).isNull();
  }

  @Test
  void theLevelCapsHowManyWaystonesStand() {
    prepare("rift", "discovery", 1);
    Waystones waystones = (Waystones) kit.adaptation("rift", "discovery", Recipe.CONVERGENCE).getSignature();
    List<Omniscience.Structure> found = found();
    assertThat(found).hasSize(2);

    waystones.run(player, found.get(0));
    waystones.run(player, found.get(1));

    assertThat(realm.waystone(player, found.get(0).id())).isNotNull();
    assertThat(realm.waystone(player, found.get(1).id())).isNull();
    assertThat(waystones.allowed(3)).isGreaterThan(waystones.allowed(2)).isGreaterThan(waystones.allowed(1));
  }

  @Test
  void nothingIsOfferedWithoutTheMutationOrPocketRealm() {
    kit.mutate("discovery", "rift");
    profile.getData().getSkillLine("discovery").setAdaptation(omniscience, 1);
    loot();
    Waystones waystones = (Waystones) kit.adaptation("discovery", "rift", Recipe.CONVERGENCE).getSignature();
    Omniscience.Structure village = found().getFirst();
    assertThat(waystones.label(player, village)).as("not learned").isNull();

    kit.learn("discovery", "rift", Recipe.CONVERGENCE, 3);
    assertThat(waystones.label(player, village)).as("no Pocket Realm").isNull();
    waystones.run(player, village);

    assertThat(realm.anchors(player)).isEmpty();
  }

  private void prepare(String lead, String partner, int level) {
    kit.mutate(lead, partner);
    profile.getData().getSkillLine("discovery").setAdaptation(omniscience, 1);
    profile.getData().getSkillLine("rift").setAdaptation(realm, 3);
    kit.learn(lead, partner, Recipe.CONVERGENCE, level);
    loot();
  }

  private void loot() {
    adapt.post(new PlayerLootGenerateEvent(player, new Loc(platform.world(), 0D, 62D, 0D)));
    adapt.post(new PlayerLootGenerateEvent(player, new Loc(platform.world(), 12D, 62D, 0D)));
  }

  private List<Omniscience.Structure> found() {
    return omniscience.structures(player);
  }

  private <A extends Adaptation<?>> A upgrade(String skill, Class<A> type) {
    return kit.registry().getSkill(skill).getUpgrades().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
  }
}
