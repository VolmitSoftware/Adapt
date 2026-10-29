package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimRepository;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimInteractionListenerTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final ClaimRanks RANKS = new ClaimRanks(true, 1, 1, 1, true);

  private ClaimManager manager;
  private ClaimsService service;
  private ClaimInteractionListener listener;
  private Player founder;
  private Player member;
  private Block anchor;
  private MockedStatic<J> scheduler;
  private MockedStatic<Bukkit> bukkit;

  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.BLOCK_DEEPSLATE_BREAK).isNotNull();
    }
  }

  @BeforeEach
  void setUpClaims() throws IOException {
    ClaimsConfig config = new ClaimsConfig();
    manager = new ClaimManager(config, new ClaimRepository(dataFolder.toPath().resolve("claims.json")));
    service = mock(ClaimsService.class);
    when(service.enabled()).thenReturn(true);
    when(service.manager()).thenReturn(manager);
    when(service.config()).thenReturn(config);
    when(service.refreshRanks(any(Player.class))).thenReturn(RANKS);
    listener = new ClaimInteractionListener(service);
    founder = player("Founder");
    member = player("Member");
    World world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    when(world.getName()).thenReturn("world");
    when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
    Location location = new Location(world, 2048, 70, 2048);
    anchor = mock(Block.class);
    when(anchor.getLocation()).thenReturn(location);
    when(anchor.getWorld()).thenReturn(world);
    when(anchor.getType()).thenReturn(Material.LODESTONE);
    when(founder.getLocation()).thenReturn(location);
    when(founder.getTargetBlockExact(6)).thenReturn(anchor);
    scheduler = mockStatic(J.class);
    scheduler.when(() -> J.isOwnedByCurrentRegion(any(Player.class))).thenReturn(true);
    scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class), anyDouble(), anyDouble())).thenReturn(true);
    bukkit = mockStatic(Bukkit.class);
  }

  @AfterEach
  void closeClaims() throws IOException {
    listener.close();
    manager.close();
    bukkit.close();
    scheduler.close();
  }

  @Test
  void anUnselectedShovelKeepsOrdinaryBlockInteraction() {
    when(anchor.getType()).thenReturn(Material.GRASS_BLOCK);
    PlayerInteractEvent event = shovelClick();

    listener.onInteract(event);

    verify(event, never()).setUseInteractedBlock(any(Event.Result.class));
    verify(event, never()).setUseItemInHand(any(Event.Result.class));
    assertThat(manager.snapshot().cities()).isEmpty();
  }

  @Test
  void switchingAwayFromTheShovelClearsTheCitySelection() {
    listener.onInteract(shovelClick());
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(founder.getInventory()).thenReturn(inventory);
    PlayerItemHeldEvent held = mock(PlayerItemHeldEvent.class);
    when(held.getPlayer()).thenReturn(founder);
    when(held.getNewSlot()).thenReturn(1);

    listener.onItemHeld(held);

    when(anchor.getType()).thenReturn(Material.GRASS_BLOCK);
    PlayerInteractEvent ordinary = shovelClick();
    listener.onInteract(ordinary);
    verify(ordinary, never()).setUseInteractedBlock(any(Event.Result.class));
    verify(ordinary, never()).setUseItemInHand(any(Event.Result.class));
    assertThat(manager.snapshot().cityOf(founder.getUniqueId()).orElseThrow().chunks()).hasSize(9);
  }

  @Test
  void shovelActivationCreatesNineChunksAndInvitationRequiresAcceptance() {
    PlayerInteractEvent event = shovelClick();

    listener.onInteract(event);

    assertThat(manager.snapshot().cityOf(founder.getUniqueId())).isPresent();
    assertThat(manager.snapshot().cityOf(founder.getUniqueId()).orElseThrow().chunks()).hasSize(9);
    verify(event).setUseItemInHand(Event.Result.DENY);
    listener.invite(founder, member);
    assertThat(manager.snapshot().cityOf(member.getUniqueId())).isEmpty();

    listener.accept(member);

    assertThat(manager.snapshot().cityOf(member.getUniqueId())).isPresent();
  }

  @Test
  void deniedFoundingUsePermissionCannotClaimLand() {
    when(service.deniedUsePermission(founder, "claims-founding")).thenReturn(Optional.of("adapt.use.claimsfounding"));

    listener.onInteract(shovelClick());

    assertThat(manager.snapshot().cities()).isEmpty();
  }

  @Test
  void deniedExpansionAndCitizenshipCannotChangeTheCity() {
    listener.onInteract(shovelClick());
    when(service.deniedUsePermission(founder, "claims-expansion")).thenReturn(Optional.of("adapt.use.claimsexpansion"));
    when(service.deniedUsePermission(founder, "claims-citizenship")).thenReturn(Optional.of("adapt.use.claimscitizenship"));
    Location target = new Location(anchor.getWorld(), 2080, 70, 2048);
    when(anchor.getLocation()).thenReturn(target);
    when(anchor.getType()).thenReturn(Material.GRASS_BLOCK);

    listener.onInteract(shovelClick());
    listener.invite(founder, member);

    assertThat(manager.snapshot().cityOf(founder.getUniqueId()).orElseThrow().chunks()).hasSize(9);
    assertThat(manager.invitation(member.getUniqueId())).isEmpty();
  }

  @Test
  void deniedHomesteadCannotCreateAPlotInOtherwiseValidCityLand() {
    personalPlot();
    manager.removePlot(member.getUniqueId());
    when(service.deniedUsePermission(member, "claims-homestead")).thenReturn(Optional.of("adapt.use.claimshomestead"));
    Block target = member.getTargetBlockExact(6);
    PlayerInteractEvent event = shovelClick();
    when(event.getPlayer()).thenReturn(member);
    when(event.getClickedBlock()).thenReturn(target);

    listener.onInteract(event);

    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cityOf(founder.getUniqueId()).orElseThrow().chunks()).hasSize(15);
  }

  @Test
  void dismantlingRequiresMatchingExplicitConfirmation() {
    listener.onInteract(shovelClick());

    listener.dismantle(founder, true);
    assertThat(manager.snapshot().cityOf(founder.getUniqueId())).isPresent();
    listener.dismantle(founder, false);
    assertThat(manager.snapshot().cityOf(founder.getUniqueId())).isPresent();
    listener.dismantle(founder, true);

    assertThat(manager.snapshot().cityOf(founder.getUniqueId())).isEmpty();
  }

  @Test
  void oldConfirmationCannotRemoveAReplacementCityAtTheSameAnchor() {
    listener.onInteract(shovelClick());
    listener.dismantle(founder, false);
    ClaimCity previous = manager.snapshot().cityOf(founder.getUniqueId()).orElseThrow();
    manager.removeCity(previous.id());
    ClaimCity replacement = manager.found(founder.getUniqueId(), "Replacement", new ClaimBlock(WORLD, 2048, 70, 2048), RANKS,
        new ClaimWorldAccess("world", true));

    listener.dismantle(founder, true);

    assertThat(manager.snapshot().cities()).containsKey(replacement.id());
  }

  @Test
  void failedDismantleRequestDisarmsThePreviousTarget() {
    listener.onInteract(shovelClick());
    listener.dismantle(founder, false);
    when(founder.getTargetBlockExact(6)).thenReturn(null);

    listener.dismantle(founder, false);
    listener.dismantle(founder, true);

    assertThat(manager.snapshot().cityOf(founder.getUniqueId())).isPresent();
  }

  @Test
  void formerMemberCanConfirmRemovalOfTheirOwnPlotWithoutReleasingTheCity() {
    ClaimPlot plot = personalPlot();
    manager.removeMember(founder.getUniqueId(), member.getUniqueId());

    listener.dismantle(member, false);
    assertThat(manager.snapshot().plots()).containsKey(member.getUniqueId());
    listener.dismantle(member, true);

    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cities()).containsKey(plot.cityId());
    assertThat(manager.snapshot().cityAt(plot.anchor().chunk()).orElseThrow().chunks()).hasSize(15);
    assertThat(manager.canEdit(member.getUniqueId(), plot.anchor())).isFalse();
  }

  @Test
  void personalConfirmationDoesNotRemoveAReplacementPlot() {
    ClaimPlot previous = personalPlot();
    listener.dismantle(member, false);
    manager.removePlot(member.getUniqueId());
    ClaimPlot replacement = manager.createPlot(member.getUniqueId(), previous.anchor(), RANKS);

    listener.dismantle(member, true);

    assertThat(manager.snapshot().plots().get(member.getUniqueId())).isSameAs(replacement);
  }

  @Test
  void administrativeRemovalRequiresConfirmationAndPreservesTheExpectedLand() {
    ClaimPlot plot = personalPlot();
    CommandSender administrator = mock(CommandSender.class);

    listener.remove(administrator, "plot", plot.owner(), false);
    assertThat(manager.snapshot().plots()).containsKey(plot.owner());
    listener.remove(administrator, "plot", plot.owner(), true);
    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cities()).containsKey(plot.cityId());
    manager.createPlot(member.getUniqueId(), plot.anchor(), RANKS);

    listener.remove(administrator, "city", plot.cityId(), false);
    assertThat(manager.snapshot().plots()).containsKey(plot.owner());
    listener.remove(administrator, "city", plot.cityId(), true);
    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cities()).isEmpty();
  }

  @Test
  void boundariesRepeatForFiveSecondsAndReadCurrentOwnership() {
    List<Vector> particles = new ArrayList<>();
    Deque<BoundaryTask> tasks = boundaryTasks(particles);
    listener.boundaries(founder);
    int firstPulse = particles.size();
    assertThat(firstPulse).isPositive();
    assertThat(particles).allSatisfy(point -> assertThat(point.getY()).isEqualTo(71.5));
    assertThat(particles).noneMatch(point -> point.getX() > 2080);

    manager.expand(founder.getUniqueId(), new ClaimChunk(WORLD, 130, 128), new ClaimWorldAccess("world", true));
    tasks.removeFirst().action().run();
    int expandedPulse = particles.size() - firstPulse;
    assertThat(particles).anyMatch(point -> point.getX() == 2096);
    for (int tick = 20; tick <= 100; tick += 10) {
      assertThat(tasks).hasSize(1);
      tasks.removeFirst().action().run();
    }

    assertThat(tasks).isEmpty();
    assertThat(particles).hasSize(firstPulse + expandedPulse * 9);
  }

  @Test
  void aNewBoundaryRequestAndCloseCancelPreviousCallbacks() {
    List<Vector> particles = new ArrayList<>();
    Deque<BoundaryTask> tasks = boundaryTasks(particles);
    listener.boundaries(founder);
    BoundaryTask previous = tasks.removeFirst();
    listener.boundaries(founder);
    int before = particles.size();

    previous.action().run();
    previous.retired().run();
    assertThat(particles).hasSize(before);
    assertThat(tasks).hasSize(1);
    tasks.removeFirst().action().run();
    assertThat(particles.size()).isGreaterThan(before);
    before = particles.size();
    listener.close();
    tasks.removeFirst().action().run();
    listener.boundaries(founder);

    assertThat(particles).hasSize(before);
    assertThat(tasks).isEmpty();
  }

  @Test
  void disablingClaimsStopsTheDisplayUntilANewRequest() {
    List<Vector> particles = new ArrayList<>();
    Deque<BoundaryTask> tasks = boundaryTasks(particles);
    listener.boundaries(founder);
    BoundaryTask pending = tasks.removeFirst();
    int before = particles.size();
    when(service.enabled()).thenReturn(false);
    pending.action().run();
    when(service.enabled()).thenReturn(true);
    pending.action().run();
    assertThat(particles).hasSize(before);
    assertThat(tasks).isEmpty();

    listener.boundaries(founder);
    assertThat(particles.size()).isGreaterThan(before);
    before = particles.size();
    manager.removeCity(manager.snapshot().cityOf(founder.getUniqueId()).orElseThrow().id());
    tasks.removeFirst().action().run();
    assertThat(particles).hasSize(before);
    assertThat(tasks).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"offline", "world", "quit"})
  void boundaryDisplayStopsWhenTheViewerLeaves(String change) {
    List<Vector> particles = new ArrayList<>();
    Deque<BoundaryTask> tasks = boundaryTasks(particles);
    listener.boundaries(founder);
    int before = particles.size();
    switch (change) {
      case "offline" -> when(founder.isOnline()).thenReturn(false);
      case "world" -> {
        World other = mock(World.class);
        when(other.getUID()).thenReturn(UUID.randomUUID());
        when(founder.getWorld()).thenReturn(other);
      }
      case "quit" -> {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(founder);
        listener.onQuit(event);
      }
      default -> throw new IllegalArgumentException(change);
    }
    tasks.removeFirst().action().run();

    assertThat(particles).hasSize(before);
    assertThat(tasks).isEmpty();
  }

  private Deque<BoundaryTask> boundaryTasks(List<Vector> particles) {
    listener.onInteract(shovelClick());
    when(founder.isOnline()).thenReturn(true);
    World world = anchor.getWorld();
    when(founder.getWorld()).thenReturn(world);
    doAnswer(call -> {
      particles.add(new Vector(call.<Double>getArgument(1), call.<Double>getArgument(2), call.<Double>getArgument(3)));
      return null;
    }).when(founder).spawnParticle(eq(Particle.DUST), anyDouble(), anyDouble(), anyDouble(), eq(1),
        anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Particle.DustOptions.class));
    Deque<BoundaryTask> tasks = new ArrayDeque<>();
    scheduler.when(() -> J.runEntity(eq(founder), any(Runnable.class), eq(10), any(Runnable.class))).thenAnswer(call -> {
      tasks.addLast(new BoundaryTask(call.getArgument(1), call.getArgument(3)));
      return true;
    });
    return tasks;
  }

  private ClaimPlot personalPlot() {
    listener.onInteract(shovelClick());
    manager.invite(founder.getUniqueId(), member.getUniqueId(), 1000);
    manager.accept(member.getUniqueId(), founder.getUniqueId(), 1001);
    for (int x = 130; x <= 131; x++) {
      for (int z = 127; z <= 129; z++) {
        manager.expand(founder.getUniqueId(), new ClaimChunk(WORLD, x, z), new ClaimWorldAccess("world", true));
      }
    }
    ClaimBlock position = new ClaimBlock(WORLD, 2080, 70, 2048);
    Location location = new Location(anchor.getWorld(), position.x(), position.y(), position.z());
    Block personalAnchor = mock(Block.class);
    when(personalAnchor.getLocation()).thenReturn(location);
    when(personalAnchor.getType()).thenReturn(Material.LODESTONE);
    when(member.getLocation()).thenReturn(location);
    when(member.getTargetBlockExact(6)).thenReturn(personalAnchor);
    return manager.createPlot(member.getUniqueId(), position, RANKS);
  }

  private PlayerInteractEvent shovelClick() {
    ItemStack shovel = mock(ItemStack.class);
    when(shovel.getType()).thenReturn(Material.IRON_SHOVEL);
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    when(event.getPlayer()).thenReturn(founder);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
    when(event.getClickedBlock()).thenReturn(anchor);
    when(event.getItem()).thenReturn(shovel);
    return event;
  }

  private static Player player(String name) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getName()).thenReturn(name);
    when(player.hasPermission("adapt.claims")).thenReturn(true);
    return player;
  }

  private record BoundaryTask(Runnable action, Runnable retired) {
  }
}
