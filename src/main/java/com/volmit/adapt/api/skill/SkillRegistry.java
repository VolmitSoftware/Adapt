package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.JarScanner;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class SkillRegistry extends TickedObject {
    private final KMap<String, Skill> skills = new KMap<>();

    public SkillRegistry() throws IOException {
        super("registry", UUID.randomUUID() + "-sk", 1250);
        JarScanner js = new JarScanner(Adapt.instance.getJarFile(), "com.volmit.adapt.content.skill");
        js.scan();

        for(Class<?> i : js.getClasses()) {
            if(i.isAssignableFrom(Skill.class) || Skill.class.isAssignableFrom(i)) {
                registerSkill((Class<? extends Skill>) i);
            }
        }
    }

    @EventHandler
    public void on(PlayerExpChangeEvent e) {
        if(e.getAmount() > 0) {
            getPlayer(e.getPlayer()).boostXPToRecents(getPlayer(e.getPlayer()), 0.03, 10000);
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(!e.getBlockFace().equals(BlockFace.UP) && !e.getBlockFace().equals(BlockFace.DOWN) && !e.getPlayer().isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock().getType().equals(Material.BOOKSHELF) &&
            (e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.AIR) || !e.getPlayer().getInventory().getItemInMainHand().getType().isBlock()) &&
            (e.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.AIR) || !e.getPlayer().getInventory().getItemInOffHand().getType().isBlock())) {
            e.getClickedBlock().getWorld().playSound(e.getClickedBlock().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 0.72f);
            e.getClickedBlock().getWorld().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
            SkillsGui.open(e.getPlayer());
            e.getPlayer().getWorld().spawnParticle(Particle.CRIT_MAGIC, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 25, 0, 0, 0, 1.1);
            e.getPlayer().getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 12, 0, 0, 0, 1.1);
        }
    }

    public Skill getSkill(String i) {
        return skills.get(i);
    }

    public KList<Skill> getSkills() {
        return skills.v();
    }

    public void registerSkill(Class<? extends Skill> skill) {
        try {
            Skill sk = skill.getConstructor().newInstance();
            skills.put(sk.getName(), sk);
        } catch(InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unregister() {
        for(Skill i : skills.v()) {
            i.unregister();
        }
    }

    @Override
    public void onTick() {

    }
}
