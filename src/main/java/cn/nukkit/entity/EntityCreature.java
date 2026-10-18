package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntityMob;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.concurrent.ThreadLocalRandom;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public abstract class EntityCreature extends EntityLiving {

    protected int wanderTicks = 0;
    protected double targetYaw = 0;

    public EntityCreature(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (!this.isAlive()) {
            return super.onUpdate(currentTick);
        }

        int tickDiff = currentTick - this.lastUpdate;
        if (tickDiff <= 0) {
            return false;
        }

        this.lastUpdate = currentTick;
        boolean hasUpdate = this.entityBaseTick(tickDiff);

        // Apply gravity
        if (!this.onGround) {
            this.motionY -= this.getGravity();
        } else if (this.motionY < 0) {
            this.motionY = 0;
        }

        double moveSpeed = 0.0;

        // If it's a monster, seek nearest survival player within 16 blocks
        Player targetPlayer = null;
        if (this instanceof EntityMob) {
            double nearestDistSq = 256.0; // 16 blocks
            for (Player p : this.level.getPlayers().values()) {
                if (p.isAlive() && !p.closed && !p.isSpectator() && !p.isCreative()) {
                    double d = p.distanceSquared(this);
                    if (d < nearestDistSq) {
                        nearestDistSq = d;
                        targetPlayer = p;
                    }
                }
            }
        }

        if (targetPlayer != null) {
            double dx = targetPlayer.x - this.x;
            double dz = targetPlayer.z - this.z;
            this.targetYaw = Math.toDegrees(-Math.atan2(dx, dz));
            moveSpeed = 0.12; // Chase speed
        } else {
            // Natural idle wandering
            if (this.wanderTicks <= 0) {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    this.wanderTicks = ThreadLocalRandom.current().nextInt(40, 80);
                    this.targetYaw = ThreadLocalRandom.current().nextDouble(0, 360);
                } else {
                    this.wanderTicks = ThreadLocalRandom.current().nextInt(25, 55); // Pause
                }
            } else {
                this.wanderTicks -= tickDiff;
                if (this.wanderTicks > 10) {
                    moveSpeed = 0.09; // Idle wander speed
                }
            }
        }

        // Smooth yaw rotation
        double yawDiff = this.targetYaw - this.yaw;
        while (yawDiff < -180) yawDiff += 360;
        while (yawDiff >= 180) yawDiff -= 360;
        this.yaw += yawDiff * 0.25;

        if (moveSpeed > 0.0) {
            double rad = Math.toRadians(this.yaw);
            double mx = -Math.sin(rad) * moveSpeed;
            double mz = Math.cos(rad) * moveSpeed;

            // Step up / jump over block in front
            if (this.onGround && this.isCollidedHorizontally) {
                this.motionY = 0.42;
            }

            this.move(mx, this.motionY, mz);
            hasUpdate = true;
        } else if (!this.onGround) {
            this.move(0, this.motionY, 0);
            hasUpdate = true;
        }

        this.updateMovement();
        return hasUpdate;
    }
}
