package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

public final class ItemsMessages {
  public static final TextKey BOUND_ENDER_PERAL_NAME = TextKey.of("items.bound_ender_peral.name", "Reliquary Portkey");
  public static final TextKey BOUND_ENDER_PERAL_USAGE1 = TextKey.of("items.bound_ender_peral.usage1", "Shift + Left Click to bind");
  public static final TextKey BOUND_ENDER_PERAL_USAGE2 = TextKey.of("items.bound_ender_peral.usage2", "Right Click to access the bound Inventory");
  public static final TextKey BOUND_EYE_OF_ENDER_NAME = TextKey.of("items.bound_eye_of_ender.name", "Ocular Anchor");
  public static final TextKey BOUND_EYE_OF_ENDER_USAGE1 = TextKey.of("items.bound_eye_of_ender.usage1", "Right Click to consume and teleport to the bound location");
  public static final TextKey BOUND_EYE_OF_ENDER_USAGE2 = TextKey.of("items.bound_eye_of_ender.usage2", "Shift + Left Click to bind to a block");
  public static final TextKey BOUND_REDSTONE_TORCH_NAME = TextKey.of("items.bound_redstone_torch.name", "Redstone Remote");
  public static final TextKey BOUND_REDSTONE_TORCH_USAGE1 = TextKey.of("items.bound_redstone_torch.usage1", "Right Click to power the bound face for 4 ticks");
  public static final TextKey BOUND_REDSTONE_TORCH_USAGE2 = TextKey.of("items.bound_redstone_torch.usage2", "Shift + Left Click a free face on any block to bind");
  public static final TextKey BOUND_SNOWBALL_NAME = TextKey.of("items.bound_snowball.name", "Web Snare!");
  public static final TextKey BOUND_SNOWBALL_USAGE1 = TextKey.of("items.bound_snowball.usage1", "Throw to create a temporary web trap at the location");
  public static final TextKey CHRONO_TIME_BOTTLE_NAME = TextKey.of("items.chrono_time_bottle.name", "Time In A Bottle");
  public static final TextKey CHRONO_TIME_BOTTLE_USAGE1 = TextKey.of("items.chrono_time_bottle.usage1", "Passively stores time while in your inventory");
  public static final TextKey CHRONO_TIME_BOTTLE_USAGE2 = TextKey.of("items.chrono_time_bottle.usage2", "Right-click timed blocks or baby animals to spend stored time");
  public static final TextKey CHRONO_TIME_BOTTLE_STORED = TextKey.of("items.chrono_time_bottle.stored", "Stored Time");
  public static final TextKey CHRONO_TIME_BOTTLE_STORED_VALUE = TextKey.of("items.chrono_time_bottle.stored_value", "Stored Time: {duration}");
  public static final TextKey CHRONO_TIME_BOMB_NAME = TextKey.of("items.chrono_time_bomb.name", "Time Bomb");
  public static final TextKey CHRONO_TIME_BOMB_USAGE1 = TextKey.of("items.chrono_time_bomb.usage1", "Right-click to launch a chrono bolt that creates a temporal field");
  public static final TextKey ELEVATOR_BLOCK_NAME = TextKey.of("items.elevator_block.name", "Elevator Block");
  public static final TextKey ELEVATOR_BLOCK_USAGE1 = TextKey.of("items.elevator_block.usage1", "Jump to teleport up");
  public static final TextKey ELEVATOR_BLOCK_USAGE2 = TextKey.of("items.elevator_block.usage2", "Shift to teleport down");
  public static final TextKey ELEVATOR_BLOCK_USAGE3 = TextKey.of("items.elevator_block.usage3", "Minimum of 2 air blocks between the elevators");
  public static final TextKey POTION_GRANTS = TextKey.of("items.potion.grants", "Grants {effect} {level}");
  public static final PluralKey MULTI_ARMOR_COUNT = PluralKey.of("items.multi_armor.count", "count", Map.of(
      "one", "MultiArmor ({count} Item)",
      "other", "MultiArmor ({count} Items)"
  ));
  public static final PluralKey OMNI_TOOL_COUNT = PluralKey.of("items.omni_tool.count", "count", Map.of(
      "one", "Leatherman ({count} Item)",
      "other", "Leatherman ({count} Items)"
  ));

  private ItemsMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(BOUND_ENDER_PERAL_NAME);
    builder.add(BOUND_ENDER_PERAL_USAGE1);
    builder.add(BOUND_ENDER_PERAL_USAGE2);
    builder.add(BOUND_EYE_OF_ENDER_NAME);
    builder.add(BOUND_EYE_OF_ENDER_USAGE1);
    builder.add(BOUND_EYE_OF_ENDER_USAGE2);
    builder.add(BOUND_REDSTONE_TORCH_NAME);
    builder.add(BOUND_REDSTONE_TORCH_USAGE1);
    builder.add(BOUND_REDSTONE_TORCH_USAGE2);
    builder.add(BOUND_SNOWBALL_NAME);
    builder.add(BOUND_SNOWBALL_USAGE1);
    builder.add(CHRONO_TIME_BOTTLE_NAME);
    builder.add(CHRONO_TIME_BOTTLE_USAGE1);
    builder.add(CHRONO_TIME_BOTTLE_USAGE2);
    builder.add(CHRONO_TIME_BOTTLE_STORED);
    builder.add(CHRONO_TIME_BOTTLE_STORED_VALUE);
    builder.add(CHRONO_TIME_BOMB_NAME);
    builder.add(CHRONO_TIME_BOMB_USAGE1);
    builder.add(ELEVATOR_BLOCK_NAME);
    builder.add(ELEVATOR_BLOCK_USAGE1);
    builder.add(ELEVATOR_BLOCK_USAGE2);
    builder.add(ELEVATOR_BLOCK_USAGE3);
    builder.add(POTION_GRANTS);
    builder.add(MULTI_ARMOR_COUNT);
    builder.add(OMNI_TOOL_COUNT);
  }
}
