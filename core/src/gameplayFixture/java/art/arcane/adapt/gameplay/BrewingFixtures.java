package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.world.WorldRef;

public final class BrewingFixtures {
    private BrewingFixtures() {
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor) {
        BrewInputs inputs = switch (name) {
            case "brew-absorption" -> new BrewInputs("healing", "quartz", false);
            case "brew-blindness" -> new BrewInputs("awkward", "ink_sac", false);
            case "brew-darkness" -> new BrewInputs("night_vision", "black_concrete", false);
            case "brew-decay" -> new BrewInputs("weakness", "poisonous_potato", false);
            case "brew-fatigue" -> new BrewInputs("weakness", "slime_ball", false);
            case "brew-haste" -> new BrewInputs("swiftness", "amethyst_shard", false);
            case "brew-healthboost" -> new BrewInputs("healing", "golden_apple", false);
            case "brew-hunger" -> new BrewInputs("awkward", "rotten_flesh", false);
            case "brew-nausea" -> new BrewInputs("awkward", "brown_mushroom", false);
            case "brew-resistance" -> new BrewInputs("awkward", "iron_ingot", false);
            case "brew-saturation" -> new BrewInputs("regeneration", "baked_potato", false);
            case "brew-lingering" -> new BrewInputs("awkward", "sugar", false);
            case "brew-super-heated" -> new BrewInputs("awkward", "sugar", true);
            default -> null;
        };
        if (inputs == null) {
            return false;
        }
        Fixtures.set(arena, 2, 99, 2, inputs.heated() ? "lava" : "stone");
        Fixtures.set(arena, 2, 100, 2, "brewing_stand");
        ItemRef potion = Fixtures.item("potion");
        potion.setPotionContents(PotionContents.of(Fixtures.id(inputs.base())));
        actor.inventory().addItem(potion);
        Fixtures.give(actor, inputs.ingredient(), 1);
        Fixtures.give(actor, "blaze_powder", 4);
        return true;
    }

    private record BrewInputs(String base, String ingredient, boolean heated) {
    }
}
