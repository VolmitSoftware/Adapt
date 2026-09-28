package art.arcane.adapt.gameplay;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class BrewingFixtures {
    private BrewingFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor) {
        BrewInputs inputs = switch (name) {
            case "brew-absorption" -> new BrewInputs(PotionType.HEALING, Material.QUARTZ, false);
            case "brew-blindness" -> new BrewInputs(PotionType.AWKWARD, Material.INK_SAC, false);
            case "brew-darkness" -> new BrewInputs(PotionType.NIGHT_VISION, Material.BLACK_CONCRETE, false);
            case "brew-decay" -> new BrewInputs(PotionType.WEAKNESS, Material.POISONOUS_POTATO, false);
            case "brew-fatigue" -> new BrewInputs(PotionType.WEAKNESS, Material.SLIME_BALL, false);
            case "brew-haste" -> new BrewInputs(PotionType.SWIFTNESS, Material.AMETHYST_SHARD, false);
            case "brew-healthboost" -> new BrewInputs(PotionType.HEALING, Material.GOLDEN_APPLE, false);
            case "brew-hunger" -> new BrewInputs(PotionType.AWKWARD, Material.ROTTEN_FLESH, false);
            case "brew-nausea" -> new BrewInputs(PotionType.AWKWARD, Material.BROWN_MUSHROOM, false);
            case "brew-resistance" -> new BrewInputs(PotionType.AWKWARD, Material.IRON_INGOT, false);
            case "brew-saturation" -> new BrewInputs(PotionType.REGENERATION, Material.BAKED_POTATO, false);
            case "brew-lingering" -> new BrewInputs(PotionType.AWKWARD, Material.SUGAR, false);
            case "brew-super-heated" -> new BrewInputs(PotionType.AWKWARD, Material.SUGAR, true);
            default -> null;
        };
        if (inputs == null) {
            return false;
        }
        arena.getBlockAt(2, 99, 2).setType(inputs.heated() ? Material.LAVA : Material.STONE, false);
        arena.getBlockAt(2, 100, 2).setType(Material.BREWING_STAND, false);
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(inputs.base());
        potion.setItemMeta(meta);
        actor.getInventory().addItem(potion, new ItemStack(inputs.ingredient()), new ItemStack(Material.BLAZE_POWDER, 4));
        return true;
    }

    private record BrewInputs(PotionType base, Material ingredient, boolean heated) {
    }
}
