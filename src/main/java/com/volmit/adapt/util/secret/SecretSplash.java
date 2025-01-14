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

package com.volmit.adapt.util.secret;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.C;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

public class SecretSplash {

    @Getter
    public static final List<String> secretSplash = List.of(
            "\n" + C.BLUE + "       ⣞⢽⢪⢣⢣⢣⢫⡺⡵⣝⡮⣗⢷⢽⢽⢽⣮⡷⡽⣜⣜⢮⢺⣜⢷⢽⢝⡽⣝   \n" +
                    C.BLUE + "       ⠸⡸⠜⠕⠕⠁⢁⢇⢏⢽⢺⣪⡳⡝⣎⣏⢯⢞⡿⣟⣷⣳⢯⡷⣽⢽⢯⣳⣫⠇  \n" +
                    C.BLUE + "        ⢀⢀⢄⢬⢪⡪⡎⣆⡈⠚⠜⠕⠇⠗⠝⢕⢯⢫⣞⣯⣿⣻⡽⣏⢗⣗⠏⠀    " + C.DARK_RED + "Adapt\n" +
                    C.BLUE + "       ⠪⡪⡪⣪⢪⢺⢸⢢⢓⢆⢤⢀⠀⠀⠀⠀⠈⢊⢞⡾⣿⡯⣏⢮⠷⠁⠀⠀     " + C.GRAY + "Version: " + C.DARK_RED + Adapt.instance.getDescription().getVersion() + "\n" +
                    C.BLUE + "         ⠈⠊⠆⡃⠕⢕⢇⢇⢇⢇⢇⢏⢎⢎⢆⢄⠀⢑⣽⣿⢝⠲⠉⠀⠀⠀⠀    " + C.GRAY + "By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.WHITE + " (Volmit Software)\n" +
                    C.BLUE + "           ⡿⠂⠠⠀⡇⢇⠕⢈⣀⠀⠁⠡⠣⡣⡫⣂⣿⠯⢪⠰⠂⠀⠀⠀⠀    " + C.GRAY + "Java Version: " + C.DARK_RED + Adapt.getJavaVersion() + "\n" +
                    C.BLUE + "          ⡦⡙⡂⢀⢤⢣⠣⡈⣾⡃⠠⠄⠀⡄⢱⣌⣶⢏⢊⠂⠀⠀⠀⠀⠀     ⠀\n" +
                    C.BLUE + "          ⢝⡲⣜⡮⡏⢎⢌⢂⠙⠢⠐⢀⢘⢵⣽⣿⡿⠁⠁⠀⠀⠀⠀     ⠀⠀⠀\n" +
                    C.BLUE + "          ⠨⣺⡺⡕⡕⡱⡑⡆⡕⡅⡕⡜⡼⢽⡻⠏⠀⠀⠀⠀⠀⠀      ⠀⠀⠀⠀\n" +
                    C.BLUE + "          ⣼⣳⣫⣾⣵⣗⡵⡱⡡⢣⢑⢕⢜⢕⡝⠀⠀⠀⠀⠀⠀⠀      ⠀⠀⠀⠀\n" +
                    C.BLUE + "         ⣴⣿⣾⣿⣿⣿⡿⡽⡑⢌⠪⡢⡣⣣⡟⠀⠀⠀⠀⠀⠀⠀⠀      ⠀⠀No Splash Screen?\n" +
                    C.BLUE + "         ⡟⡾⣿⢿⢿⢵⣽⣾⣼⣘⢸⢸⣞⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀      ⠀⠀\n" +
                    C.BLUE + "          ⠁⠇⠡⠩⡫⢿⣝⡻⡮⣒⢽⠋⠀⠀⠀⠀",

            "\n  :::.   :::::::-.    :::.  ::::::::::. ::::::::::::     \n" +
                    "  ;;`;;   ;;,   `';,  ;;`;;  `;;;```.;;;;;;;;;;;''''       " + C.GRAY + "Version: " + C.DARK_RED + Adapt.instance.getDescription().getVersion() + "\n" +
                    " ,[[ '[[, `[[     [[ ,[[ '[[, `]]nnn]]'      [[            " + C.GRAY + "By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.WHITE + " (Volmit Software)\n" +
                    " $$$$$$$$  $$,    $$ $$$$$$$$  $$$\"\"         $$            " + C.GRAY + "Java Version: " + C.DARK_RED + Adapt.getJavaVersion() + "\n" +
                    " 888   888,888_,o8P' 888   888,888o          88,           \n" +
                    " YMM   \"\"` MMMMP\"`   YMM   \"\"` YMMMb         MMM      \n",

            C.GRAY + "\n ██░ ██ ▓█████  ██▓     ██▓███      ███▄ ▄███▓▓█████       \n" +
                    C.GRAY + "▓██░ ██▒▓█   ▀ ▓██▒    ▓██░  ██▒   ▓██▒▀█▀ ██▒▓█   ▀       " + C.DARK_RED + "Adapt     \n" +
                    C.GRAY + "▒██▀▀██░▒███   ▒██░    ▓██░ ██▓▒   ▓██    ▓██░▒███         " + C.GRAY + "Version: " + C.DARK_RED + Adapt.instance.getDescription().getVersion() + "     \n" +
                    C.GRAY + "░▓█ ░██ ▒▓█  ▄ ▒██░    ▒██▄█▓▒ ▒   ▒██    ▒██ ▒▓█  ▄       " + C.GRAY + "By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.WHITE + " (Volmit Software)\n" +
                    C.GRAY + "░▓█▒░██▓░▒████▒░██████▒▒██▒ ░  ░   ▒██▒   ░██▒░▒████▒      " + C.GRAY + "Java Version: " + C.DARK_RED + Adapt.getJavaVersion() + "     \n" +
                    C.GRAY + " ▒ ░░▒░▒░░ ▒░ ░░ ▒░▓  ░▒▓▒░ ░  ░   ░ ▒░   ░  ░░░ ▒░ ░      ",

            C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⣴⣶⣿⣿⣷⣶⣄⣀⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀\n" +
                    C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⣾⣿⣿⡿⢿⣿⣿⣿⣿⣿⣿⣿⣷⣦⡀⠀⠀⠀⠀   ⠀\n" +
                    C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⢀⣾⣿⣿⡟⠁⣰⣿⣿⣿⡿⠿⠻⠿⣿⣿⣿⣿⣧⠀  ⠀⠀⠀\n" +
                    C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⣾⣿⣿⠏⠀⣴⣿⣿⣿⠉⠀⠀⠀⠀⠀⠈⢻⣿⣿⣇⠀⠀⠀   \n" +
                    C.GRAY + "⠀⠀⠀⠀⢀⣠⣼⣿⣿⡏⠀⢠⣿⣿⣿⠇⠀⠀⠀⠀⠀⠀⠀⠈⣿⣿⣿⡀⠀   ⠀" + C.DARK_RED + "Adapt     \n" +
                    C.GRAY + "⠀⠀⠀⣰⣿⣿⣿⣿⣿⡇⠀⢸⣿⣿⣿⡀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⣿⡇⠀   ⠀" + C.GRAY + "Version: " + C.DARK_RED + Adapt.instance.getDescription().getVersion() + "     \n" +
                    C.GRAY + "⠀⠀⢰⣿⣿⡿⣿⣿⣿⡇⠀⠘⣿⣿⣿⣧⠀⠀⠀⠀⠀⠀⢀⣸⣿⣿⣿⠁⠀  ⠀" + C.GRAY + "By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.WHITE + " (Volmit Software)\n" +
                    C.GRAY + "⠀⠀⣿⣿⣿⠁⣿⣿⣿⡇⠀⠀⠻⣿⣿⣿⣷⣶⣶⣶⣶⣶⣿⣿⣿⣿⠃⠀ ⠀⠀" + C.GRAY + "Java Version: " + C.DARK_RED + Adapt.getJavaVersion() + "     \n" +
                    C.GRAY + "⠀⢰⣿⣿⡇⠀⣿⣿⣿⠀⠀⠀⠀⠈⠻⣿⣿⣿⣿⣿⣿⣿⣿⣿⠟⠁⠀⠀⠀  ⠀\n" +
                    C.GRAY + "⠀⢸⣿⣿⡇⠀⣿⣿⣿⠀⠀⠀⠀⠀⠀⠀⠉⠛⠛⠛⠉⢉⣿⣿⠀⠀⠀⠀⠀   ⠀\n" +
                    C.GRAY + "⠀⢸⣿⣿⣇⠀⣿⣿⣿⠀⠀⠀⠀⠀⢀⣤⣤⣤⡀⠀⠀⢸⣿⣿⣿⣷⣦⠀   ⠀⠀\n" +
                    C.GRAY + "⠀⠀⢻⣿⣿⣶⣿⣿⣿⠀⠀⠀⠀⠀⠈⠻⣿⣿⣿⣦⡀⠀⠉⠉⠻⣿⣿⡇⠀ ⠀ \n" +
                    C.GRAY + "⠀⠀⠀⠛⠿⣿⣿⣿⣿⣷⣤⡀⠀⠀⠀⠀⠈⠹⣿⣿⣇⣀⠀⣠⣾⣿⣿⡇⠀⠀       ⠀⠀" + C.DARK_RED + "sus\n  " +
                    C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⠹⣿⣿⣿⣿⣦⣤⣤⣤⣤⣾⣿⣿⣿⣿⣿⣿⣿⣿⡟⠀⠀  ⠀\n" +
                    C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠻⢿⣿⣿⣿⣿⣿⣿⠿⠋⠉⠛⠋⠉⠉⠁⠀⠀⠀   ⠀\n" +
                    C.GRAY + "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠉⠉⠉⠁"

    );

    public static String randomString7() {
        byte[] array = new byte[7]; // length is bounded by 7
        new Random().nextBytes(array);

        return new String(array, StandardCharsets.UTF_8);
    }
}
