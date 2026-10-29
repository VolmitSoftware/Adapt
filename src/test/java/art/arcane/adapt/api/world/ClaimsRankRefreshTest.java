package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.claims.ClaimAction;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.content.adaptation.claims.ClaimsFortification;
import art.arcane.adapt.content.skill.SkillClaims;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ClaimsRankRefreshTest extends AdaptTestBase {
  private final UUID founder = UUID.randomUUID();
  private Player player;
  private PlayerData data;
  private PlayerSkillLine line;
  private List<Adaptation<?>> adaptations;
  private AdaptServer server;
  private ClaimsService service;
  private MockedStatic<AdaptAttributeService> attributes;

  @BeforeEach
  void prepareRuntime() throws IOException {
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(founder);
    data = new PlayerData();
    line = new PlayerSkillLine();
    line.setLine("claims");
    data.getSkillLines().put("claims", line);
    SkillClaims skill = new SkillClaims();
    adaptations = List.copyOf(skill.getAdaptations());
    line.setAdaptation(adaptations.get(0), 1);
    line.setAdaptation(adaptations.get(1), 1);

    server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getAnySkill("claims");
    doReturn(skill).when(registry).getSkill("claims");
    when(registry.getSkills()).thenReturn(List.of(skill));
    AdaptPlayer owner = mock(AdaptPlayer.class);
    when(owner.getPlayer()).thenReturn(player);
    when(owner.getServer()).thenReturn(server);
    when(owner.getData()).thenReturn(data);
    when(owner.isRuntimeReady()).thenReturn(true);
    when(server.getOnlineAdaptPlayer(founder)).thenReturn(owner);
    data.bindRuntimeOwner(owner);
    service = new ClaimsService(plugin);
    when(plugin.getClaimsService()).thenReturn(service);
    attributes = mockStatic(AdaptAttributeService.class);
    AdaptAttributeService attributeService = mock(AdaptAttributeService.class);
    attributes.when(AdaptAttributeService::get).thenReturn(attributeService);
  }

  @AfterEach
  void closeRuntime() throws IOException {
    if (attributes != null) {
      attributes.close();
    }
    if (service != null) {
      service.close();
    }
  }

  @Test
  void liveUnlocksAndResetsImmediatelyUpdateExistingLand() {
    ClaimsConfig config = service.config();
    ClaimCity city = found();

    line.setAdaptation(adaptations.get(1), 4);
    ClaimRanks ranks = service.manager().snapshot().cities().get(city.id()).ranks();
    assertThat(ranks.fortification()).isEqualTo(4);
    assertThat(config.resistance(ranks, true, ClaimAction.BREAK)).isEqualTo(64);

    line.setAdaptation(adaptations.get(2), 4);
    ranks = service.manager().snapshot().cities().get(city.id()).ranks();
    assertThat(config.memberCapacity(ranks)).isEqualTo(32);
    line.setAdaptation(adaptations.get(3), 4);
    ranks = service.manager().snapshot().cities().get(city.id()).ranks();
    assertThat(config.chunkCapacity(ranks)).isEqualTo(121);
    line.setAdaptation(adaptations.get(4), 1);
    assertThat(service.manager().snapshot().cities().get(city.id()).ranks().homestead()).isTrue();

    data.clearAdaptations();
    ClaimCity retained = service.manager().snapshot().cities().get(city.id());
    assertThat(retained.chunks()).isEqualTo(city.chunks());
    assertThat(retained.ranks()).isEqualTo(ClaimRanks.none());
    assertThat(config.resistance(retained.ranks(), true, ClaimAction.BREAK)).isEqualTo(16);
  }

  @Test
  void temporaryGrantsNeverCountAsPurchasedRanks() {
    ClaimCity city = found();
    for (Adaptation<?> adaptation : adaptations) {
      line.setAdaptation(adaptation, 1);
      line.getAdaptation(adaptation.getName()).setRegionGranted(true);
    }

    assertThat(service.ranks(player)).isEqualTo(ClaimRanks.none());
    service.refreshRanks(player);

    assertThat(service.manager().snapshot().cities().get(city.id()).ranks()).isEqualTo(ClaimRanks.none());
    assertThat(service.manager().snapshot().cities().get(city.id()).chunks()).isEqualTo(city.chunks());
  }

  @ParameterizedTest
  @CsvSource({"claims-founding,adapt.use.claims", "claims-founding,adapt.use.claimsfounding",
      "claims-expansion,adapt.use.claimsexpansion", "claims-citizenship,adapt.use.claimscitizenship",
      "claims-homestead,adapt.use.claimshomestead"})
  void actionUsePermissionsFollowTheExistingExplicitDenialAndOperatorConvention(String adaptation, String permission) {
    assertThat(service.deniedUsePermission(player, adaptation)).isEmpty();
    when(player.isPermissionSet(permission)).thenReturn(true);

    assertThat(service.deniedUsePermission(player, adaptation)).contains(permission);

    when(player.isOp()).thenReturn(true);
    assertThat(service.deniedUsePermission(player, adaptation)).isEmpty();
  }

  @Test
  void trimmingPowerRefreshesTheFounderBeforeAnyFurtherInteraction() {
    ClaimCity city = found();
    line.setAdaptation(adaptations.get(1), 4);
    data.setRegionPowerBonus(4 - data.getMaxPower());

    data.pruneAdaptationsForPowerBudget();

    assertThat(line.getAdaptationLevel("claims-fortification")).isEqualTo(3);
    ClaimCity retained = service.manager().snapshot().cities().get(city.id());
    assertThat(retained.ranks().fortification()).isEqualTo(3);
    assertThat(retained.chunks()).isEqualTo(city.chunks());
  }

  @Test
  void configurationCapsApplyWhileTheFounderIsOfflineAndRestoreWithoutLosingRanks() {
    ClaimCity city = found();
    line.setAdaptation(adaptations.get(1), 4);
    when(server.getOnlineAdaptPlayer(founder)).thenReturn(null);
    ClaimsFortification fortification = (ClaimsFortification) adaptations.get(1);
    fortification.setMaxLevel(2);

    service.refreshRankLimits();

    assertThat(service.manager().effectiveRanks(service.manager().snapshot().cities().get(city.id()).ranks()).fortification()).isEqualTo(2);
    assertThat(service.manager().snapshot().cities().get(city.id()).ranks().fortification()).isEqualTo(4);
    for (Adaptation<?> adaptation : adaptations) {
      ((AdaptationConfig) adaptation.getConfig()).enabled = false;
    }
    service.refreshRankLimits();
    assertThat(service.manager().effectiveRanks(service.manager().snapshot().cities().get(city.id()).ranks())).isEqualTo(ClaimRanks.none());
    assertThat(service.manager().snapshot().cities().get(city.id()).chunks()).isEqualTo(city.chunks());

    fortification.getConfig().enabled = true;
    fortification.setMaxLevel(4);
    service.refreshRankLimits();
    assertThat(service.manager().effectiveRanks(service.manager().snapshot().cities().get(city.id()).ranks()).fortification()).isEqualTo(4);
  }

  private ClaimCity found() {
    return service.manager().found(founder, "Meadow", new ClaimBlock(UUID.randomUUID(), 2008, 64, 2008),
        service.ranks(player), new ClaimWorldAccess("world", true));
  }
}
