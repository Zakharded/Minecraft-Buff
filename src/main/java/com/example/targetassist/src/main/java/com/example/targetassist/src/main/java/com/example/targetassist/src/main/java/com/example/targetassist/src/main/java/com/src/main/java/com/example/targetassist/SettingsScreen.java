package com.example.targetassist;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class SettingsScreen extends Screen {
    private KeyMapping listening;
    private Button listeningBtn;
    private String listeningName;

    public SettingsScreen() {
        super(Component.literal("Target Assist"));
    }

    @Override
    protected void init() {
        int L = width / 2 - 155, R = width / 2 + 5, y = 34, s = 22;

        // left column: display
        addRenderableWidget(toggle(L, y, "Health indicators", () -> Settings.healthIndicators, v -> Settings.healthIndicators = v));
        addRenderableWidget(toggle(L, y + s, "Player sight", () -> Settings.playerSight, v -> Settings.playerSight = v));
        addRenderableWidget(toggle(L, y + 2 * s, "Tab highlight", () -> Settings.tabHighlight, v -> Settings.tabHighlight = v));
        addRenderableWidget(toggle(L, y + 3 * s, "Side list", () -> Settings.nearbyList, v -> Settings.nearbyList = v));
        addRenderableWidget(new Slider(L, y + 4 * s, 150, 20, "Nearby range", 16, 128, Settings.nearbyRange, v -> Settings.nearbyRange = v));
        addRenderableWidget(keyButton(L, y + 5 * s, "Menu key", Keys.OPEN_GUI));

        // right column: lock-on
        addRenderableWidget(toggle(R, y, "Lock-on", () -> Settings.lockOn, v -> Settings.lockOn = v));
        addRenderableWidget(Button.builder(aimLabel(), b -> {
            Settings.aimHead = !Settings.aimHead;
            b.setMessage(aimLabel());
        }).bounds(R, y + s, 150, 20).build());
        addRenderableWidget(new Slider(R, y + 2 * s, 150, 20, "Lock range", 5, 100, Settings.lockRange, v -> Settings.lockRange = v));
        addRenderableWidget(new Slider(R, y + 3 * s, 150, 20, "Lock speed (deg/s)", 30, 720, Settings.lockSpeed, v -> Settings.lockSpeed = v));
        addRenderableWidget(keyButton(R, y + 4 * s, "Lock key (hold)", Keys.LOCK_ON));

        // targets
        addRenderableWidget(toggle(L, y + 6 * s, "Target players", () -> Settings.tPlayers, v -> Settings.tPlayers = v));
        addRenderableWidget(toggle(R, y + 6 * s, "Target zombies", () -> Settings.tZombies, v -> Settings.tZombies = v));
        addRenderableWidget(toggle(L, y + 7 * s, "Other hostile", () -> Settings.tHostile, v -> Settings.tHostile = v));
        addRenderableWidget(toggle(R, y + 7 * s, "Passive mobs", () -> Settings.tPassive, v -> Settings.tPassive = v));

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, y + 8 * s + 4, 200, 20).build());
    }

    private static Component aimLabel() {
        return Component.literal("Aim at: " + (Settings.aimHead ? "Head" : "Chest"));
    }

    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private Button toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).bounds(x, y, 150, 20).build();
    }

    private static Component keyLabel(String name, KeyMapping km) {
        return Component.literal(name + ": ").append(km.getTranslatedKeyMessage());
    }

    private Button keyButton(int x, int y, String name, KeyMapping km) {
        return Button.builder(keyLabel(name, km), b -> {
            listening = km;
            listeningBtn = b;
            listeningName = name;
            b.setMessage(Component.literal("> press a key <"));
        }).bounds(x, y, 150, 20).build();
    }

    private void finishListening() {
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        listeningBtn.setMessage(keyLabel(listeningName, listening));
        listening = null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listening != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) listening.setKey(InputConstants.getKey(key, scan));
            finishListening();
            return true;
        }
        if (Keys.OPEN_GUI.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (listening != null) {
            listening.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            finishListening();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        Settings.save();
    }

    private static class Slider extends AbstractSliderButton {
        private final String label;
        private final double min, max;
        private final DoubleConsumer set;

        Slider(int x, int y, int w, int h, String label, double min, double max, double cur, DoubleConsumer set) {
            super(x, y, w, h, Component.empty(), (cur - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.set = set;
            updateMessage();
        }

        private double val() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + Math.round(val())));
        }

        @Override
        protected void applyValue() {
            set.accept(val());
        }
    }
}
