package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

public class RiftVisage extends SimpleAdaptation<RiftVisage.Config> {
  public static final Id ENDERMAN = Id.minecraft("enderman");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private final RiftEndermanCalm calm = new RiftEndermanCalm(this, cooldowns(),
      (PlayerRef player) -> addStat(player, "rift.visage.stares-survived", 1));

  public RiftVisage() {
    super("rift-visage");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("popped_chorus_fruit"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_eye"))
        .key("challenge_rift_visage_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_head"))
            .key("challenge_rift_visage_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_visage_100", "rift.visage.stares-survived", 100, 300);
    registerMilestone("challenge_rift_visage_1k", "rift.visage.stares-survived", 1000, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.VISAGE_LORE1));
  }

  @Handle
  public void onEntityTarget(EntityTargetEvent event) {
    PlayerRef player = RiftEndermanCalm.endermanTarget(event);
    if (player == null || !hasActiveAdaptation(player) || !hasEnderPearl(player)) {
      return;
    }

    calm.calm(event, player);
  }

  @Override
  public void onTick() {
    calm.prune();
  }

  private boolean hasEnderPearl(PlayerRef player) {
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && item.type().equals(ENDER_PEARL)) {
        return true;
      }
    }
    return false;
  }

  public static void aversionFx(Adaptation<?> owner, EntityRef enderman, PlayerRef player) {
    Loc eye = enderman.location().add(0, 1.5, 0);
    Fx.now(owner, eye, FxPriority.AMBIENT)
        .burst(Particles.SMOKE, 4, 0.2)
        .sound(Sounds.ENTITY_ENDERMAN_AMBIENT, 0.3f, 0.7f);
    Fx.now(owner, player, FxPriority.AMBIENT)
        .particle(Particles.REVERSE_PORTAL, 2, 0, 1.0, 0, 0.2, 0.02);
  }

  @ConfigDescription("Prevents Endermen from becoming aggressive when you carry Enderpearls.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 2;
      permanent = true;
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
