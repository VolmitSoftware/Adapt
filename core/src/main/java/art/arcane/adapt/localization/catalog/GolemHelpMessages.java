package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class GolemHelpMessages {
  public static final TextKey CROOK_MANAGE = TextKey.of("golemancy.help.crook.manage", "Right-click your golem to manage it");
  public static final TextKey CROOK_ROSTER = TextKey.of("golemancy.help.crook.roster", "Sneak-right-click the air to open your roster");
  public static final TextKey CROOK_AREAS = TextKey.of("golemancy.help.crook.areas", "Hold to see your channel-colored work areas");
  public static final TextKey CROOK_SHIFT = TextKey.of("golemancy.help.crook.shift", "Double-sneak inside an area to shift it");
  public static final TextKey CROOK_SCROLL = TextKey.of("golemancy.help.crook.scroll", "Then sneak-scroll along the direction you look");
  public static final TextKey CORE_BIND = TextKey.of("golemancy.help.core.bind", "Sneak-right-click an unbound golem to bind it");
  public static final TextKey CORE_COMPANION = TextKey.of("golemancy.help.core.companion", "Companions follow and guard you; they do not work");
  public static final TextKey CORE_CRAFT = TextKey.of("golemancy.help.core.craft", "Craft a role core before binding a worker");
  public static final TextKey CORE_FIXED = TextKey.of("golemancy.help.core.fixed", "Primary role stays until you release this golem");
  public static final TextKey ROSTER_BIND = TextKey.of("golemancy.help.roster.bind", "Build a snow golem and bind it with a core");
  public static final TextKey ROSTER_WORKER = TextKey.of("golemancy.help.roster.worker", "Use a Gatherer Core and link a home chest for item pickup");
  public static final TextKey MODE_WORK = TextKey.of("golemancy.help.mode.work", "Do your core's jobs around home");
  public static final TextKey MODE_FOLLOW = TextKey.of("golemancy.help.mode.follow", "Follow you while you are nearby");
  public static final TextKey MODE_STAY = TextKey.of("golemancy.help.mode.stay", "Stay near the spot where you chose this mode");
  public static final TextKey MODE_HOME = TextKey.of("golemancy.help.mode.home", "Return home and wait there");
  public static final TextKey HOME_REACH = TextKey.of("golemancy.help.home.reach", "Job reach from home: {value} blocks");
  public static final TextKey HOME_LEASH = TextKey.of("golemancy.help.home.leash", "Travel and chest-link limit: {value} blocks");
  public static final TextKey CHANNEL_PICK = TextKey.of("golemancy.help.channel.pick", "Click to choose a channel");
  public static final TextKey CHANNEL_MATCH = TextKey.of("golemancy.help.channel.match", "Colored nodes need a matching selected channel");
  public static final TextKey CHANNEL_NONE = TextKey.of("golemancy.help.channel.none", "Your nodes with no channel accept all your golems");
  public static final TextKey RUNE_EFFECTIVE = TextKey.of("golemancy.help.rune.effective", "Masterwork: tier {tier} numeric effects, strength ×{value}");
  public static final TextKey RUNE_SPEED = TextKey.of("golemancy.help.rune.speed", "Movement speed: +{value}%");
  public static final TextKey RUNE_WORK = TextKey.of("golemancy.help.rune.work", "Workers: {value}% shorter action delays");
  public static final TextKey RUNE_CARRY = TextKey.of("golemancy.help.rune.carry", "Workers: +{value}% carry capacity");
  public static final TextKey RUNE_ATTACK = TextKey.of("golemancy.help.rune.attack", "Melee attackers: +{value} damage");
  public static final TextKey RUNE_IGNITE = TextKey.of("golemancy.help.rune.ignite", "Attacks ignite targets for {value} seconds");
  public static final TextKey RUNE_FIRE = TextKey.of("golemancy.help.rune.fire", "Immune to fire damage");
  public static final TextKey RUNE_LAVA = TextKey.of("golemancy.help.rune.lava", "Immune to lava damage");
  public static final TextKey RUNE_RADIUS = TextKey.of("golemancy.help.rune.radius", "Work and mob detection reach: +{value} blocks");
  public static final TextKey RUNE_WATER = TextKey.of("golemancy.help.rune.water", "Breathes underwater; protects frost from water damage");
  public static final TextKey RUNE_SWIM = TextKey.of("golemancy.help.rune.swim", "Water movement efficiency: +{value}");
  public static final TextKey RUNE_CHANNELS = TextKey.of("golemancy.help.rune.channels", "Select up to {value} channels");
  public static final TextKey RUNE_RETRY = TextKey.of("golemancy.help.rune.retry", "Failed-job retry delay: {value}% shorter");
  public static final TextKey RUNE_YIELD = TextKey.of("golemancy.help.rune.yield", "Eligible work drops: {value}% chance to double");
  public static final TextKey RUNE_WEAKNESS = TextKey.of("golemancy.help.rune.weakness", "Attacks apply Weakness {level} for {value} seconds");
  public static final TextKey RUNE_ARMOR = TextKey.of("golemancy.help.rune.armor", "Armor: +{value}");
  public static final TextKey RUNE_TOUGHNESS = TextKey.of("golemancy.help.rune.toughness", "Armor toughness: +{value}");
  public static final TextKey RUNE_KNOCKBACK = TextKey.of("golemancy.help.rune.knockback", "Knockback resistance: +{value}%");

  private GolemHelpMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (TextKey key : new TextKey[]{CROOK_MANAGE, CROOK_ROSTER, CROOK_AREAS, CROOK_SHIFT, CROOK_SCROLL, CORE_BIND,
        CORE_COMPANION, CORE_CRAFT, CORE_FIXED, ROSTER_BIND, ROSTER_WORKER, MODE_WORK, MODE_FOLLOW, MODE_STAY,
        MODE_HOME, HOME_REACH, HOME_LEASH, CHANNEL_PICK, CHANNEL_MATCH, CHANNEL_NONE, RUNE_EFFECTIVE, RUNE_SPEED,
        RUNE_WORK, RUNE_CARRY, RUNE_ATTACK, RUNE_IGNITE, RUNE_FIRE, RUNE_LAVA, RUNE_RADIUS, RUNE_WATER, RUNE_SWIM,
        RUNE_CHANNELS, RUNE_RETRY, RUNE_YIELD, RUNE_WEAKNESS, RUNE_ARMOR, RUNE_TOUGHNESS, RUNE_KNOCKBACK}) {
      builder.add(key);
    }
  }
}
