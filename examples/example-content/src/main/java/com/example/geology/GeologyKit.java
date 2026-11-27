package com.example.geology;

import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

/** The six mutation blocks that let Geology merge with any other skill that has a kit. */
public final class GeologyKit {
  private static final Id HASTE = Id.minecraft("haste");

  private GeologyKit() {
  }

  public static MutationKit create() {
    String skill = TextKeys.skill(GeologyAddon.GEOLOGY);
    return MutationKit.builder()
        .words(Text.key(skill + ".mutated.noun"), Text.key(skill + ".mutated.adjective"))
        .trigger(TriggerKind.WORK, Trigger::new)
        .strikes(FightStrike::new, WorkStrike::new)
        .boon(Boon::new)
        .fuel(Fuel::new)
        .state(State::new)
        .sounds(SoundPalette.of(Sounds.BLOCK_STONE_BREAK, Sounds.BLOCK_DEEPSLATE_BREAK,
            Sounds.BLOCK_AMETHYST_BLOCK_CHIME, Sounds.BLOCK_STONE_HIT))
        .tint(Rgb.of(0x8A6F4E))
        .build();
  }

  public static final class Trigger extends TriggerBlock<MutationBlock.Config> {
    public Trigger() {
      super(GeologyAddon.GEOLOGY);
      registerConfiguration(MutationBlock.Config.class);
      setIcon(Id.minecraft("stone"));
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent event) {
      PlayerRef player = event.player();
      if (hasActiveAdaptation(player) && Rocks.isStone(event.block().type())) {
        fire(player, null, event.block().location(), false);
      }
    }
  }

  public static final class FightStrike extends StrikeBlock<MutationBlock.Config> {
    public FightStrike() {
      super(GeologyAddon.GEOLOGY, StrikeForm.FIGHT);
      registerConfiguration(MutationBlock.Config.class);
      setIcon(Id.minecraft("pointed_dripstone"));
    }

    @Override
    public Outcome strike(BlockUse use) {
      addPotionStacks(use.player(), Id.minecraft("resistance"), 0, (int) use.scaled(40D, 120D), false);
      return Outcome.INSTANT;
    }
  }

  public static final class WorkStrike extends StrikeBlock<MutationBlock.Config> {
    public WorkStrike() {
      super(GeologyAddon.GEOLOGY, StrikeForm.WORK);
      registerConfiguration(MutationBlock.Config.class);
      setIcon(Id.minecraft("iron_pickaxe"));
    }

    @Override
    public Outcome strike(BlockUse use) {
      addPotionStacks(use.player(), HASTE, 0, (int) use.scaled(40D, 160D), false);
      return Outcome.INSTANT;
    }
  }

  public static final class Boon extends BoonBlock<MutationBlock.Config> {
    public Boon() {
      super(GeologyAddon.GEOLOGY);
      registerConfiguration(MutationBlock.Config.class);
      setIcon(Id.minecraft("amethyst_cluster"));
    }

    @Override
    public Outcome grant(BlockUse use) {
      giveExp(use.player(), (int) use.scaled(2D, 8D));
      return Outcome.INSTANT;
    }
  }

  public static final class Fuel extends FuelBlock<MutationBlock.Config> {
    public Fuel() {
      super(GeologyAddon.GEOLOGY);
      registerConfiguration(MutationBlock.Config.class);
      setIcon(Id.minecraft("bread"));
    }

    @Override
    public boolean canPay(BlockUse use) {
      return use.player().foodLevel() > 2;
    }

    @Override
    public boolean pay(BlockUse use) {
      return payHunger(use, 1);
    }
  }

  public static final class State extends StateBlock<MutationBlock.Config> {
    public State() {
      super(GeologyAddon.GEOLOGY);
      registerConfiguration(MutationBlock.Config.class);
      setIcon(Id.minecraft("deepslate"));
    }

    @Override
    public boolean holds(PlayerRef player) {
      return Rocks.isPickaxe(player.inventory().mainHand());
    }
  }
}
