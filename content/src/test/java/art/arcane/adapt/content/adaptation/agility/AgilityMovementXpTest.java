package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Vault and Cat Reflexes are movement: using them pays no skill XP. */
class AgilityMovementXpTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().tagBlocks(Id.minecraft("fences"), Id.minecraft("oak_fence"));
    player = platform.player("Runner", 0.5D, 61D, 0.5D);
    player.setOnGround(true);
    adapt = TestAdapt.start(platform, List.of(SkillAgility.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aVaultPaysNoXp() {
    AgilityVault vault = (AgilityVault) registry.getAdaptation(Id.adapt("agility/vault"));
    profile.getData().getSkillLine("agility").setAdaptation(vault, 1);
    platform.world().set(1, 61, 0, "oak_fence");
    adapt.post(new ResourcesReloadedEvent(ResourcesReloadedEvent.Cause.UNKNOWN));

    adapt.post(new PlayerJumpEvent(player, new Loc(platform.world(), 0.5D, 61D, 0.5D),
        new Loc(platform.world(), 0.9D, 61D, 0.5D)));
    platform.scheduler().advance(2);

    assertThat(profile.getData().getStat("agility.vault.vaults")).isEqualTo(1D);
    assertThat(XpPaid.by(profile, vault)).isZero();
  }

  @Test
  void aDodgedProjectilePaysNoXp() {
    AgilityCatReflexes reflexes = (AgilityCatReflexes) registry.getAdaptation(Id.adapt("agility/cat_reflexes"));
    reflexes.getConfig().dodgeChanceBase = 1D;
    reflexes.getConfig().maxDodgeChance = 1D;
    profile.getData().getSkillLine("agility").setAdaptation(reflexes, 1);
    player.setSprinting(true);
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 4.5D, 62D, 0.5D));

    EntityDamageByEntityEvent hit = adapt.post(new EntityDamageByEntityEvent(player, arrow, null, DamageCause.PROJECTILE,
        Id.minecraft("arrow"), 4D, false, FinalDamage.IDENTITY));

    assertThat(hit.isCancelled()).isTrue();
    assertThat(profile.getData().getStat("agility.cat-reflexes.dodges")).isEqualTo(1D);
    assertThat(XpPaid.by(profile, reflexes)).isZero();
  }
}
