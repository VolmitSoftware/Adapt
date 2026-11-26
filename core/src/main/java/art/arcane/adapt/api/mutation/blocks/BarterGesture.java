package art.arcane.adapt.api.mutation.blocks;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * How a player casts Barter. A pair uses the first gesture, in declaration order, that neither parent reserves, so
 * casting Barter never also sets off one of the parents' own abilities.
 *
 * <p>A skill reserves a gesture when one of its abilities (original, upgrade or block, under any control preference
 * and with any item in hand) fires when the gesture is made. An ability on plain swap hands or plain left-click the
 * air counts, since the sneaking form is one of those. A single sneak press is not counted against double-tap sneak.
 * The golem crook is left out: every input made with it is a crook command, so Barter does not cast while it is held.
 */
public enum BarterGesture {
  SNEAK_SWAP("Sneak and swap hands"),
  DOUBLE_SNEAK("Double-tap sneak"),
  SNEAK_LEFT_CLICK("Sneak and left-click the air"),
  SNEAK_SCROLL("Sneak and scroll the hotbar");

  private static final Map<String, Set<BarterGesture>> RESERVED = Map.ofEntries(
      // Air Dash and Skyrunner's dash.
      Map.entry("agility", EnumSet.of(SNEAK_LEFT_CLICK)),
      // Smart Shape, Stonecutter Savant, Chalk Line and Wireless Redstone.
      Map.entry("architect", EnumSet.of(SNEAK_LEFT_CLICK)),
      // The Alchemist's Bandolier picks its potion.
      Map.entry("brewing", EnumSet.of(SNEAK_SCROLL)),
      // Rewind, Second Chance and the Chronos Trigger; Instant Recall's clock click.
      Map.entry("chronos", EnumSet.of(SNEAK_SWAP, SNEAK_LEFT_CLICK)),
      // Compactor and Unmaker.
      Map.entry("crafting", EnumSet.of(SNEAK_SWAP)),
      // Better Mending, also run by Unity Prime.
      Map.entry("discovery", EnumSet.of(SNEAK_LEFT_CLICK)),
      // Mass Shift, and Terminal Toggle's double-tap preference.
      Map.entry("kinetics", EnumSet.of(SNEAK_SWAP, DOUBLE_SNEAK)),
      // Pocket Realm.
      Map.entry("rift", EnumSet.of(SNEAK_LEFT_CLICK)),
      // Hydro Jet's double-tap preference; Tidecaller's and Maelstrom's swings.
      Map.entry("seaborne", EnumSet.of(DOUBLE_SNEAK, SNEAK_LEFT_CLICK)),
      // Phantom Decoy and Decoy Swap (both gestures), Shadowmeld's and Shadow Decoy's double-tap preferences.
      Map.entry("stealth", EnumSet.of(SNEAK_SWAP, DOUBLE_SNEAK)),
      // Alpha's Command with a bone.
      Map.entry("taming", EnumSet.of(SNEAK_LEFT_CLICK)),
      // Shockwave Clap.
      Map.entry("unarmed", EnumSet.of(SNEAK_LEFT_CLICK)));

  private final String phrase;

  BarterGesture(String phrase) {
    this.phrase = phrase;
  }

  public String phrase() {
    return phrase;
  }

  /** The gestures the skill's own abilities use. */
  public static Set<BarterGesture> reserved(String skillId) {
    Set<BarterGesture> reserved = RESERVED.get(skillId);
    return reserved == null ? Set.of() : Set.copyOf(reserved);
  }

  /** The skills that reserve a gesture. */
  public static Set<String> reservingSkills() {
    return RESERVED.keySet();
  }

  public static BarterGesture of(String leadId, String partnerId) {
    Set<BarterGesture> lead = RESERVED.getOrDefault(leadId, Set.of());
    Set<BarterGesture> partner = RESERVED.getOrDefault(partnerId, Set.of());
    for (BarterGesture gesture : values()) {
      if (!lead.contains(gesture) && !partner.contains(gesture)) {
        return gesture;
      }
    }
    throw new IllegalStateException("No Barter gesture is free for " + leadId + " + " + partnerId);
  }
}
