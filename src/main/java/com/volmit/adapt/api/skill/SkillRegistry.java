package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.JarScanner;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
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
        if(!e.getBlockFace().equals(BlockFace.UP) && !e.getBlockFace().equals(BlockFace.DOWN) && !e.getPlayer().isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock().getType().equals(Material.BOOKSHELF))
        {
            if(e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENCHANTED_BOOK))
            {
                if(e.getPlayer().hasCooldown(Material.ENCHANTED_BOOK))
                {
                    return;
                }

                e.getPlayer().sendMessage("   ");
                e.getPlayer().setCooldown(Material.ENCHANTED_BOOK, 3);
                AdaptPlayer a = getPlayer(e.getPlayer());
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.1f);
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.6f);
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);

                String xv = a.getData().getMultiplier()-1d > 0 ? "+" + Form.pc(a.getData().getMultiplier() - 1D) : Form.pc(a.getData().getMultiplier() - 1D);
                e.getPlayer().sendMessage("Global" + C.GRAY + ": " + C.GREEN + xv);

                for(XPMultiplier i : a.getData().getMultipliers())
                {
                    String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
                    e.getPlayer().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
                }

                for(PlayerSkillLine i : a.getData().getSkillLines().v())
                {
                    Skill s = i.getRawSkill(a);
                    String v = i.getMultiplier()-a.getData().getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier() - a.getData().getMultiplier()) : Form.pc(i.getMultiplier() - a.getData().getMultiplier());
                    e.getPlayer().sendMessage("  "+ s.getDisplayName() + C.GRAY + ": " + s.getColor() + v);

                    for(XPMultiplier j : i.getMultipliers())
                    {
                        String vv = j.getMultiplier() > 0 ? "+" + Form.pc(j.getMultiplier()) : Form.pc(j.getMultiplier());
                        e.getPlayer().sendMessage("  " + s.getShortName() + C.GRAY + " " + vv + " for " + Form.duration(j.getGoodFor() - M.ms(), 0));
                    }
                }

            }

            else if((e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.AIR) || !e.getPlayer().getInventory().getItemInMainHand().getType().isBlock()) &&
                    (e.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.AIR) || !e.getPlayer().getInventory().getItemInOffHand().getType().isBlock())) {
                e.getClickedBlock().getWorld().playSound(e.getClickedBlock().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 0.72f);
                e.getClickedBlock().getWorld().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
                SkillsGui.open(e.getPlayer());
                e.getPlayer().getWorld().spawnParticle(Particle.CRIT_MAGIC, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 25, 0, 0, 0, 1.1);
                e.getPlayer().getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 12, 0, 0, 0, 1.1);
            }
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
