package art.arcane.adapt.command;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimRepository;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.claims.ClaimInteractionListener;
import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.service.CommandSVC;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommandClaimsTest extends AdaptTestBase {
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

  @Test
  void ordinaryClaimCommandsPassTheAdministrativeRootGate() {
    Player player = mock(Player.class);
    when(player.hasPermission("adapt.claims")).thenReturn(true);
    ClaimsService service = mock(ClaimsService.class);
    ClaimInteractionListener interactions = mock(ClaimInteractionListener.class);
    when(plugin.getClaimsService()).thenReturn(service);
    when(service.enabled()).thenReturn(true);
    when(service.interactions()).thenReturn(interactions);

    route(player, "claims", "accept");

    verify(interactions).accept(player);
    verify(player, never()).hasPermission("adapt.main");
  }

  @Test
  void memberRemovalResolvesOfflinePlayersThroughTheCommandParser() {
    Player player = mock(Player.class);
    when(player.hasPermission("adapt.claims")).thenReturn(true);
    ClaimsService service = mock(ClaimsService.class);
    ClaimInteractionListener interactions = mock(ClaimInteractionListener.class);
    when(plugin.getClaimsService()).thenReturn(service);
    when(service.enabled()).thenReturn(true);
    when(service.interactions()).thenReturn(interactions);
    UUID id = UUID.randomUUID();
    OfflinePlayer member = mock(OfflinePlayer.class);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getOfflinePlayer(id)).thenReturn(member);

      route(player, "claims", "kick", id.toString());
    }

    verify(interactions).kick(player, member);
  }

  @Test
  void claimAdministrativeCommandsStillRequireTheirOwnPermission() {
    Player player = mock(Player.class);
    ClaimsService service = mock(ClaimsService.class);
    when(plugin.getClaimsService()).thenReturn(service);

    route(player, "claims", "remove", "city", UUID.randomUUID().toString(), "confirm=true");

    verify(player).hasPermission(CommandClaims.REMOVE_PERMISSION);
    verify(player, never()).hasPermission("adapt.main");
    verifyNoInteractions(service);
  }

  @Test
  void ownerDismantlingRequiresThePromptAndKeyedConfirmation() throws IOException {
    UUID owner = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    World world = mock(World.class);
    when(world.getUID()).thenReturn(worldId);
    Location location = new Location(world, 2048, 70, 2048);
    Block anchor = mock(Block.class);
    when(anchor.getLocation()).thenReturn(location);
    when(anchor.getType()).thenReturn(Material.LODESTONE);
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(owner);
    when(player.getLocation()).thenReturn(location);
    when(player.getTargetBlockExact(6)).thenReturn(anchor);
    when(player.hasPermission("adapt.claims")).thenReturn(true);
    ClaimsService service = mock(ClaimsService.class);
    when(plugin.getClaimsService()).thenReturn(service);
    when(service.enabled()).thenReturn(true);
    ClaimsConfig config = new ClaimsConfig();
    try (ClaimManager manager = new ClaimManager(config, new ClaimRepository(dataFolder.toPath().resolve("claims.json")))) {
      when(service.manager()).thenReturn(manager);
      ClaimInteractionListener interactions = new ClaimInteractionListener(service);
      when(service.interactions()).thenReturn(interactions);
      manager.found(owner, "Meadow", new ClaimBlock(worldId, 2048, 70, 2048),
          new ClaimRanks(true, 1, 0, 0, false), new ClaimWorldAccess("world", true));

      route(player, "claims", "dismantle", "confirm=true");
      assertThat(manager.snapshot().cityOf(owner)).isPresent();
      route(player, "claims", "dismantle");
      assertThat(manager.snapshot().cityOf(owner)).isPresent();
      route(player, "claims", "dismantle", "confirm=true");
      assertThat(manager.snapshot().cityOf(owner)).isEmpty();
      interactions.close();
    }
  }

  @Test
  void unrelatedCommandsStillRequireTheAdministrativeRootPermission() {
    Player player = mock(Player.class);

    route(player, "clear", "all");

    verify(player).hasPermission("adapt.main");
  }

  @Test
  void grantsAnAdministrativeAwardWithoutGameplayMultipliers() {
    CommandSender sender = mock(CommandSender.class);
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer runtime = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    PlayerSkillLine line = mock(PlayerSkillLine.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    SkillClaims skill = mock(SkillClaims.class);
    when(sender.hasPermission(CommandClaims.XP_PERMISSION)).thenReturn(true);
    when(plugin.getClaimsService()).thenReturn(mock(ClaimsService.class));
    when(plugin.getAdaptServer()).thenReturn(server);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getName()).thenReturn("Founder");
    when(server.getOnlineAdaptPlayer(id)).thenReturn(runtime);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getAnySkill("claims");
    when(runtime.isRuntimeReady()).thenReturn(true);
    when(runtime.getPlayer()).thenReturn(player);
    when(runtime.getData()).thenReturn(data);
    when(data.getSkillLine("claims")).thenReturn(line);

    try (MockedStatic<BukkitDirectorContext> context = mockStatic(BukkitDirectorContext.class);
         MockedStatic<J> scheduler = mockStatic(J.class);
         MockedStatic<XP> xp = mockStatic(XP.class)) {
      context.when(BukkitDirectorContext::sender).thenReturn(sender);
      scheduler.when(J::isPrimaryThread).thenReturn(true);

      new CommandClaims().xp(400D, player);

      xp.verify(() -> XP.award(eq(runtime), eq(skill), argThat(award -> award.amount() == 400D
          && award.kind() == XpAward.Kind.ADMINISTRATIVE && award.source().equals("claims:administrative"))));
    }
  }

  @Test
  void grantRequiresItsOwnAdministrativePermission() {
    CommandSender sender = mock(CommandSender.class);
    try (MockedStatic<BukkitDirectorContext> context = mockStatic(BukkitDirectorContext.class);
         MockedStatic<XP> xp = mockStatic(XP.class)) {
      context.when(BukkitDirectorContext::sender).thenReturn(sender);

      new CommandClaims().xp(400D, mock(Player.class));

      xp.verifyNoInteractions();
    }
  }

  @Test
  void rejectsNonpositiveAndNonfiniteGrants() {
    assertThat(CommandClaims.validXp(1D)).isTrue();
    assertThat(CommandClaims.validXp(0D)).isFalse();
    assertThat(CommandClaims.validXp(-1D)).isFalse();
    assertThat(CommandClaims.validXp(Double.NaN)).isFalse();
    assertThat(CommandClaims.validXp(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(CommandClaims.validXp(Double.NEGATIVE_INFINITY)).isFalse();
  }

  private void route(Player player, String... arguments) {
    CommandSVC commands = spy(new CommandSVC());
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandAdapt(), DirectorEngineOptions.builder()
        .contexts(new DirectorContextRegistry())
        .dispatcher((DirectorExecutionMode mode, Runnable runnable) -> runnable.run())
        .invocationHook(commands)
        .legacyHandlers(DirectorHelpRenderTest.resolveHandlers(new StringBuilder()))
        .build());
    doReturn(engine).when(commands).getDirector();
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("adapt");
    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<SoundPlayer> sounds = mockStatic(SoundPlayer.class)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(any(Player.class))).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class), anyDouble(), anyDouble())).thenReturn(true);
      sounds.when(() -> SoundPlayer.of(player)).thenReturn(mock(SoundPlayer.class));
      assertThat(commands.onCommand(player, command, "adapt", arguments)).isTrue();
    }
  }
}
