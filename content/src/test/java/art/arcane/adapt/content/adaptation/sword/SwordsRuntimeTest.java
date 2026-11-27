package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.AdaptCore;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwordsRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Duelist", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("swords");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("swords").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return zombie;
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }

  @Test
  void swordHitsCountForTheSkill() {
    hit(zombie(), 6D);

    assertThat(profile.getData().getStat("sword.hits")).isEqualTo(1D);
    assertThat(profile.getData().getStat("sword.damage")).isPositive();
  }

  @Test
  void dualWieldRaisesTheHitWithASwordInEachHand() {
    learn(SwordsDualWield.class, 1);
    player.inventory().setOffHand(FakeItem.tool("iron_sword", 250));

    EntityDamageByEntityEvent event = hit(zombie(), 10D);

    assertThat(event.damage()).isGreaterThan(10D);
  }

  @Test
  void bloodyBladeBleedsWithMagicDamageCreditedToTheAttacker() {
    learn(SwordsBloodyBlade.class, 1);
    FakeLiving target = zombie();

    hit(target, 4D);
    platform.scheduler().advance(6);

    assertThat(target.lastDamageType()).isEqualTo(MAGIC);
    assertThat(target.lastDamageSource()).isSameAs(player);
  }

  @Test
  void poisonedBladeBleedsPoisonImmuneTargetsWithoutTheBukkitVisual() {
    learn(SwordsPoisonedBlade.class, 1);
    FakeLiving target = zombie();

    hit(target, 4D);
    platform.scheduler().advance(3);

    assertThat(target.lastDamageType()).isEqualTo(MAGIC);
    assertThat(target.lastDamageSource()).isSameAs(player);
  }

  @Test
  void heirloomEdgeMarksRenamedSwordsAndBanksKillsIntoTheBlade() {
    SwordsHeirloomEdge heirloom = learn(SwordsHeirloomEdge.class, 5);
    FakeItem sword = FakeItem.tool("diamond_sword", 1561);

    PrepareAnvilEvent prepare = adapt.post(new PrepareAnvilEvent(player, InventoryKind.ANVIL,
        List.of(sword.copy()), sword, "Oathkeeper", 1));
    ItemRef result = prepare.result();
    assertThat(result.lore()).hasSize(1);

    player.holding(result);
    for (int kill = 0; kill < heirloom.getConfig().killsPerBank; kill++) {
      adapt.post(new EntityDeathEvent(zombie(), player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
      platform.scheduler().advance(1);
    }

    FakeItem held = (FakeItem) player.inventory().mainHand();
    AttributeMod bonus = held.attributeModifier(Attributes.ATTACK_DAMAGE, Id.adapt("heirloom_edge_damage"));
    assertThat(bonus).isNotNull();
    assertThat(bonus.amount()).isPositive();
    assertThat(held.attributeModifierSlot(Id.adapt("heirloom_edge_damage"))).isEqualTo(EquipmentSlot.MAIN_HAND);
    assertThat(held.lore()).hasSize(1);
    assertThat(profile.getData().getStat("swords.heirloom-edge.banks")).isEqualTo(1D);
  }
}
