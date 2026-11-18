package art.arcane.adapt.bukkit.feature.advancement;

import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitAdvancementHostTest {
  private static final ItemRef ICON = mock(ItemRef.class);
  private static final Id ROOT = Id.adapt("skill_agility");
  private static final Id CHILD = Id.adapt("agility_walk_1k");
  private static final Id COUNTER = Id.adapt("agility_walk_10k");

  private final PlayerRef player = mock(PlayerRef.class);
  private final BukkitAdvancementHost host = new BukkitAdvancementHost(mock(Plugin.class), mock(Server.class), null,
      (AdvancementMain main) -> {
      }, (Player target, Runnable task, int delayTicks) -> true, mock(PlatformLog.class));

  BukkitAdvancementHostTest() {
    when(player.uuid()).thenReturn(UUID.randomUUID());
    host.setCatalog(List.of(tree()));
  }

  @Test
  void catalogFlattensTreesIntoOneTabPerRoot() {
    AdvancementCatalog catalog = AdvancementCatalog.build(List.of(tree()));

    assertThat(catalog.roots()).singleElement().extracting(AdvancementCatalog.Entry::tab).isEqualTo("adapt_skill_agility");
    assertThat(catalog.entries()).extracting((AdvancementCatalog.Entry entry) -> entry.node().key())
        .containsExactly(ROOT, CHILD, COUNTER);
    assertThat(catalog.get(COUNTER).parent()).isEqualTo(CHILD);
    assertThat(catalog.get(COUNTER).root()).isEqualTo(ROOT);
    assertThat(catalog.tree(ROOT)).extracting((AdvancementCatalog.Entry entry) -> entry.node().key()).containsExactly(CHILD, COUNTER);
  }

  @Test
  void catalogRejectsRootsWithoutBackgroundAndDuplicateKeys() {
    AdvancementNode bare = node(ROOT, null, 1, List.of());
    AdvancementNode twice = node(ROOT, Id.minecraft("textures/block/stone.png"), 1, List.of(node(ROOT, null, 1, List.of())));

    assertThatThrownBy(() -> AdvancementCatalog.build(List.of(bare))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdvancementCatalog.build(List.of(twice))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void grantKnowsRegisteredKeysOnly() {
    assertThat(host.isRegistered(CHILD)).isTrue();
    assertThat(host.grant(player, CHILD, true)).isTrue();
    assertThat(host.grant(player, Id.adapt("unknown"), true)).isFalse();
    assertThat(host.progress(player, CHILD)).isEqualTo(1);
  }

  @Test
  void progressClampsAndCompletesAtTheMaximum() {
    host.setProgress(player, COUNTER, 4);
    assertThat(host.progress(player, COUNTER)).isEqualTo(4);

    host.setProgress(player, COUNTER, 99);
    assertThat(host.progress(player, COUNTER)).isEqualTo(10);

    host.setProgress(player, COUNTER, 0);
    assertThat(host.progress(player, COUNTER)).isZero();
  }

  @Test
  void restoreMarksKnownGrantsAndForgetDropsThem() {
    host.restore(player, List.of(ROOT, COUNTER, Id.adapt("removed_since")));

    assertThat(host.progress(player, ROOT)).isEqualTo(1);
    assertThat(host.progress(player, COUNTER)).isEqualTo(10);
    assertThat(host.progress(player, Id.adapt("removed_since"))).isZero();

    host.revoke(player, ROOT);
    assertThat(host.progress(player, ROOT)).isZero();

    host.forget(player);
    assertThat(host.progress(player, COUNTER)).isZero();
  }

  @Test
  void replacingTheCatalogDropsOldKeys() {
    host.setCatalog(List.of(node(Id.adapt("skill_mining"), Id.minecraft("textures/block/stone.png"), 1, List.of())));

    assertThat(host.isRegistered(CHILD)).isFalse();
    assertThat(host.isRegistered(Id.adapt("skill_mining"))).isTrue();
  }

  @Test
  void emptyCatalogKeepsUaapiClosedUntilShutdownDisablesIt() {
    AdvancementMain main = mock(AdvancementMain.class);
    List<AdvancementMain> opened = new ArrayList<>();
    BukkitAdvancementHost owning = new BukkitAdvancementHost(mock(Plugin.class), mock(Server.class), main, opened::add,
        (Player target, Runnable task, int delayTicks) -> true, mock(PlatformLog.class));

    owning.setCatalog(List.of());

    verify(main, never()).disable();
    assertThat(opened).isEmpty();
    assertThat(owning.isRegistered(ROOT)).isFalse();

    owning.shutdown();
    verify(main).disable();
  }

  @Test
  void schedulerMismatchIsFoundInTheCauseChain() {
    assertThat(BukkitAdvancementHost.schedulerContextMismatch(new RuntimeException(new UnsupportedOperationException()))).isTrue();
    assertThat(BukkitAdvancementHost.schedulerContextMismatch(new IllegalStateException("other"))).isFalse();
  }

  @Test
  void userNotLoadedIsFoundInTheCauseChain() {
    RuntimeException wrapped = new RuntimeException(new UserNotLoadedException());

    assertThat(BukkitAdvancementHost.userNotLoaded(wrapped)).isTrue();
    assertThat(BukkitAdvancementHost.userNotLoaded(new IllegalStateException("other"))).isFalse();
  }

  private static AdvancementNode tree() {
    AdvancementNode counter = node(COUNTER, null, 10, List.of());
    AdvancementNode child = node(CHILD, null, 1, List.of(counter));
    return node(ROOT, Id.minecraft("textures/block/stone.png"), 1, List.of(child));
  }

  private static AdvancementNode node(Id key, Id background, int maxProgress, List<AdvancementNode> children) {
    return new AdvancementNode(key, ICON, key.path(), "", AdvancementFrame.TASK, false, false,
        AdvancementVisibility.VANILLA, 0F, 0F, maxProgress, background, children);
  }

  private static final class UserNotLoadedException extends RuntimeException {
  }
}
