package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.entity.mob.EntityZombiePigman;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.HeartParticle;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.SetEntityLinkPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: BeYkeRYkt, Enhanced for Nukkit-Retro
 * Nukkit Project
 */
public class EntityPig extends EntityAnimal implements EntityRideable {

    public static final int NETWORK_ID = 12;

    private int wanderTicks = 0;
    private int panicTicks = 0;
    private double targetYaw = 0;

    public EntityPig(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.45f;
        }
        return 0.9f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.45f;
        }
        return 0.9f;
    }

    @Override
    public float getEyeHeight() {
        if (this.isBaby()) {
            return 0.45f;
        }
        return 0.9f;
    }

    @Override
    public void initEntity() {
        super.initEntity();
        this.setMaxHealth(10);

        if (this.namedTag.contains("Saddle")) {
            this.setSaddled(this.namedTag.getBoolean("Saddle"));
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("Saddle", this.isSaddled());
    }

    public boolean isSaddled() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_SADDLED);
    }

    public void setSaddled(boolean saddled) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SADDLED, saddled);
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Pig";
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();
        int count = ThreadLocalRandom.current().nextInt(1, 4);
        int porkId = this.isOnFire() ? Item.COOKED_PORKCHOP : Item.RAW_PORKCHOP;
        drops.add(Item.get(porkId, 0, count));

        if (this.isSaddled()) {
            drops.add(Item.get(Item.SADDLE, 0, 1));
        }

        return drops.toArray(new Item[0]);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public boolean isBreedingItem(Item item) {
        if (item == null) {
            return false;
        }
        int id = item.getId();
        return id == Item.CARROT || id == Item.POTATO || id == Item.BEETROOT || id == Item.CARROT_ON_A_STICK;
    }

    @Override
    public boolean onInteract(Player player, Item item) {
        // 1. Saddling the Pig
        if (item != null && item.getId() == Item.SADDLE && !this.isSaddled() && !this.isBaby()) {
            this.setSaddled(true);
            if (!player.isCreative()) {
                item.count--;
                player.getInventory().setItemInHand(item);
            }
            this.level.addParticle(new HeartParticle(this.add(0, this.getHeight() + 0.2, 0)));
            return true;
        }

        // 2. Feeding / Breeding
        if (this.isBreedingItem(item) && item.getId() != Item.CARROT_ON_A_STICK) {
            if (!player.isCreative()) {
                item.count--;
                player.getInventory().setItemInHand(item);
            }
            if (this.isBaby()) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_BABY, false);
            } else if (this.getHealth() < this.getMaxHealth()) {
                this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + 2));
            }
            this.level.addParticle(new HeartParticle(this.add(0, this.getHeight() + 0.2, 0)));
            return true;
        }

        // 3. Mounting saddled adult pig
        if (this.isSaddled() && !this.isBaby() && this.linkedEntity == null) {
            this.mountEntity(player);
            return true;
        }

        return super.onInteract(player, item);
    }

    public boolean mountEntity(Entity entity) {
        Objects.requireNonNull(entity, "The target of the mounting entity can't be null");
        if (entity.riding != null) {
            SetEntityLinkPacket pk = new SetEntityLinkPacket();
            pk.rider = this.getId();
            pk.riding = entity.getId();
            pk.type = SetEntityLinkPacket.TYPE_REMOVE;
            Server.broadcastPacket(this.hasSpawned.values(), pk);

            if (entity instanceof Player) {
                ((Player) entity).dataPacket(pk);
            }

            entity.riding = null;
            this.linkedEntity = null;
            entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
            return true;
        }

        SetEntityLinkPacket pk = new SetEntityLinkPacket();
        pk.rider = this.getId();
        pk.riding = entity.getId();
        pk.type = SetEntityLinkPacket.TYPE_RIDE;
        Server.broadcastPacket(this.hasSpawned.values(), pk);

        if (entity instanceof Player) {
            pk = new SetEntityLinkPacket();
            pk.rider = this.getId();
            pk.riding = 0;
            pk.type = SetEntityLinkPacket.TYPE_RIDE;
            ((Player) entity).dataPacket(pk);
        }

        entity.riding = this;
        this.linkedEntity = entity;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
        return true;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        boolean result = super.attack(source);
        if (result) {
            this.panicTicks = 60; // Run around panicked for 3 seconds
        }
        return result;
    }

    @Override
    public void onStruckByLightning(Entity entity) {
        super.onStruckByLightning(entity);

        EntityZombiePigman pigman = new EntityZombiePigman(this.chunk, this.namedTag);
        pigman.spawnToAll();

        this.close();
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;
        if (tickDiff <= 0) {
            return false;
        }

        this.lastUpdate = currentTick;
        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.isAlive()) {
            // Apply gravity
            if (!this.onGround) {
                this.motionY -= this.getGravity();
            } else if (this.motionY < 0) {
                this.motionY = 0;
            }

            // Case A: Pig is being ridden by a player
            if (this.linkedEntity instanceof Player) {
                Player rider = (Player) this.linkedEntity;
                if (!rider.isAlive() || rider.closed || rider.riding != this) {
                    this.mountEntity(rider); // Dismount
                } else {
                    this.yaw = rider.yaw;
                    this.pitch = rider.pitch;

                    double speed = 0.08;
                    Item held = rider.getInventory().getItemInHand();
                    if (held != null && held.getId() == Item.CARROT_ON_A_STICK) {
                        speed = 0.22; // Boost with carrot on a stick
                    }

                    double rad = Math.toRadians(this.yaw);
                    double mx = -Math.sin(rad) * speed;
                    double mz = Math.cos(rad) * speed;

                    // Jump over 1-block obstacles if on ground
                    if (this.onGround && this.isCollidedHorizontally) {
                        this.motionY = 0.42;
                    }

                    this.move(mx, this.motionY, mz);
                    this.updateMovement();
                    return true;
                }
            }

            // Case B: Autonomous AI (Following food, panicking, or wandering)
            double moveSpeed = 0.0;

            if (this.panicTicks > 0) {
                this.panicTicks -= tickDiff;
                moveSpeed = 0.18;
                if (ThreadLocalRandom.current().nextInt(10) == 0) {
                    this.targetYaw = ThreadLocalRandom.current().nextDouble(0, 360);
                }
            } else {
                // Find nearby player holding tempting food
                Player nearestPlayer = null;
                double nearestDistSq = 64.0; // 8 blocks

                for (Player p : this.level.getPlayers().values()) {
                    if (p.isAlive() && !p.closed) {
                        double d = p.distanceSquared(this);
                        if (d < nearestDistSq) {
                            Item inHand = p.getInventory().getItemInHand();
                            if (inHand != null && this.isBreedingItem(inHand)) {
                                nearestPlayer = p;
                                nearestDistSq = d;
                            }
                        }
                    }
                }

                if (nearestPlayer != null) {
                    double dx = nearestPlayer.x - this.x;
                    double dz = nearestPlayer.z - this.z;
                    this.targetYaw = Math.toDegrees(-Math.atan2(dx, dz));

                    if (nearestDistSq > 4.0) { // Keep 2-block distance
                        moveSpeed = 0.11;
                    }
                } else {
                    // Idle wander
                    if (this.wanderTicks <= 0) {
                        if (ThreadLocalRandom.current().nextInt(3) == 0) {
                            this.wanderTicks = ThreadLocalRandom.current().nextInt(40, 80);
                            this.targetYaw = ThreadLocalRandom.current().nextDouble(0, 360);
                        } else {
                            this.wanderTicks = ThreadLocalRandom.current().nextInt(60, 120); // Pause
                        }
                    } else {
                        this.wanderTicks -= tickDiff;
                        if (this.wanderTicks > 20) {
                            moveSpeed = 0.06;
                        }
                    }
                }
            }

            // Smooth yaw rotation
            double yawDiff = this.targetYaw - this.yaw;
            while (yawDiff < -180) yawDiff += 360;
            while (yawDiff >= 180) yawDiff -= 360;
            this.yaw += yawDiff * 0.2;

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
        }

        return hasUpdate;
    }

    @Override
    public void spawnTo(Player player) {
        AddEntityPacket pk = new AddEntityPacket();
        pk.type = this.getNetworkId();
        pk.entityUniqueId = this.getId();
        pk.entityRuntimeId = this.getId();
        pk.x = (float) this.x;
        pk.y = (float) this.y;
        pk.z = (float) this.z;
        pk.speedX = (float) this.motionX;
        pk.speedY = (float) this.motionY;
        pk.speedZ = (float) this.motionZ;
        pk.yaw = (float) this.yaw;
        pk.pitch = (float) this.pitch;
        pk.metadata = this.dataProperties;
        player.dataPacket(pk);

        super.spawnTo(player);
    }
}
