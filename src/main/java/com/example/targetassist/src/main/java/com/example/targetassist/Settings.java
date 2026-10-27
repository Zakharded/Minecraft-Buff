package com.example.targetassist;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Settings {
    public static boolean healthIndicators = true, playerSight = true, nearbyList = false, tabHighlight = true;
    public static boolean lockOn = true, aimHead = false;
    public static boolean tPlayers = true, tZombies = true, tHostile = false, tPassive = false;
    public static double nearbyRange = 64, lockRange = 30, lockSpeed = 180;

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("targetassist.properties");

    public static void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        sync(p, false);
    }

    public static void save() {
        Properties p = new Properties();
        sync(p, true);
        try (Writer w = Files.newBufferedWriter(FILE)) {
            p.store(w, "Target Assist");
        } catch (IOException ignored) {
        }
    }

    private static void sync(Properties p, boolean saving) {
        for (Field f : Settings.class.getFields()) {
            String n = f.getName();
            try {
                if (saving) {
                    p.setProperty(n, String.valueOf(f.get(null)));
                } else if (p.containsKey(n)) {
                    if (f.getType() == boolean.class) f.setBoolean(null, Boolean.parseBoolean(p.getProperty(n)));
                    else if (f.getType() == double.class) f.setDouble(null, Double.parseDouble(p.getProperty(n)));
                }
            } catch (Exception ignored) {
            }
        }
    }
}
