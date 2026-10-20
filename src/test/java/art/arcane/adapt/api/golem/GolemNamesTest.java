package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.insight.EntityInsight;
import art.arcane.adapt.api.insight.GlossInsight;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemNamesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

  private final GlossInsight gloss = mock(GlossInsight.class);
  private final EntityInsight.Display insight = mock(EntityInsight.Display.class);
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private MockedStatic<AdaptHud> hud;
  private GolemRegistry registry;
  private RevealService reveal;
  private Collection<Player> online;
  private GolemNames names;

  @BeforeEach
  void setUp() {
    online = new ArrayList<>();
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> online);
    registry = mock(GolemRegistry.class);
    reveal = mock(RevealService.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    hud = mockStatic(AdaptHud.class);
    names = new GolemNames(gloss);
  }

  @AfterEach
  void tearDown() {
    names.stop();
    EntityInsight.detach(insight);
    hud.close();
    scheduling.close();
    runtime.close();
    bukkit.close();
  }

  @Test
  void glossWithItsOverlayRunningSelectsGlossEvenWhenInsightIsOn() {
    Player probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(true);
    EntityInsight.attach(insight);

    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);
    verify(registry).showNames();
    verify(reveal).refreshViewers();
  }

  @Test
  void glossWithItsOverlayOffFallsToInsightWhenInsightIsEnabled() {
    Player probe = player(OWNER);
    online.add(probe);
    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(false);
    EntityInsight.attach(insight);

    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.INSIGHT);
  }

  @Test
  void withoutGlossOrInsightTheNameStaysVanillaAndNothingIsReapplied() {
    names.start();

    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    verify(registry, never()).showNames();
    verify(gloss, never()).overlayActive(any());
  }

  @Test
  void insightComingAndGoingSwitchesTheModeAndReappliesNames() {
    names.start();

    EntityInsight.attach(insight);
    assertThat(names.mode()).isEqualTo(GolemNameMode.INSIGHT);
    EntityInsight.detach(insight);
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);

    verify(registry, times(2)).showNames();
    verify(reveal, times(2)).refreshViewers();
  }

  @Test
  void withNobodyOnlineGlossCountsAsActiveUntilTheFirstPlayerCanProbeIt() {
    when(gloss.available()).thenReturn(true);
    names.start();
    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);

    Player first = player(OWNER);
    when(gloss.overlayActive(first)).thenReturn(false);
    names.on(new PlayerJoinEvent(first, Component.empty()));
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);

    Player second = player(STRANGER);
    names.on(new PlayerJoinEvent(second, Component.empty()));
    verify(gloss, never()).overlayActive(second);
  }

  @Test
  void glossEnablingAndDisablingReevaluatesTheMode() {
    Player probe = player(OWNER);
    online.add(probe);
    names.start();
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    Plugin glossPlugin = mock(Plugin.class);
    when(glossPlugin.getName()).thenReturn("Gloss");
    Plugin other = mock(Plugin.class);
    when(other.getName()).thenReturn("Other");

    when(gloss.available()).thenReturn(true);
    when(gloss.overlayActive(probe)).thenReturn(true);
    names.on(new PluginEnableEvent(other));
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
    names.on(new PluginEnableEvent(glossPlugin));
    assertThat(names.mode()).isEqualTo(GolemNameMode.GLOSS);
    verify(gloss).refresh();

    names.on(new PluginDisableEvent(glossPlugin));
    assertThat(names.mode()).isEqualTo(GolemNameMode.VANILLA);
  }

  @Test
  void theOwnersNameTagRenamesTheGolemAndIsUsedUp() {
    Player player = player(OWNER);
    GolemHandle handle = boundGolem();
    ItemStack tag = nameTag(player, EquipmentSlot.HAND, "Clank", 3);
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, handle.entity(), EquipmentSlot.HAND);

    names.on(event);

    assertThat(event.isCancelled()).isTrue();
    verify(registry).rename(handle, Component.text("Clank"));
    verify(tag).setAmount(2);
    hud.verify(() -> AdaptHud.actionBar(eq(player), contains("Clank")));
  }

  @Test
  void theLastNameTagInTheOffHandIsRemovedAndCreativeKeepsItsTag() {
    Player player = player(OWNER);
    GolemHandle handle = boundGolem();
    nameTag(player, EquipmentSlot.OFF_HAND, "Clank", 1);

    names.on(new PlayerInteractEntityEvent(player, handle.entity(), EquipmentSlot.OFF_HAND));

    verify(player.getInventory()).setItem(EquipmentSlot.OFF_HAND, null);

    Player creative = player(OWNER);
    when(creative.getGameMode()).thenReturn(GameMode.CREATIVE);
    ItemStack kept = nameTag(creative, EquipmentSlot.HAND, "Bolt", 1);
    names.on(new PlayerInteractEntityEvent(creative, handle.entity(), EquipmentSlot.HAND));
    verify(registry).rename(handle, Component.text("Bolt"));
    verify(kept, never()).setAmount(anyInt());
    verify(creative.getInventory(), never()).setItem(any(EquipmentSlot.class), any());
  }

  @Test
  void aStrangersNameTagIsRefusedAndKept() {
    Player stranger = player(STRANGER);
    GolemHandle handle = boundGolem();
    ItemStack tag = nameTag(stranger, EquipmentSlot.HAND, "Mine Now", 1);
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(stranger, handle.entity(), EquipmentSlot.HAND);

    names.on(event);

    assertThat(event.isCancelled()).isTrue();
    verify(registry, never()).rename(any(), any());
    verify(tag, never()).setAmount(anyInt());
    verify(stranger.getInventory(), never()).setItem(any(EquipmentSlot.class), any());
    hud.verify(() -> AdaptHud.actionBar(eq(stranger), contains("owner")));
  }

  @Test
  void blankTagsOtherItemsAndUnboundMobsAreLeftToVanilla() {
    Player player = player(OWNER);
    GolemHandle handle = boundGolem();
    ItemStack blank = nameTag(player, EquipmentSlot.HAND, null, 1);
    PlayerInteractEntityEvent blankEvent = new PlayerInteractEntityEvent(player, handle.entity(), EquipmentSlot.HAND);
    names.on(blankEvent);
    assertThat(blankEvent.isCancelled()).isFalse();

    when(blank.getType()).thenReturn(Material.IRON_INGOT);
    PlayerInteractEntityEvent ingot = new PlayerInteractEntityEvent(player, handle.entity(), EquipmentSlot.HAND);
    names.on(ingot);
    assertThat(ingot.isCancelled()).isFalse();

    nameTag(player, EquipmentSlot.HAND, "Clank", 1);
    PlayerInteractEntityEvent villager = new PlayerInteractEntityEvent(player, mock(Villager.class), EquipmentSlot.HAND);
    names.on(villager);
    assertThat(villager.isCancelled()).isFalse();
    verify(registry, never()).rename(any(), any());
    hud.verify(() -> AdaptHud.actionBar(any(Player.class), anyString()), never());
  }

  private GolemHandle boundGolem() {
    IronGolem golem = mock(IronGolem.class);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private static Player player(UUID id) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    lenient().when(player.isOnline()).thenReturn(true);
    return player;
  }

  private static ItemStack nameTag(Player player, EquipmentSlot hand, String name, int amount) {
    ItemStack tag = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    lenient().when(tag.getType()).thenReturn(Material.NAME_TAG);
    lenient().when(tag.getAmount()).thenReturn(amount);
    lenient().when(tag.getItemMeta()).thenReturn(meta);
    lenient().when(tag.hasItemMeta()).thenReturn(true);
    lenient().when(meta.hasDisplayName()).thenReturn(name != null);
    lenient().when(meta.displayName()).thenReturn(name == null ? null : Component.text(name));
    PlayerInventory inventory = player.getInventory();
    lenient().when(inventory.getItemInMainHand()).thenReturn(hand == EquipmentSlot.HAND ? tag : mock(ItemStack.class));
    lenient().when(inventory.getItemInOffHand()).thenReturn(hand == EquipmentSlot.OFF_HAND ? tag : mock(ItemStack.class));
    return tag;
  }
}
