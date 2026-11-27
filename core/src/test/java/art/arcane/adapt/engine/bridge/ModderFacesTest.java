package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.AdaptSettings;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.item.ShapedRecipeConfig;
import art.arcane.adapt.api.world.BlockOwners;
import art.arcane.adapt.api.world.BlockScan;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The faces a third-party skill uses for block data, scans, its data folder, settings, boosts and recipes. */
class ModderFacesTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private final Object owner = new Object();

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Surveyor", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, new KeeperAddon());
  }

  @AfterEach
  void stop() {
    BlockScan.cancelAll(owner);
    AdaptPorts.uninstall(WorldDataPort.class);
    adapt.close();
  }

  @Test
  void blockOwnersReadAndWriteTheWorldData() {
    AdaptPorts.install(WorldDataPort.class, new OwnerStore());
    BlockRef stand = platform.world().blockAt(4, 61, 4);
    UUID brewer = UUID.randomUUID();

    assertThat(BlockOwners.owner(stand)).isEmpty();
    BlockOwners.setOwner(stand, brewer);
    assertThat(BlockOwners.owner(stand)).contains(brewer);
    BlockOwners.clearOwner(stand);
    assertThat(BlockOwners.owner(stand)).isEmpty();
  }

  @Test
  void aBlockScanDeliversTheNearestMatchOverLaterTicks() {
    platform.world().set(3, 61, 3, "diamond_ore");
    platform.world().set(6, 61, 6, "diamond_ore");
    platform.world().set(2, 61, 2, "stone");
    List<BlockScan.Result> results = new ArrayList<>();

    UUID scan = BlockScan.around(player.location()).radius(10).denseRadius(10).maxSamples(20_000).maxResults(1)
        .matching((Id block) -> block.path().equals("diamond_ore"))
        .onComplete(results::add)
        .build()
        .submit(owner, player.uuid());
    assertThat(results).isEmpty();
    platform.scheduler().advance(120);

    assertThat(results).hasSize(1);
    BlockScan.Result result = results.getFirst();
    assertThat(result.scan()).isEqualTo(scan);
    assertThat(result.matches()).extracting(BlockScan.Match::x, BlockScan.Match::y, BlockScan.Match::z)
        .containsExactly(tuple(3, 61, 3));
  }

  @Test
  void matchesAtTheSameDistanceComeInBlockOrder() {
    platform.world().set(3, 61, 3, "diamond_ore");
    platform.world().set(-3, 61, 3, "diamond_ore");
    platform.world().set(3, 61, -3, "diamond_ore");
    platform.world().set(-3, 61, -3, "diamond_ore");
    List<BlockScan.Result> results = new ArrayList<>();

    BlockScan.around(player.location()).radius(10).denseRadius(10).maxSamples(20_000).maxResults(2)
        .matching((Id block) -> block.path().equals("diamond_ore"))
        .onComplete(results::add)
        .build()
        .submit(owner, player.uuid());
    platform.scheduler().advance(120);

    assertThat(results.getFirst().matches()).extracting(BlockScan.Match::x, BlockScan.Match::z)
        .containsExactly(tuple(-3, -3), tuple(-3, 3));
  }

  @Test
  void aSeededSparseScanReadsTheSameBlocksEachTime() {
    for (int x = -8; x <= 8; x++) {
      for (int z = -8; z <= 8; z++) {
        platform.world().set(x, 61, z, "diamond_ore");
      }
    }
    List<List<BlockScan.Match>> found = new ArrayList<>();
    for (int run = 0; run < 2; run++) {
      List<BlockScan.Result> results = new ArrayList<>();
      BlockScan scan = BlockScan.around(player.location()).radius(8).denseRadius(0).maxSamples(300).maxResults(6)
          .seed(42)
          .matching((Id block) -> block.path().equals("diamond_ore"))
          .onComplete(results::add)
          .build();
      assertThat(scan.seed()).isEqualTo(42);
      scan.submit(owner, player.uuid());
      platform.scheduler().advance(120);
      found.add(results.getFirst().matches());
    }

    assertThat(found.get(0)).isNotEmpty().isEqualTo(found.get(1));
  }

  @Test
  void aCancelledScanNeverCompletes() {
    platform.world().set(3, 61, 3, "diamond_ore");
    List<BlockScan.Result> results = new ArrayList<>();
    BlockScan.around(player.location()).radius(6).denseRadius(6).maxSamples(5_000)
        .matching((Id block) -> block.path().equals("diamond_ore"))
        .onComplete(results::add)
        .build()
        .submit(owner, player.uuid());

    BlockScan.cancel(owner, player.uuid());
    platform.scheduler().advance(120);

    assertThat(results).isEmpty();
  }

  @Test
  void anAddonGetsItsOwnDataFolder() {
    AddonHandle handle = Engine.addons().handle(HostKeys.key(), "keeper").orElseThrow();

    Path folder = handle.dataDirectory();

    assertThat(folder).isEqualTo(data.resolve("addons").resolve("keeper"));
    assertThat(Files.isDirectory(folder)).isTrue();
  }

  @Test
  void settingsReadAdaptToml() {
    AdaptSettings settings = AdaptApi.settings();
    AdaptConfig config = AdaptConfig.get();

    assertThat(settings.hungerEffectPausesHunterAdaptations()).isEqualTo(config.isPreventHunterSkillsWhenHungerApplied());
    assertThat(settings.hardcoreResetOnDeath()).isEqualTo(config.isHardcoreResetOnPlayerDeath());
    assertThat(settings.hardcoreNoRefunds()).isEqualTo(config.isHardcoreNoRefunds());
    assertThat(settings.customModels()).isEqualTo(config.isCustomModels());
    assertThat(settings.menuBackButton()).isEqualTo(config.isGuiBackButton());
    assertThat(settings.escapeClosesAllMenus()).isEqualTo(config.isEscClosesAllGuis());
  }

  @Test
  void aConfiguredRecipeParsesUnderTheAddonsKey() {
    ShapedRecipeConfig config = new ShapedRecipeConfig().rows("RR", "RR").key('R', "REDSTONE").result("REDSTONE_BLOCK", 1);

    RecipeSpec.Shaped recipe = config.toRecipe(Id.of("keeper", "packed_dust")).orElseThrow();

    assertThat(recipe.key()).isEqualTo(Id.of("keeper", "packed_dust"));
    assertThat(recipe.shape()).containsExactly("RR", "RR");
    assertThat(recipe.result().type()).isEqualTo(Id.minecraft("redstone_block"));
  }

  @Test
  void aDisabledOrBrokenConfiguredRecipeRegistersNothing() {
    ShapedRecipeConfig disabled = new ShapedRecipeConfig().rows("R").key('R', "REDSTONE").result("REDSTONE", 1);
    disabled.enabled = false;
    ShapedRecipeConfig custom = new ShapedRecipeConfig().rows("R").key('R', "keeper:dust").result("REDSTONE", 1);

    assertThat(disabled.toRecipe(Id.of("keeper", "dust"))).isEmpty();
    assertThat(custom.toRecipe(Id.of("keeper", "dust"))).isEmpty();
  }

  private static final class KeeperAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "keeper";
    }

    @Override
    public void register(ContentRegistrar registrar) {
    }
  }

  private static final class OwnerStore implements WorldDataPort {
    private final Map<String, UUID> owners = new HashMap<>();

    @Override
    public double reportEarnings(BlockRef block) {
      return 1D;
    }

    @Override
    public double getEarningsMultiplier(BlockRef block) {
      return 1D;
    }

    @Override
    public Placement placement(BlockRef block) {
      return null;
    }

    @Override
    public void setPlacement(BlockRef block, Placement placement) {
    }

    @Override
    public void clearPlacement(BlockRef block) {
    }

    @Override
    public UUID blockOwner(BlockRef block) {
      return owners.get(key(block));
    }

    @Override
    public void setBlockOwner(BlockRef block, UUID owner) {
      if (owner == null) {
        owners.remove(key(block));
      } else {
        owners.put(key(block), owner);
      }
    }

    private static String key(BlockRef block) {
      return block.x() + "," + block.y() + "," + block.z();
    }
  }
}
