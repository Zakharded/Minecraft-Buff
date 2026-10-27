package com.example.targetassist;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TargetAssistMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static final long REACTION_MS = 150;  // pause after acquiring a target before the aim starts moving
    private static final double MAX_ANGLE = 60;   // only targets within this many degrees of your crosshair

    private static final Set<Integer> glowing = new HashSet<>();
    private static LivingEntity target;
    private static final Map<UUID, Component> tabOriginal = new HashMap<>();
    private static final Map<UUID, Component> tabApplied = new HashMap<>();
    private static long acquiredAt, lastNanos;

    // ---------- per tick: GUI key + player sight ----------
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            glowing.clear();
            tabOriginal.clear();
            tabApplied.clear();
            return;
        }
        updateTabList(mc);
        while (Keys.OPEN_GUI.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SettingsScreen());
        }
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (Settings.playerSight) {
                p.setGlowingTag(true);
                glowing.add(p.getId());
            } else if (glowing.remove(p.getId())) {
                p.setGlowingTag(p.hasEffect(MobEffects.GLOWING));
            }
        }
    }

    // ---------- tab list: gold name for nearby players ----------
    private static void updateTabList(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null || mc.level == null) return;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            Player other = mc.level.getPlayerByUUID(id);
            boolean near = Settings.tabHighlight && other != null && other != mc.player
                    && mc.player.distanceTo(other) <= Settings.nearbyRange;
            if (near) {
                Component current = info.getTabListDisplayName();
                if (current == null || current != tabApplied.get(id)) {
                    tabOriginal.put(id, current);  // remember what the server had set (may be null)
                    Component gold = Component.literal(current != null ? current.getString() : info.getProfile().getName())
                            .withStyle(ChatFormatting.GOLD);
                    tabApplied.put(id, gold);
                    info.setTabListDisplayName(gold);
                }
            } else if (tabApplied.remove(id) != null) {
                info.setTabListDisplayName(tabOriginal.remove(id));
            }
        }
    }

    // ---------- per frame: smooth lock-on ----------
    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        double dt = Math.min((now - lastNanos) / 1e9, 0.1);
        lastNanos = now;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.screen != null || !Settings.lockOn || !Keys.LOCK_ON.isDown()) {
            target = null;
            return;
        }
        if (target == null || !valid(p, target)) {
            target = pickTarget(p);
            acquiredAt = System.currentTimeMillis();
            if (target == null) return;
        }
        if (System.currentTimeMillis() - acquiredAt < REACTION_MS) return;

        Vec3 eye = p.getEyePosition();
        Vec3 aim = aimPoint(target);
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

        float step = (float) (Settings.lockSpeed * dt);  // max degrees to turn this frame
        float yaw = p.getYRot() + Mth.clamp(Mth.wrapDegrees(wantYaw - p.getYRot()), -step, step);
        float pitch = Mth.clamp(p.getXRot() + Mth.clamp(wantPitch - p.getXRot(), -step, step), -90f, 90f);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    private static boolean wanted(LocalPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player) return Settings.tPlayers;
        if (e instanceof Zombie) return Settings.tZombies;
        if (e instanceof Enemy) return Settings.tHostile;
        return Settings.tPassive && e instanceof Mob;
    }

    private static boolean valid(LocalPlayer p, LivingEntity e) {
        return wanted(p, e) && p.distanceTo(e) <= Settings.lockRange && p.hasLineOfSight(e);
    }

    private static Vec3 aimPoint(LivingEntity e) {
        if (Settings.aimHead) return e.getEyePosition();
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.65, e.getZ());
    }

    private static LivingEntity pickTarget(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double bestDot = Math.cos(Math.toRadians(MAX_ANGLE));
        LivingEntity best = null;
        List<LivingEntity> nearby = p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(Settings.lockRange), e -> wanted(p, e));
        for (LivingEntity e : nearby) {
            if (!valid(p, e)) continue;
            double dot = look.dot(aimPoint(e).subtract(eye).normalize());
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    // ---------- health indicators ----------
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent e) {
        if (!Settings.healthIndicators) return;
        Entity en = e.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || en == mc.player) return;
        if (!(en instanceof LivingEntity le) || !(en instanceof Player || en instanceof Mob)) return;
        if (mc.player.distanceTo(en) > 64) return;
        e.setContent(Component.empty()
                .append(e.getContent())
                .append(Component.literal(String.format(" ♥ %.1f", le.getHealth())).withStyle(ChatFormatting.RED)));
        e.setResult(Event.Result.ALLOW);
    }

    // ---------- nearby players list (top right) ----------
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type() || !Settings.nearbyList) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        List<AbstractClientPlayer> list = mc.level.players().stream()
                .filter(o -> o != mc.player && mc.player.distanceTo(o) <= Settings.nearbyRange)
                .sorted(Comparator.comparingDouble(o -> mc.player.distanceTo(o)))
                .limit(8)
                .toList();
        if (list.isEmpty()) return;

        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int w = e.getWindow().getGuiScaledWidth();
        int y = 4;
        String header = "Nearby players";
        g.drawString(f, header, w - 4 - f.width(header), y, 0xFFFF55, true);
        y += 10;
        for (AbstractClientPlayer o : list) {
            String s = o.getName().getString() + " " + Math.round(mc.player.distanceTo(o)) + "m";
            g.drawString(f, s, w - 4 - f.width(s), y, 0xFFFFFF, true);
            y += 10;
        }
    }
}
