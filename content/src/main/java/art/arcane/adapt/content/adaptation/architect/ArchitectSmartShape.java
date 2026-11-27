/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArchitectSmartShape extends SimpleAdaptation<ArchitectSmartShape.Config> {
  public static final Id ID = Id.adapt("architect/smart_shape");
  public static final PlayerPreference<Families> FAMILIES = PlayerPreference.of("families", Families.class, Families.ALL, List.of(
          new PlayerPreference.Choice<>(Families.ALL, Text.key("adapt.architectsmartshape.preferences.preference_families_all"), Id.minecraft("compass"), 1),
          new PlayerPreference.Choice<>(Families.DIRECTIONAL, Text.key("adapt.architectsmartshape.preferences.preference_families_directional"), Id.minecraft("piston"), 1),
          new PlayerPreference.Choice<>(Families.ROTATABLE, Text.key("adapt.architectsmartshape.preferences.preference_families_rotatable"), Id.minecraft("oak_sign"), 1),
          new PlayerPreference.Choice<>(Families.PILLARS, Text.key("adapt.architectsmartshape.preferences.preference_families_pillars"), Id.minecraft("oak_log"), 1))).labelled(Text.key("adapt.architectsmartshape.preferences.preference_families"));
  public static final PlayerPreference<CommonPreferences.Toggle> REVERSE = CommonPreferences.toggle("reverse", Text.key("adapt.architectsmartshape.preferences.preference_reverse"), CommonPreferences.Toggle.OFF);

  static final String FACING = "facing";
  static final String ROTATION = "rotation";
  static final String AXIS = "axis";
  /** Cardinal facings in clockwise yaw order. */
  static final List<String> YAW_ORDER = List.of("north", "east", "south", "west");
  static final List<String> ALL_FACES = List.of("north", "east", "south", "west", "up", "down");
  static final List<String> AXIS_ORDER = List.of("x", "y", "z");
  /** Vanilla sign rotation steps: 16 values of 22.5 degrees, 8 is north. */
  static final int ROTATION_STEPS = 16;
  private static final int NORTH_ROTATION = 8;

  public ArchitectSmartShape() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bricks"));
    setInterval(800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("quartz_stairs"))
        .key("challenge_architect_smart_shape_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("quartz_stairs"))
            .key("challenge_architect_smart_shape_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_smart_shape_200", "architect.smart-shape.rotations", 200, 300);
    registerMilestone("challenge_architect_smart_shape_5k", "architect.smart-shape.rotations", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FAMILIES, REVERSE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.architect.smart_shape.lore1").resolve());
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.architect.smart_shape.lore2").resolve());
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_BLOCK && action != InteractAction.LEFT_CLICK_AIR) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    withAdaptedPlayer(p, () -> {
      if (!p.isSneaking()) {
        return;
      }

      ItemRef hand = p.inventory().mainHand();
      if (ItemKinds.isItem(hand) && !hand.isEmpty()) {
        return;
      }

      BlockRef target = action == InteractAction.LEFT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
      if (target == null) {
        return;
      }

      if (!canBlockPlace(p, target.location())) {
        return;
      }

      BlockStateRef data = target.state();
      Families family = preference(p, FAMILIES);
      if ((family == Families.DIRECTIONAL && data.property(FACING) == null)
          || (family == Families.ROTATABLE && data.property(ROTATION) == null)
          || (family == Families.PILLARS && data.property(AXIS) == null)) {
        return;
      }
      Rotation rotation = rotateData(data);
      int options = rotation.options();
      if (options > 1 && preferenceEnabled(p, REVERSE)) {
        for (int index = 1; index < options - 1; index++) {
          rotation = rotateData(rotation.state());
        }
      }
      if (options <= 0) {
        fx(target.location().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
            .sound(Sounds.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.25f, 0.9f);
        return;
      }

      target.setState(rotation.state(), true);
      e.setCancelled(true);
      fx(target.location().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
          .ring(Particles.CRIT, 0.55D, 8, 0.3D)
          .chord(Sounds.ITEM_AXE_STRIP, 0.45f, 1.8f, Sounds.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.4f, 1.6f);
      xp(p, Math.max(getConfig().minXpPerRotate, options * getConfig().xpPerOrientationOption));
      addStat(p, "architect.smart-shape.rotations", 1);
      Mutations.landed(this, p);
    });
  }

  /**
   * One rotation step: facing blocks spin around Y, signs step 22.5 degrees, pillars cycle their axes. The state is
   * unchanged and the option count zero when the block cannot turn.
   */
  static Rotation rotateData(BlockStateRef data) {
    String facing = data.property(FACING);
    if (facing != null) {
      List<String> yawFaces = yawFaces(supportedValues(data, FACING, ALL_FACES));
      String next = nextYawFace(facing, yawFaces);
      if (next != null) {
        return new Rotation(data.with(FACING, next), yawFaces.size());
      }
    }

    String rotation = data.property(ROTATION);
    if (rotation != null) {
      return new Rotation(data.with(ROTATION, Integer.toString(nextRotation(parseRotation(rotation)))), ROTATION_STEPS);
    }

    String axis = data.property(AXIS);
    if (axis != null) {
      List<String> axes = supportedValues(data, AXIS, AXIS_ORDER);
      String next = nextAxis(axis, axes);
      if (next != null) {
        return new Rotation(data.with(AXIS, next), axes.size());
      }
    }

    return new Rotation(data, 0);
  }

  /** The candidate values the block state accepts for the property, in candidate order. */
  static List<String> supportedValues(BlockStateRef data, String property, List<String> candidates) {
    List<String> supported = new ArrayList<>(candidates.size());
    for (String candidate : candidates) {
      if (candidate.equals(data.with(property, candidate).property(property))) {
        supported.add(candidate);
      }
    }
    return List.copyOf(supported);
  }

  /** Rotation is a spin around Y, so only the cardinal faces a block supports are reachable. */
  static List<String> yawFaces(Collection<String> supported) {
    if (supported == null || supported.isEmpty()) {
      return List.of();
    }

    List<String> faces = new ArrayList<>(YAW_ORDER.size());
    for (String face : YAW_ORDER) {
      if (supported.contains(face)) {
        faces.add(face);
      }
    }

    return List.copyOf(faces);
  }

  /** Null for UP/DOWN facings: a spin around Y cannot move them, and forcing one detaches the block. */
  static String nextYawFace(String current, List<String> yawFaces) {
    if (yawFaces.size() < 2) {
      return null;
    }

    int index = yawFaces.indexOf(current);
    return index < 0 ? null : yawFaces.get((index + 1) % yawFaces.size());
  }

  /** Signs, banners and skulls yaw in vanilla 22.5 degree steps; anything unreadable restarts at north. */
  static int nextRotation(int current) {
    if (current < 0 || current >= ROTATION_STEPS) {
      return NORTH_ROTATION;
    }
    return (current + 1) % ROTATION_STEPS;
  }

  private static int parseRotation(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException invalid) {
      return -1;
    }
  }

  /** Pillars have no upside down state, so every supported axis stays reachable. */
  static String nextAxis(String current, Collection<String> supported) {
    if (supported == null || supported.size() < 2) {
      return null;
    }

    int index = Math.max(0, AXIS_ORDER.indexOf(current));
    for (int offset = 1; offset < AXIS_ORDER.size(); offset++) {
      String candidate = AXIS_ORDER.get((index + offset) % AXIS_ORDER.size());
      if (!candidate.equals(current) && supported.contains(candidate)) {
        return candidate;
      }
    }

    return null;
  }

  record Rotation(BlockStateRef state, int options) {
  }

  @ConfigDescription("Sneak-left-click a block with an empty hand to rotate its orientation.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Min Xp Per Rotate for the Architect Smart Shape adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minXpPerRotate = 0.4;
    @ConfigDoc(value = "Controls Xp Per Orientation Option for the Architect Smart Shape adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerOrientationOption = 0.16;

    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 1;
    }
  }

  public enum Families { ALL, DIRECTIONAL, ROTATABLE, PILLARS }
}
