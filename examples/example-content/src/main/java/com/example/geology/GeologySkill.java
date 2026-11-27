package com.example.geology;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

/** Geology: XP for breaking stone and ore, four adaptations and a mutation kit. */
public final class GeologySkill extends SimpleSkill<GeologySkill.Config> {
  public static final String ROCKS_STAT = "example.geology.rocks";
  private static final String ROCKS_ADVANCEMENT = "geology_rocks_100";

  private final XpSource stone = xpSource("stone");
  private final XpSource ore = xpSource("ore");

  public GeologySkill() {
    super(GeologyAddon.GEOLOGY);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x8A6F4E));
    setIcon(Id.minecraft("tuff"));
    registerAdaptation(StoneSkin.ID, StoneSkin::new);
    registerAdaptation(Prospect.ID, Prospect::new);
    registerAdaptation(CoreSample.ID, CoreSample::new);
    registerAdaptation(GeodeLens.ID, GeodeLens::new);
    registerGroup(text("group.field"), List.of(StoneSkin.ID, Prospect.ID, CoreSample.ID));
    registerGroup(text("group.workshop"), List.of(GeodeLens.ID));
    xpNote(text("xp_note"));
    registerAdvancement(AdaptAdvancement.builder()
        .key(ROCKS_ADVANCEMENT)
        .icon(Id.minecraft("cobblestone"))
        .title(Text.key(TextKeys.advancement(GeologyAddon.GEOLOGY, ROCKS_ADVANCEMENT) + ".title"))
        .description(Text.key(TextKeys.advancement(GeologyAddon.GEOLOGY, ROCKS_ADVANCEMENT) + ".description"))
        .frame(AdvancementFrame.TASK)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone(ROCKS_ADVANCEMENT, ROCKS_STAT, 100D, () -> getConfig().milestoneReward);
    registerMutationKit(GeologyKit.create());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent event) {
    PlayerRef player = event.player();
    Id type = event.block().type();
    shouldReturnForPlayer(player, event, () -> {
      if (Rocks.isOre(type)) {
        ore.award(player, event.block().center(), getConfig().oreXp, type.path());
      } else if (Rocks.isStone(type)) {
        stone.award(player, event.block().center(), getConfig().stoneXp, type.path());
        addStat(player, ROCKS_STAT, 1D);
      }
    });
  }

  public static final class Config extends SkillConfig {
    @ConfigDoc("XP for each block of natural stone broken.")
    public double stoneXp = 4D;
    @ConfigDoc("XP for each ore block broken.")
    public double oreXp = 18D;
    @ConfigDoc("XP paid once for breaking 100 stone.")
    public double milestoneReward = 250D;
  }
}
