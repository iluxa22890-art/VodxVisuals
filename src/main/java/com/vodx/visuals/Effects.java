package com.vodx.visuals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;

public final class Effects {
    private static final class Ring {
        final double x, y, z;
        int age;

        Ring(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final List<Ring> rings = new ArrayList<>();
    private static LivingEntity target;
    private static boolean killed;
    private static int sinceHit;
    private static boolean wasGround = true;
    private static int ticks;

    private Effects() { }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && entity instanceof LivingEntity le) onAttack(le);
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(Effects::tick);
    }

    private static DustParticleEffect dust(int argb, float size) {
        return new DustParticleEffect(Colors.rgb(argb), size);
    }

    private static void onAttack(LivingEntity e) {
        HudRenderer.onHit(e);
        target = e;
        killed = false;
        sinceHit = 0;
        if (Modules.HIT_PARTICLES.enabled) hitBurst(e);
        if (Modules.HIT_SOUND.enabled) playHitSound();
    }

    private static void playHitSound() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        SoundEvent s = switch (Modules.HIT_SOUND_TYPE.index) {
            case 1 -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            case 2 -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
            default -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
        p.playSound(s, 0.7f, 1.2f);
    }

    private static void hitBurst(LivingEntity e) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Modules.HIT_AMOUNT.i();
        for (int i = 0; i < n; i++) {
            double x = e.getX() + r.nextGaussian() * 0.3;
            double y = e.getY() + r.nextDouble() * e.getHeight();
            double z = e.getZ() + r.nextGaussian() * 0.3;
            ParticleEffect pe;
            switch (Modules.HIT_STYLE.index) {
                case 1 -> pe = dust(Colors.accent(i * 20), 1.0f);
                case 2 -> pe = ParticleTypes.END_ROD;
                default -> pe = ParticleTypes.CRIT;
            }
            w.addParticle(pe, x, y, z, r.nextGaussian() * 0.1, r.nextDouble() * 0.15, r.nextGaussian() * 0.1);
        }
    }

    private static void killBurst(LivingEntity e) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        for (double h = 0; h < 3.0; h += 0.2) {
            w.addParticle(ParticleTypes.END_ROD, x, y + h, z, 0, 0.03, 0);
        }
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36.0;
            w.addParticle(dust(Colors.accent(i * 10), 1.2f),
                    x + Math.cos(a) * 0.6, y + 0.1, z + Math.sin(a) * 0.6,
                    Math.cos(a) * 0.12, 0.05, Math.sin(a) * 0.12);
        }
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null) return;
        ticks++;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // kill effect
        if (target != null) {
            sinceHit++;
            if (!killed && Modules.KILL_EFFECT.enabled && (target.isDead() || target.getHealth() <= 0f)) {
                killBurst(target);
                killed = true;
            }
            if (killed || sinceHit > 100) target = null;
        }

        // jump circle
        boolean ground = p.isOnGround();
        if (Modules.JUMP_CIRCLE.enabled && wasGround && !ground && p.getVelocity().y > 0.1) {
            rings.add(new Ring(p.getX(), p.getY(), p.getZ()));
        }
        wasGround = ground;
        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            ring.age++;
            double radius = ring.age * Modules.JUMP_SPEED.value * 3.0;
            for (int k = 0; k < 18; k++) {
                double a = Math.PI * 2 * k / 18.0 + ring.age * 0.2;
                w.addParticle(dust(Colors.accent(ring.age * 8 + k * 6), 0.9f),
                        ring.x + Math.cos(a) * radius, ring.y + 0.05, ring.z + Math.sin(a) * radius, 0, 0, 0);
            }
            if (ring.age > 16) it.remove();
        }

        // trail
        if (Modules.TRAIL.enabled && (!ground || p.getVelocity().horizontalLengthSquared() > 0.0025)) {
            for (int i = 0; i < Modules.TRAIL_DENSITY.i(); i++) {
                w.addParticle(dust(Colors.accent(ticks * 5 + i * 30), 0.8f),
                        p.getX() + r.nextGaussian() * 0.15, p.getY() + 0.1 + r.nextDouble() * 0.2,
                        p.getZ() + r.nextGaussian() * 0.15, 0, 0.01, 0);
            }
        }

        // china hat
        if (Modules.CHINA_HAT.enabled && !mc.options.getPerspective().isFirstPerson()) {
            double top = p.getY() + p.getHeight() + 0.15;
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8.0 + ticks * 0.15;
                for (double rad : new double[] { 0.2, 0.5 }) {
                    w.addParticle(dust(Colors.accent(ticks * 4 + k * 20), 0.55f),
                            p.getX() + Math.cos(a) * rad, top + (0.5 - rad) * 0.5, p.getZ() + Math.sin(a) * rad, 0, 0, 0);
                }
            }
            w.addParticle(dust(Colors.accent(ticks * 4), 0.55f), p.getX(), top + 0.25, p.getZ(), 0, 0, 0);
        }

        // ambient
        if (Modules.AMBIENT.enabled && ticks % 2 == 0) {
            w.addParticle(dust(Colors.accent(ticks), 0.6f),
                    p.getX() + (r.nextDouble() - 0.5) * 12, p.getY() + r.nextDouble() * 4,
                    p.getZ() + (r.nextDouble() - 0.5) * 12, 0, 0.02, 0);
        }
    }
}
