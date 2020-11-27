package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.JarScanner;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class SkillRegistry extends TickedObject
{
    private KMap<String, Skill> skills = new KMap<>();

    public SkillRegistry() throws IOException {
        super("registry", UUID.randomUUID() + "-sk", 1250);
        JarScanner js = new JarScanner(Adapt.instance.getJarFile(), "com.volmit.adapt.content.skill");
        js.scan();

        for(Class<?> i : js.getClasses())
        {
            if(i.isAssignableFrom(Skill.class) || Skill.class.isAssignableFrom(i))
            {
                registerSkill((Class<? extends Skill>) i);
            }
        }
    }

    public Skill getSkill(String i)
    {
        return skills.get(i);
    }

    public KList<Skill> getSkills()
    {
        return skills.v();
    }

    public void registerSkill(Class<? extends Skill> skill)
    {
        try {
            Skill sk = skill.getConstructor().newInstance();
            skills.put(sk.getName(), sk);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unregister()
    {
        for(Skill i : skills.v())
        {
            i.unregister();
        }
    }

    @Override
    public void onTick() {

    }
}
