package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.HerbalismGrowthAura;
import com.volmit.adapt.content.adaptation.HerbalismHungryShield;
import com.volmit.adapt.content.adaptation.HerbalismReplant;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Levelled;
import org.bukkit.boss.BarColor;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class SkillHerbalism extends SimpleSkill<SkillHerbalism.Config> {
    public SkillHerbalism() {
        super("herbalism", "\u2698");
        setColor(C.GREEN);
        setInterval(3700);
        setDescription("Sticks and stones don't break your bones");
        setIcon(Material.WHEAT);
        registerAdaptation(new HerbalismGrowthAura());
        registerAdaptation(new HerbalismReplant());
        registerAdaptation(new HerbalismHungryShield());
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.COOKED_BEEF)
            .key("challenge_eat_100")
            .title("So much to eat!")
            .description("Eat over 100 Items!")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.COOKED_BEEF)
                .key("challenge_eat_1000")
                .title("Unquenchable Hunger!")
                .description("Eat over 1,000 Items!")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_eat_100").goal(100).stat("food.eaten").reward(1250).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_eat_1000").goal(1000).stat("food.eaten").reward(6250).build());
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.COOKED_BEEF)
            .key("challenge_harvest_100")
            .title("Full Harvest")
            .description("Harvest over 100 crops!")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.COOKED_BEEF)
                .key("challenge_harvest_1000")
                .title("Grand Harvest")
                .description("Harvest 1,000 crops!")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_harvest_100").goal(100).stat("harvest.blocks").reward(1250).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_harvest_1000").goal(1000).stat("harvest.blocks").reward(6250).build());
    }

    @EventHandler
    public void on(PlayerItemConsumeEvent e) {
        xp(e.getPlayer(), 125);
        getPlayer(e.getPlayer()).getData().addStat("food.eaten", 1);
    }

    @EventHandler
    public void on(PlayerShearEntityEvent e) {
        xp(e.getPlayer(), e.getEntity().getLocation(), 95);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerHarvestBlockEvent e) {
        if(e.isCancelled()) {
            return;
        }

        if(e.getHarvestedBlock().getBlockData() instanceof Ageable) {
            getPlayer(e.getPlayer()).getData().addStat("harvest.blocks", 1);
            xp(e.getPlayer(),e.getHarvestedBlock().getLocation().clone().add(0.5, 0.5, 0.5), 32 * (((Ageable) e.getHarvestedBlock().getBlockData()).getAge()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent e) {
        if(e.getBlock().getBlockData() instanceof Ageable) {
            xp(e.getPlayer(),e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), 3);
            getPlayer(e.getPlayer()).getData().addStat("harvest.planted", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        if(e.useItemInHand().equals(Event.Result.DENY)) {
            return;
        }

        if(e.getClickedBlock() == null) {
            return;
        }

        if(e.getClickedBlock().getType().equals(Material.COMPOSTER)) {
            Levelled c = ((Levelled) e.getClickedBlock().getBlockData());
            int ol = c.getLevel();

            J.s(() -> {
                int nl = ((Levelled) e.getClickedBlock().getBlockData()).getLevel();
                if(nl > ol || (ol > 0 && nl == 0)) {
                    xp(e.getPlayer(),e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5), 51 + (nl * 3) + (nl == 0 ? 250 : 5));
                    getPlayer(e.getPlayer()).getData().addStat("harvest.composted", 1);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if(e.getBlock().getType().equals(Material.CACTUS)) {
            return;
        }

        if(e.getBlock().getBlockData() instanceof Ageable) {
            xp(e.getPlayer(),e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), 32 * (((Ageable) e.getBlock().getBlockData()).getAge()));
            getPlayer(e.getPlayer()).getData().addStat("harvest.blocks", 1);
        }
    }

    @Override
    public void onTick() {

    }

    protected static class Config{}
}
