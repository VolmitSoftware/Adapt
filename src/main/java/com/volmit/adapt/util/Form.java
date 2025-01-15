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

package com.volmit.adapt.util;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Form {
    private static final String[] NAMES = new String[]{"Thousand", "Million", "Billion", "Trillion", "Quadrillion", "Quintillion", "Sextillion", "Septillion", "Octillion", "Nonillion", "Decillion", "Undecillion", "Duodecillion", "Tredecillion", "Quattuordecillion", "Quindecillion", "Sexdecillion", "Septendecillion", "Octodecillion", "Novemdecillion", "Vigintillion",};
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);
    private static final NavigableMap<BigInteger, String> MAP;
    private static final LinkedHashMap<String, Integer> roman_numerals = new LinkedHashMap<>();
    private static NumberFormat NF;
    private static DecimalFormat DF;

    static {
        MAP = new TreeMap<>();
        for (int i = 0; i < NAMES.length; i++) {
            MAP.put(THOUSAND.pow(i + 1), NAMES[i]);
        }

        roman_numerals.put("M", 1000);
        roman_numerals.put("CM", 900);
        roman_numerals.put("D", 500);
        roman_numerals.put("CD", 400);
        roman_numerals.put("C", 100);
        roman_numerals.put("XC", 90);
        roman_numerals.put("L", 50);
        roman_numerals.put("XL", 40);
        roman_numerals.put("X", 10);
        roman_numerals.put("IX", 9);
        roman_numerals.put("V", 5);
        roman_numerals.put("IV", 4);
        roman_numerals.put("I", 1);
    }

    private static void instantiate() {
        if (NF == null) {
            NF = NumberFormat.getInstance(Locale.US);
        }
    }

    /**
     * Capitalize the first letter
     *
     * @param s the string
     * @return the capitalized string
     */
    public static String capitalize(String s) {
        StringBuilder roll = new StringBuilder();
        boolean f = true;

        for (Character i : s.trim().toCharArray()) {
            if (f) {
                roll.append(Character.toUpperCase(i));
                f = false;
            } else {
                roll.append(i);
            }
        }

        return roll.toString();
    }

    /**
     * Capitalize all words in the string
     *
     * @param s the string
     * @return the capitalized string
     */
    public static String capitalizeWords(String s) {
        StringBuilder rollx = new StringBuilder();

        for (String i : s.trim().split(" ")) {
            rollx.append(" ").append(capitalize(i.trim()));
        }

        return rollx.substring(1);
    }

    /**
     * Soft Word wrap
     *
     * @param s   the string
     * @param len the length to wrap
     * @return the wrapped string
     */
    public static String wrapWords(String s, int len) {
        return wrap(s, len, null, true);
    }

    public static String wrapWordsPrefixed(String s, String prefix, int len) {
        return wrapPrefixed(s, prefix, len, null, true);
    }

    /**
     * Wrap words
     *
     * @param s          the string
     * @param len        the wrap length
     * @param newLineSep the new line seperator
     * @param soft       should it be soft wrapped or hard wrapped?
     * @return the wrapped words
     */
    public static String wrap(String s, int len, String newLineSep, boolean soft) {
        return wrap(s, len, newLineSep, soft, " ");
    }

    public static String wrapPrefixed(String s, String pref, int len, String newLineSep, boolean soft) {
        return pref + wrapPrefixed(s, len, newLineSep, soft, " ").replaceAll("\\Q\n\\E", "\n" + pref);
    }

    /**
     * Wrap words
     *
     * @param s          the string
     * @param len        the length
     * @param newLineSep the new line seperator
     * @param soft       soft or hard wrapping
     * @param regex      the regex
     * @return the wrapped string
     */
    public static String wrap(String s, int len, String newLineSep, boolean soft, String regex) {
        if (s == null) {
            return null;
        } else {
            if (newLineSep == null) {
                newLineSep = "\n";
            }

            if (len < 1) {
                len = 1;
            }

            if (regex.trim().isEmpty()) {
                regex = " ";
            }

            Pattern arg4 = Pattern.compile(regex);
            int arg5 = s.length();
            int arg6 = 0;
            StringBuilder arg7 = new StringBuilder(arg5 + 32);

            while (arg6 < arg5) {
                int arg8 = -1;
                Matcher arg9 = arg4.matcher(s.substring(arg6, Math.min(arg6 + len + 1, arg5)));
                if (arg9.find()) {
                    if (arg9.start() == 0) {
                        arg6 += arg9.end();
                        continue;
                    }

                    arg8 = arg9.start();
                }

                if (arg5 - arg6 <= len) {
                    break;
                }

                while (arg9.find()) {
                    arg8 = arg9.start() + arg6;
                }

                if (arg8 >= arg6) {
                    arg7.append(s, arg6, arg8);
                    arg7.append(newLineSep);
                    arg6 = arg8 + 1;
                } else if (soft) {
                    arg7.append(s, arg6, len + arg6);
                    arg7.append(newLineSep);
                    arg6 += len;
                } else {
                    arg9 = arg4.matcher(s.substring(arg6 + len));
                    if (arg9.find()) {
                        arg8 = arg9.start() + arg6 + len;
                    }

                    if (arg8 >= 0) {
                        arg7.append(s, arg6, arg8);
                        arg7.append(newLineSep);
                        arg6 = arg8 + 1;
                    } else {
                        arg7.append(s.substring(arg6));
                        arg6 = arg5;
                    }
                }
            }

            arg7.append(s.substring(arg6));
            return arg7.toString();
        }
    }

    public static String wrapPrefixed(String s, int len, String newLineSep, boolean soft, String regex) {
        if (s == null) {
            return null;
        } else {
            if (newLineSep == null) {
                newLineSep = "\n";
            }

            if (len < 1) {
                len = 1;
            }

            if (regex.trim().isEmpty()) {
                regex = " ";
            }

            Pattern arg4 = Pattern.compile(regex);
            int arg5 = s.length();
            int arg6 = 0;
            StringBuilder arg7 = new StringBuilder(arg5 + 32);

            while (arg6 < arg5) {
                int arg8 = -1;
                Matcher arg9 = arg4.matcher(s.substring(arg6, Math.min(arg6 + len + 1, arg5)));
                if (arg9.find()) {
                    if (arg9.start() == 0) {
                        arg6 += arg9.end();
                        continue;
                    }

                    arg8 = arg9.start();
                }

                if (arg5 - arg6 <= len) {
                    break;
                }

                while (arg9.find()) {
                    arg8 = arg9.start() + arg6;
                }

                if (arg8 >= arg6) {
                    arg7.append(s, arg6, arg8);
                    arg7.append(newLineSep);
                    arg6 = arg8 + 1;
                } else if (soft) {
                    arg7.append(s, arg6, len + arg6);
                    arg7.append(newLineSep);
                    arg6 += len;
                } else {
                    arg9 = arg4.matcher(s.substring(arg6 + len));
                    if (arg9.find()) {
                        arg8 = arg9.start() + arg6 + len;
                    }

                    if (arg8 >= 0) {
                        arg7.append(s, arg6, arg8);
                        arg7.append(newLineSep);
                        arg6 = arg8 + 1;
                    } else {
                        arg7.append(s.substring(arg6));
                        arg6 = arg5;
                    }
                }
            }

            arg7.append(s.substring(arg6));
            return arg7.toString();
        }
    }


    /**
     * Get a high accuracy but limited range duration (accurate up to a couple of
     * minutes)
     *
     * @param ms   the milliseconds (double)
     * @param prec the precision (decimal format)
     * @return the formatted string
     */
    public static String duration(double ms, int prec) {
        if (ms < 1000.0) {
            return Form.f(ms, prec) + "ms";
        }

        if (ms / 1000.0 < 60.0) {
            return Form.f(ms / 1000.0, prec) + "s";
        }

        if (ms / 1000.0 / 60.0 < 60.0) {
            return Form.f(ms / 1000.0 / 60.0, prec) + "m";
        }

        return getString(ms, prec, Form.f(ms, prec));
    }

    @NotNull
    private static String getString(double ms, int prec, String f) {
        if (ms / 1000.0 / 60.0 / 60.0 < 24.0) {
            return Form.f(ms / 1000.0 / 60.0 / 60.0, prec) + " hours";
        }

        if (ms / 1000.0 / 60.0 / 60.0 / 24.0 < 7) {
            return Form.f(ms / 1000.0 / 60.0 / 24.0, prec) + " days";
        }

        return f + "ms";
    }

    public static String duration(long ms) {
        return duration(ms, 0);
    }

    /**
     * Get a duration from milliseconds up to days
     *
     * @param ms   the ms
     * @param prec the precision (decimal format)
     * @return the formatted string
     */
    public static String duration(long ms, int prec) {
        if (ms < 1000.0) {
            return Form.f(ms, prec) + "ms";
        }

        if (ms / 1000.0 < 60.0) {
            return Form.f(ms / 1000.0, prec) + " seconds";
        }

        if (ms / 1000.0 / 60.0 < 60.0) {
            return Form.f(ms / 1000.0 / 60.0, prec) + " minutes";
        }

        return getString(ms, prec, Form.f(ms, prec));
    }

    /**
     * Format a big value
     *
     * @param i the number
     * @return the full value in string
     */
    public static String b(int i) {
        return b(new BigInteger(String.valueOf(i)));
    }

    /**
     * Format a big value
     *
     * @param i the number
     * @return the full value in string
     */
    public static String b(long i) {
        return b(new BigInteger(String.valueOf(i)));
    }

    /**
     * Format a big value
     *
     * @param i the number
     * @return the full value in string
     */
    public static String b(double i) {
        return b(new BigInteger(String.valueOf((long) i)));
    }

    /**
     * Format a big number
     *
     * @param number the big number
     * @return the value in string
     */
    public static String b(BigInteger number) {
        Entry<BigInteger, String> entry = MAP.floorEntry(number);
        if (entry == null) {
            return "Nearly nothing";
        }

        BigInteger key = entry.getKey();
        BigInteger d = key.divide(THOUSAND);
        BigInteger m = number.divide(d);
        float f = m.floatValue() / 1000.0f;
        float rounded = ((int) (f * 100.0)) / 100.0f;

        if (rounded % 1 == 0) {
            return ((int) rounded) + " " + entry.getValue();
        }

        return rounded + " " + entry.getValue();
    }

    /**
     * ":", "a", "b", "c" -> a:b:c
     *
     * @param splitter the splitter that goes in between
     * @param strings  the strings
     * @return the result
     */
    public static String split(String splitter, String... strings) {
        StringBuilder b = new StringBuilder();

        for (String i : strings) {
            b.append(splitter);
            b.append(i);
        }

        return b.substring(splitter.length());
    }

    /**
     * Trim a string to a length, then append ... at the end if it extends the limit
     *
     * @param s the string
     * @param l the limit
     * @return the modified string
     */
    public static String trim(String s, int l) {
        if (s.length() <= l) {
            return s;
        }

        return s.substring(0, l) + "...";
    }

    /**
     * Format a long. Changes -10334 into -10,334
     *
     * @param i the number
     * @return the string representation of the number
     */
    public static String f(long i) {
        instantiate();
        return NF.format(i);
    }

    /**
     * Format a number. Changes -10334 into -10,334
     *
     * @param i the number
     * @return the string representation of the number
     */
    public static String f(int i) {
        instantiate();
        return NF.format(i);
    }

    /**
     * Formats a double's decimals to a limit
     *
     * @param i the double
     * @param p the number of decimal places to use
     * @return the formated string
     */
    public static String f(double i, int p) {
        String form = "#";

        if (p > 0) {
            form = form + "." + repeat("#", p);
        }

        DF = new DecimalFormat(form);

        return DF.format(i);
    }

    /**
     * Formats a float's decimals to a limit
     *
     * @param i the float
     * @param p the number of decimal places to use
     * @return the formated string
     */
    public static String f(float i, int p) {
        String form = "#";

        if (p > 0) {
            form = form + "." + repeat("#", p);
        }

        DF = new DecimalFormat(form);

        return DF.format(i);
    }

    /**
     * Formats a double's decimals (one decimal point)
     *
     * @param i the double
     */
    public static String f(double i) {
        return f(i, 1);
    }

    /**
     * Formats a float's decimals (one decimal point)
     *
     * @param i the float
     */
    public static String f(float i) {
        return f(i, 1);
    }

    /**
     * Get a percent representation of a double and decimal places (0.53) would
     * return 53%
     *
     * @param i the double
     * @param p the number of decimal points
     * @return a string
     */
    public static String pc(double i, int p) {
        return f(i * 100.0, p) + "%";
    }

    /**
     * Get a percent representation of a float and decimal places (0.53) would
     * return 53%
     *
     * @param i the float
     * @param p the number of decimal points
     * @return a string
     */
    public static String pc(float i, int p) {
        return f(i * 100, p) + "%";
    }

    /**
     * Get a percent representation of a double and zero decimal places (0.53) would
     * return 53%
     *
     * @param i the double
     * @return a string
     */
    public static String pc(double i) {
        return f(i * 100, 0) + "%";
    }

    /**
     * Get a percent representation of a float and zero decimal places (0.53) would
     * return 53%
     *
     * @param i the double
     * @return a string
     */
    public static String pc(float i) {
        return f(i * 100, 0) + "%";
    }

    /**
     * Get a percent as the percent of I out of "of" with custom decimal places
     *
     * @param i  the percent out of
     * @param of of
     * @param p  the decimal places
     * @return the string
     */
    public static String pc(int i, int of, int p) {
        return f(100.0 * (((double) i) / ((double) of)), p) + "%";
    }

    /**
     * Get a percent as the percent of I out of "of"
     *
     * @param i  the percent out of
     * @param of of
     * @return the string
     */
    public static String pc(int i, int of) {
        return pc(i, of, 0);
    }

    /**
     * Get a percent as the percent of I out of "of" with custom decimal places
     *
     * @param i  the percent out of
     * @param of of
     * @param p  the decimal places
     * @return the string
     */
    public static String pc(long i, long of, int p) {
        return f(100.0 * (((double) i) / ((double) of)), p) + "%";
    }

    /**
     * Get a percent as the percent of I out of "of"
     *
     * @param i  the percent out of
     * @param of of
     * @return the string
     */
    public static String pc(long i, long of) {
        return pc(i, of, 0);
    }

    /**
     * Get roman numeral representation of the int
     *
     * @param num the int
     * @return the numerals
     */
    public static String toRoman(int num) {

        StringBuilder res = new StringBuilder();

        for (Map.Entry<String, Integer> entry : roman_numerals.entrySet()) {
            int matches = num / entry.getValue();

            res.append(repeat(entry.getKey(), matches));
            num = num % entry.getValue();
        }

        return res.toString();
    }

    /**
     * Repeat a string
     *
     * @param s the string
     * @param n the amount of times to repeat
     * @return the repeated string
     */
    public static String repeat(String s, int n) {
        if (s == null) {
            return null;
        }

        return s.repeat(Math.max(0, n));
    }
}
