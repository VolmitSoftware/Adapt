/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.adapt.util.data;

import java.awt.*;

public class TinyColor {
    private final int r;
    private final int g;
    private final int b;

    public TinyColor(int color) {
        this.r = (color >> 16) & 0xFF;
        this.g = (color >> 8) & 0xFF;
        this.b = color & 0xFF;
    }

    public TinyColor(Color color) {
        this(color.getRGB());
    }

    public TinyColor(String c) {
        this(Color.decode(c));
    }

    public TinyColor(org.bukkit.Color c) {
        this(c.asRGB());
    }

    public TinyColor(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public TinyColor(double h, double s, double b) {
        this(Color.getHSBColor((float) h, (float) s, (float) b));
    }

    public TinyColor(float h, float s, float b) {
        this(Color.getHSBColor(h, s, b));
    }

    public TinyColor brightness(float brightness) {
        return new TinyColor(getHue(), getSaturation(), brightness);
    }

    public TinyColor saturation(float saturation) {
        return new TinyColor(getHue(), saturation, getBrightness());
    }

    public TinyColor hue(float hue) {
        return new TinyColor(hue, getSaturation(), getBrightness());
    }

    public float getHue() {
        return Color.RGBtoHSB(r, g, b, null)[0];
    }

    public float getSaturation() {
        return Color.RGBtoHSB(r, g, b, null)[1];
    }

    public float getBrightness() {
        return Color.RGBtoHSB(r, g, b, null)[2];
    }

    public TinyColor spin(int amount) {
        int h = Math.round(getHue() * 360);
        h = (h + amount) % 360;
        return hue((float) h / 360.0f);
    }

    public int toRGB() {
        return new Color(r, g, b).getRGB();
    }

    public Color getColor() {
        return new Color(r, g, b);
    }

    public org.bukkit.Color getBukkitColor() {
        return org.bukkit.Color.fromRGB(r, g, b);
    }

    public String toHex() {
        return String.format("#%02x%02x%02x", r, g, b);
    }

    public String toHex(boolean hash) {
        if (hash) {
            return toHex();
        }

        return String.format("%02x%02x%02x", r, g, b);
    }

    public TinyColor copy() {
        return new TinyColor(r, g, b);
    }

    public TinyColor compliment() {
        return spin(180);
    }

    private float fclamp(float f) {
        return Math.max((float) 0.0, Math.min((float) 1.0, f));
    }

    private int iclamp(int i, int min, int max) {
        return Math.max(min, Math.min(max, i));
    }

    public TinyColor saturate(int amount) {
        return saturation(fclamp(getSaturation() + ((float) amount / 100.0f)));
    }

    public TinyColor desaturate(int amount) {
        return saturate(-amount);
    }

    public TinyColor brighten(int amount) {
        return brightness(fclamp(getBrightness() + ((float) amount / 100.0f)));
    }

    public TinyColor darken(int amount) {
        return brighten(-amount);
    }
}
