package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.viewer.GlowLayer;
import art.arcane.adapt.api.viewer.ViewerGlowPort;
import art.arcane.adapt.content.adaptation.GlowLayers;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;

import java.util.ArrayList;
import java.util.List;

/** Animals and mobs around the player glow for that player alone, on Hunter's own per-viewer glow layer. */
public class HunterWorkStrike extends StrikeBlock<HunterWorkStrike.Config> {
  private static final GlowLayer LAYER = GlowLayers.HUNTER;

  public HunterWorkStrike() {
    super(SkillHunter.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    ViewerGlowPort glow = ViewerGlowPort.current();
    if (!glow.isAvailable()) {
      return Outcome.MISSED;
    }
    double radius = getConfig().radius;
    Loc center = player.location();
    List<LivingRef> sensed = new ArrayList<>();
    for (EntityRef entity : player.nearbyEntities(radius, radius, radius)) {
      if (entity instanceof LivingRef living && !(entity instanceof PlayerRef) && living.isValid() && !living.isDead()
          && (entity instanceof MobRef || entity.has(EntityTrait.ANIMAL)) && !entity.has(EntityTrait.GOLEM)
          && living.location().distanceSquared(center) <= radius * radius
          && glow.set(LAYER, living, player, GlowColor.RED)) {
        sensed.add(living);
      }
    }
    if (sensed.isEmpty()) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().durationMillis / 50L);
    Runnable fade = () -> {
      for (LivingRef living : sensed) {
        glow.unset(LAYER, living.uuid(), living.entityId(), player);
      }
    };
    if (!AdaptApi.scheduler().runEntity(player, fade, ticks, fade)) {
      fade.run();
      return Outcome.MISSED;
    }
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Animals and mobs within 16 blocks glow for you for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Reach around you in blocks.", impact = "Higher values reveal prey farther away.")
    double radius = 16D;
    @ConfigDoc(value = "How long the glow lasts, in milliseconds.", impact = "Higher values keep prey outlined longer.")
    long durationMillis = 5_000L;
  }
}
