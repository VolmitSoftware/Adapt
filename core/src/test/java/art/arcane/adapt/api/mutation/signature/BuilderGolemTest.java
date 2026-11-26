package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Golemancy and Architect, either leading: a golem given a template builds the previewed stamp from a Deposit Node. */
class BuilderGolemTest {
  private static final Id STONE = Id.minecraft("stone");

  @TempDir
  Path data;

  private SignatureBench bench;
  private MockedStatic<GolemancyRuntime> runtime;
  private MobRef body;
  private GolemHandle golem;
  private InventoryRef node;
  private NodeIndex index;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillGolemancy.class, SkillArchitect.class));
    bench.world.set(10, 61, 10, "stone").set(11, 61, 10, "stone").set(12, 61, 10, "stone");
    bench.world.set(6, 61, 6, "chest");
    node = bench.world.blockAt(6, 61, 6).container();
    node.addItem(new FakeItem(STONE, 10));
    bench.player.inventory().setItem(20, new FakeItem(STONE, 10));
    body = mock(MobRef.class);
    when(body.uuid()).thenReturn(UUID.randomUUID());
    when(body.isValid()).thenReturn(true);
    when(body.type()).thenReturn(Id.minecraft("iron_golem"));
    when(body.location()).thenReturn(new Loc(bench.world, 4.5D, 61D, 4.5D));
    golem = mock(GolemHandle.class);
    when(golem.owner()).thenReturn(bench.player.uuid());
    when(golem.entity()).thenReturn(body);
    index = new NodeIndex();
    NodeKey key = new NodeKey(bench.world.uid(), 6, 61, 6, BlockFace.UP);
    long generation = index.beginLoad(bench.world.uid(), key.chunkKey());
    index.completeLoad(bench.world.uid(), key.chunkKey(), generation,
        Map.of(key, NodeRecord.builder(bench.player.uuid(), NodeType.DEPOSIT, BlockFace.UP).build()));
    NodeService service = mock(NodeService.class);
    when(service.index()).thenReturn(index);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(() -> GolemancyRuntime.handle(any(EntityRef.class))).thenReturn(Optional.of(golem));
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
  }

  @AfterEach
  void stop() {
    runtime.close();
    bench.close();
  }

  @Test
  void theGolemBuildsThePreviewedStampFromItsDepositNode() {
    bench.signature("golemancy", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);

    order();
    bench.advance(200);

    assertThat(bench.placed(STONE, row())).isEqualTo(3);
    assertThat(node.containsAtLeast(new FakeItem(STONE, 1), 8)).isFalse();
    assertThat(node.containsAtLeast(new FakeItem(STONE, 1), 7)).isTrue();
    assertThat(bench.count(STONE)).isEqualTo(10);
  }

  @Test
  void eitherSkillMayLead() {
    bench.signature("architect", "golemancy", 1);
    bench.upgrade("architect", Skyhook.class, 1);

    order();
    bench.advance(200);

    assertThat(bench.placed(STONE, row())).isEqualTo(3);
  }

  @Test
  void nothingIsBuiltWithoutTheLearnedSignature() {
    bench.kit.mutate("golemancy", "architect");
    bench.upgrade("architect", Skyhook.class, 1);

    order();
    bench.advance(200);

    assertThat(bench.placed(STONE, row())).isZero();
  }

  @Test
  void theGolemStopsAtTheLevelsBlocksOrWhenYouLeaveAndTheCooldownHolds() {
    bench.signature("golemancy", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);
    BuilderGolem builder = bench.handler("golemancy", "architect", BuilderGolem.class);
    builder.getConfig().blocksAtFirstLevel = 2;

    order();
    bench.advance(200);
    int capped = bench.placed(STONE, row());
    for (BlockRef block : row()) {
      block.setType(Id.minecraft("air"), false);
    }
    bench.player.inventory().setItem(20, FakeItem.empty());
    order();
    bench.advance(200);
    int duringCooldown = bench.placed(STONE, row());

    assertThat(capped).isEqualTo(2);
    assertThat(duringCooldown).isZero();
    assertThat(builder.perSecond(3)).isEqualTo(4D);
  }

  @Test
  void leavingStopsTheJob() {
    bench.signature("golemancy", "architect", 3);
    bench.upgrade("architect", Skyhook.class, 1);

    order();
    bench.advance(6);
    bench.player.moveTo(new Loc(bench.world, 200.5D, 61D, 200.5D));
    bench.advance(200);

    assertThat(bench.placed(STONE, row())).isLessThan(3);
  }

  @Test
  void aGolemWithAnEmptyNodeNeverPaysFromYourInventory() {
    bench.signature("golemancy", "architect", 3);
    bench.upgrade("architect", Skyhook.class, 1);
    for (int slot = 0; slot < node.size(); slot++) {
      node.clear(slot);
    }

    order();
    bench.advance(200);

    assertThat(bench.placed(STONE, row())).isZero();
    assertThat(bench.count(STONE)).isEqualTo(10);
  }

  @Test
  void aNodeBrokenMidJobPaysForNothingMore() {
    bench.signature("golemancy", "architect", 1);
    bench.upgrade("architect", Skyhook.class, 1);

    order();
    bench.advance(25);
    int beforeBreak = bench.placed(STONE, row());
    bench.world.set(6, 61, 6, "air");
    bench.advance(200);

    assertThat(beforeBreak).isEqualTo(1);
    assertThat(bench.placed(STONE, row())).isEqualTo(1);
    assertThat(node.containsAtLeast(new FakeItem(STONE, 1), 9)).isTrue();
  }

  @Test
  void someoneElsesGolemTakesNoOrder() {
    bench.signature("golemancy", "architect", 3);
    bench.upgrade("architect", Skyhook.class, 1);
    when(golem.owner()).thenReturn(UUID.randomUUID());

    order();
    bench.advance(200);

    assertThat(bench.placed(STONE, row())).isZero();
  }

  private void order() {
    MasonsTemplate.Data data = new MasonsTemplate.Data();
    data.pattern = ArchitectPattern.copy(bench.world, 10, 61, 10, 12, 61, 10);
    bench.player.holding(MasonsTemplate.find().create(data));
    bench.adapt.post(new PlayerInteractEvent(bench.player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        bench.player.inventory().mainHand(), bench.world.blockAt(0, 60, 8), BlockFace.UP, null, UseResult.DEFAULT,
        UseResult.DEFAULT));
    bench.adapt.post(new PlayerInteractEntityEvent(bench.player, body, Hand.MAIN_HAND, new Vec3(0D, 1D, 0D)));
  }

  private List<BlockRef> row() {
    return List.of(bench.world.blockAt(-1, 61, 8), bench.world.blockAt(0, 61, 8), bench.world.blockAt(1, 61, 8));
  }
}
