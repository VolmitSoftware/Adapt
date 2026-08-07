package art.arcane.adapt.api.protection;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class RegionGrantRuntimeTest extends AdaptTestBase {
  private static final AtomicLong CATALOG_REVISION = new AtomicLong(1);
  private static final String SKILL = "axes";
  private static final String GRANTED = "axe-shield-splitter";
  private static final String OTHER = "axe-heavy-hands";

  private FakeRegionPolicySource source;
  private AdaptPlayer adaptPlayer;
  private PlayerData data;
  private Location location;

  @BeforeEach
  void installRuntime() {
    Skill<?> skill = mock(Skill.class);
    Adaptation<?> granted = adaptation(GRANTED, skill);
    Adaptation<?> other = adaptation(OTHER, skill);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(granted);
    adaptations.add(other);
    lenient().when(skill.getName()).thenReturn(SKILL);
    lenient().when(skill.isEnabled()).thenReturn(true);
    lenient().when(skill.getAdaptations()).thenReturn(adaptations);

    SkillRegistry registry = mock(SkillRegistry.class);
    lenient().when(registry.getCatalogRevision()).thenReturn(CATALOG_REVISION.incrementAndGet());
    lenient().when(registry.getSkills()).thenReturn(List.of(skill));
    lenient().when(registry.getSkill(SKILL)).thenAnswer(invocation -> skill);

    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getSkillRegistry()).thenReturn(registry);
    lenient().when(plugin.getAdaptServer()).thenReturn(server);

    data = new PlayerData();
    adaptPlayer = mock(AdaptPlayer.class);
    lenient().when(adaptPlayer.getData()).thenReturn(data);
    lenient().when(adaptPlayer.getPlayer()).thenReturn(mock(Player.class));

    location = new Location(null, 0, 64, 0);
    source = new FakeRegionPolicySource();
    RegionPolicyService.install(source);
  }

  @AfterEach
  void clearRuntime() {
    RegionPolicyService.clear();
  }

  @Test
  @DisplayName("a named region unlock grants the adaptation at level one")
  void namedUnlockGrantsLevelOne() {
    source.setPolicy(new RegionPolicy(true, 1D, 0, Set.of(GRANTED)));

    RegionGrantRuntime.refresh(adaptPlayer, location);

    PlayerAdaptation adaptation = adaptationOf(GRANTED);
    assertThat(adaptation).isNotNull();
    assertThat(adaptation.getLevel()).isEqualTo(1);
    assertThat(adaptation.isRegionGranted()).isTrue();
    assertThat(data.getRegionGrantedCount()).isEqualTo(1);
    assertThat(adaptationOf(OTHER)).isNull();
  }

  @Test
  @DisplayName("repeated ticks inside the region leave the grant untouched")
  void repeatedTicksAreIdempotent() {
    source.setPolicy(new RegionPolicy(true, 1D, 0, Set.of(GRANTED)));

    RegionGrantRuntime.refresh(adaptPlayer, location);
    RegionGrantRuntime.refresh(adaptPlayer, location);
    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(adaptationOf(GRANTED).getLevel()).isEqualTo(1);
    assertThat(data.getRegionGrantedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("leaving the region revokes the grant on the next tick")
  void leavingTheRegionRevokesTheGrant() {
    source.setPolicy(new RegionPolicy(true, 1D, 0, Set.of(GRANTED)));
    RegionGrantRuntime.refresh(adaptPlayer, location);

    source.setPolicy(RegionPolicy.DEFAULT);
    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(adaptationOf(GRANTED)).isNull();
    assertThat(data.getRegionGrantedCount()).isZero();
  }

  @Test
  @DisplayName("the wildcard unlock grants every registered adaptation")
  void wildcardGrantsEveryAdaptation() {
    source.setPolicy(new RegionPolicy(true, 1D, 0, Set.of(RegionPolicy.UNLOCK_ALL)));

    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(adaptationOf(GRANTED).isRegionGranted()).isTrue();
    assertThat(adaptationOf(OTHER).isRegionGranted()).isTrue();
    assertThat(data.getRegionGrantedCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("a learned adaptation is never marked as region granted and is never revoked")
  void learnedAdaptationsAreLeftAlone() {
    PlayerSkillLine line = data.getSkillLine(SKILL);
    PlayerAdaptation learned = new PlayerAdaptation();
    learned.setId(GRANTED);
    learned.setLevel(3);
    line.getAdaptations().put(GRANTED, learned);

    source.setPolicy(new RegionPolicy(true, 1D, 0, Set.of(GRANTED)));
    RegionGrantRuntime.refresh(adaptPlayer, location);
    source.setPolicy(RegionPolicy.DEFAULT);
    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(adaptationOf(GRANTED).getLevel()).isEqualTo(3);
    assertThat(adaptationOf(GRANTED).isRegionGranted()).isFalse();
    assertThat(data.getRegionGrantedCount()).isZero();
  }

  @Test
  @DisplayName("region granted adaptations never consume used power")
  void regionGrantsAreFreeOfPowerCost() {
    source.setPolicy(new RegionPolicy(true, 1D, 0, Set.of(GRANTED)));
    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(data.getUsedPower()).isZero();

    PlayerAdaptation learned = new PlayerAdaptation();
    learned.setId(OTHER);
    learned.setLevel(2);
    data.getSkillLine(SKILL).getAdaptations().put(OTHER, learned);

    assertThat(data.getUsedPower()).isEqualTo(2);
  }

  @Test
  @DisplayName("the region power bonus raises max power and never drives it below zero")
  void regionPowerBonusAdjustsMaxPower() {
    int base = data.getMaxPower();

    source.setPolicy(new RegionPolicy(true, 1D, 5, Set.of()));
    RegionGrantRuntime.refresh(adaptPlayer, location);
    assertThat(data.getMaxPower()).isEqualTo(base + 5);

    source.setPolicy(new RegionPolicy(true, 1D, -1000, Set.of()));
    RegionGrantRuntime.refresh(adaptPlayer, location);
    assertThat(data.getMaxPower()).isZero();

    source.setPolicy(RegionPolicy.DEFAULT);
    RegionGrantRuntime.refresh(adaptPlayer, location);
    assertThat(data.getMaxPower()).isEqualTo(base);
  }

  @Test
  @DisplayName("dropping the region power bonus prunes adaptations that no longer fit the budget")
  void leavingTheRegionPrunesOverBudgetAdaptations() {
    source.setPolicy(new RegionPolicy(true, 1D, 5, Set.of()));
    RegionGrantRuntime.refresh(adaptPlayer, location);

    PlayerAdaptation learned = new PlayerAdaptation();
    learned.setId(OTHER);
    learned.setLevel(2);
    data.getSkillLine(SKILL).getAdaptations().put(OTHER, learned);

    source.setPolicy(new RegionPolicy(true, 1D, 3, Set.of()));
    RegionGrantRuntime.refresh(adaptPlayer, location);
    assertThat(adaptationOf(OTHER).getLevel()).isEqualTo(2);

    source.setPolicy(RegionPolicy.DEFAULT);
    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(adaptationOf(OTHER)).isNull();
    assertThat(data.getUsedPower()).isZero();
  }

  @Test
  @DisplayName("a quarantined policy source revokes outstanding grants instead of leaking them")
  void quarantinedSourceRevokesGrants() {
    source.setPolicy(new RegionPolicy(true, 1D, 6, Set.of(GRANTED)));
    RegionGrantRuntime.refresh(adaptPlayer, location);
    assertThat(data.getRegionGrantedCount()).isEqualTo(1);

    source.setFailure(new IllegalStateException("worldguard exploded"));
    RegionGrantRuntime.refresh(adaptPlayer, location);

    assertThat(adaptationOf(GRANTED)).isNull();
    assertThat(data.getRegionGrantedCount()).isZero();
    assertThat(data.getRegionPowerBonus()).isZero();
  }

  private PlayerAdaptation adaptationOf(String id) {
    PlayerSkillLine line = data.getSkillLineNullable(SKILL);
    return line == null ? null : line.getAdaptation(id);
  }

  private static Adaptation<?> adaptation(String name, Skill<?> skill) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getName()).thenReturn(name);
    lenient().when(adaptation.getMaxLevel()).thenReturn(3);
    lenient().when(adaptation.isEnabled()).thenReturn(true);
    lenient().when(adaptation.getSkill()).thenAnswer(invocation -> skill);
    return adaptation;
  }
}
