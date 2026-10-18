package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityMob;
import cn.nukkit.entity.passive.EntityAnimal;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles natural entity spawning for Nukkit-Retro.
 */
public class MobSpawner {

    private static final int SPAWN_INTERVAL = 100; // Run check every 5 seconds (100 ticks)
    private static final int MAX_ANIMALS_PER_LEVEL = 40;
    private static final int MAX_MONSTERS_PER_LEVEL = 35;

    private static final String[] ANIMAL_TYPES = new String[]{
            "Pig", "Cow", "Sheep", "Chicken"
    };

    private static final String[] MONSTER_TYPES = new String[]{
            "Zombie", "Skeleton", "Spider", "Creeper"
    };

    public void tick(Level level, int currentTick) {
        if (currentTick % SPAWN_INTERVAL != 0) {
            return;
        }

        Map<Long, Player> players = level.getPlayers();
        if (players.isEmpty()) {
            return;
        }

        Server server = Server.getInstance();
        boolean spawnAnimals = server.getPropertyBoolean("spawn-animals", true);
        boolean spawnMobs = server.getPropertyBoolean("spawn-mobs", true);
        if (!spawnAnimals && !spawnMobs) {
            return;
        }

        int animalCount = 0;
        int monsterCount = 0;

        for (Entity entity : level.getEntities()) {
            if (entity instanceof EntityAnimal) {
                animalCount++;
            } else if (entity instanceof EntityMob) {
                monsterCount++;
            }

            // Despawn far creatures (> 100 blocks from all alive players)
            if (entity instanceof EntityAnimal || entity instanceof EntityMob) {
                boolean nearPlayer = false;
                for (Player p : players.values()) {
                    if (p.isAlive() && p.distanceSquared(entity) < 10000.0) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (!nearPlayer) {
                    entity.close();
                }
            }
        }

        int time = level.getTime() % Level.TIME_FULL;
        boolean isNight = (time >= Level.TIME_NIGHT && time <= Level.TIME_SUNRISE);

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (Player player : players.values()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            boolean tryAnimal = spawnAnimals && animalCount < MAX_ANIMALS_PER_LEVEL && !isNight;
            boolean tryMonster = spawnMobs && monsterCount < MAX_MONSTERS_PER_LEVEL;

            if (!tryAnimal && !tryMonster) {
                continue;
            }

            // Random position between 24 and 54 blocks from player
            double angle = rand.nextDouble(0, Math.PI * 2);
            double dist = rand.nextDouble(24, 54);
            int spawnX = (int) (player.x + Math.cos(angle) * dist);
            int spawnZ = (int) (player.z + Math.sin(angle) * dist);

            int chunkX = spawnX >> 4;
            int chunkZ = spawnZ >> 4;
            FullChunk chunk = level.getChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                continue;
            }

            int spawnY = level.getHighestBlockAt(spawnX, spawnZ);
            if (spawnY <= 1 || spawnY >= 126) {
                continue;
            }

            Block floorBlock = level.getBlock(new Vector3(spawnX, spawnY - 1, spawnZ));
            Block spawnBlock = level.getBlock(new Vector3(spawnX, spawnY, spawnZ));
            Block aboveBlock = level.getBlock(new Vector3(spawnX, spawnY + 1, spawnZ));

            // Must have air/passable space to stand
            if (spawnBlock.isSolid() || aboveBlock.isSolid()) {
                continue;
            }

            // Avoid liquids
            if (floorBlock.getId() == Block.WATER || floorBlock.getId() == Block.STILL_WATER ||
                floorBlock.getId() == Block.LAVA || floorBlock.getId() == Block.STILL_LAVA) {
                continue;
            }

            if (tryAnimal && floorBlock.getId() == Block.GRASS) {
                String mobType = ANIMAL_TYPES[rand.nextInt(ANIMAL_TYPES.length)];
                int count = rand.nextInt(1, 4); // 1-3 animals per herd
                for (int i = 0; i < count && animalCount < MAX_ANIMALS_PER_LEVEL; i++) {
                    double ox = (i == 0) ? 0 : rand.nextDouble(-2, 2);
                    double oz = (i == 0) ? 0 : rand.nextDouble(-2, 2);
                    spawnEntity(mobType, chunk, spawnX + ox + 0.5, spawnY, spawnZ + oz + 0.5);
                    animalCount++;
                }
            } else if (tryMonster && floorBlock.isSolid()) {
                int light = level.getBlockLightAt(spawnX, spawnY, spawnZ);
                int skyLight = level.getBlockSkyLightAt(spawnX, spawnY, spawnZ) - level.calculateSkylightSubtracted(1);
                int actualLight = Math.max(light, skyLight);

                if (isNight || actualLight <= 7) {
                    String mobType = MONSTER_TYPES[rand.nextInt(MONSTER_TYPES.length)];
                    spawnEntity(mobType, chunk, spawnX + 0.5, spawnY, spawnZ + 0.5);
                    monsterCount++;
                }
            }
        }
    }

    private void spawnEntity(String type, FullChunk chunk, double x, double y, double z) {
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", x))
                        .add(new DoubleTag("", y))
                        .add(new DoubleTag("", z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", ThreadLocalRandom.current().nextFloat() * 360))
                        .add(new FloatTag("", 0)));

        Entity entity = Entity.createEntity(type, chunk, nbt);
        if (entity != null) {
            entity.spawnToAll();
        }
    }
}
