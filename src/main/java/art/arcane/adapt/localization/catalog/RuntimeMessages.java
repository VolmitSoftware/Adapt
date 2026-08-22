package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class RuntimeMessages {
  public static final TextKey KNOWLEDGE_GAIN = TextKey.of("runtime.knowledge_gain", "+ {amount} {skill} Knowledge");
  public static final TextKey KNOWLEDGE_STATUS = TextKey.of("runtime.knowledge_status", "{amount} {skill} Knowledge");
  public static final TextKey REQUIRES_ADAPTATION = TextKey.of("runtime.requires_adaptation", "Requires {adaptation} from {skill}");
  public static final TextKey UNKNOWN_SKILL = TextKey.of("runtime.unknown_skill", "Unknown Skill");
  public static final TextKey ADVANCEMENT_UNLOCK = TextKey.of("runtime.advancement_unlock", "{description}. {instruction} {block}");
  public static final TextKey MAX_ABILITY_POWER = TextKey.of("runtime.max_ability_power", "{power} Maximum Ability Power");
  public static final TextKey XP_BONUS = TextKey.of("runtime.xp_bonus", "+{percent} XP for {duration}");
  public static final TextKey STAT_LORE = TextKey.of("runtime.stat_lore", "{prefix}{value}{separator}{label}");
  public static final TextKey DATA_DELETED_KICK = TextKey.of("runtime.data_deleted_kick", "Your data has been deleted.");
  public static final TextKey NO_DESCRIPTION_PROVIDED = TextKey.of("runtime.no_description_provided", "No description provided");
  public static final TextKey CONFIG_HOTLOADED = TextKey.of("runtime.config_hotloaded", "&7[&4Adapt&7]: &aAdapt Hotloaded: &f[{file}] &b[{key}] &7[{oldValue} -> {newValue}]");
  public static final TextKey CONFIG_HOTLOAD_TRUNCATED = TextKey.of("runtime.config_hotload_truncated", "&7[&4Adapt&7]: &aAdapt Hotloaded: &f[{file}] &7[{remaining} additional changes omitted]");

  private RuntimeMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(KNOWLEDGE_GAIN);
    builder.add(KNOWLEDGE_STATUS);
    builder.add(REQUIRES_ADAPTATION);
    builder.add(UNKNOWN_SKILL);
    builder.add(ADVANCEMENT_UNLOCK);
    builder.add(MAX_ABILITY_POWER);
    builder.add(XP_BONUS);
    builder.add(STAT_LORE);
    builder.add(DATA_DELETED_KICK);
    builder.add(NO_DESCRIPTION_PROVIDED);
    builder.add(CONFIG_HOTLOADED);
    builder.add(CONFIG_HOTLOAD_TRUNCATED);
  }
}
