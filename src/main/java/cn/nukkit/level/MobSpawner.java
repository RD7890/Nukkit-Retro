package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLiquid;
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
 * Handles accurate vanilla-style natural mob spawning for Nukkit-Retro.
 */
public class MobSpawner {

    private static final int SPAWN_INTERVAL = 80; // Run check every 4 seconds (80 ticks)
    private static final int MAX_ANIMALS_PER_LEVEL = 30;
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

        // Count current animals and monsters, and despawn far entities
        int animalCount = 0;
        int monsterCount = 0;

        for (Entity entity : level.getEntities()) {
            if (entity instanceof EntityAnimal) {
                animalCount++;
            } else if (entity instanceof EntityMob) {
                monsterCount++;
            }

            // Despawn rule: monsters > 72 blocks, animals > 96 blocks from all players
            if (entity instanceof EntityAnimal || entity instanceof EntityMob) {
                double maxDistSq = (entity instanceof EntityMob) ? 5184.0 : 9216.0; // 72^2 or 96^2
                boolean nearPlayer = false;
                for (Player p : players.values()) {
                    if (p.isAlive() && p.distanceSquared(entity) < maxDistSq) {
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
        // Night is between 13000 (dusk) and 23000 (dawn)
        boolean isNight = (time >= 13000 && time <= 23000);

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (Player player : players.values()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            // Decide whether to attempt animal or monster spawning
            boolean tryAnimal = spawnAnimals && animalCount < MAX_ANIMALS_PER_LEVEL && !isNight;
            boolean tryMonster = spawnMobs && monsterCount < MAX_MONSTERS_PER_LEVEL;

            if (!tryAnimal && !tryMonster) {
                continue;
            }

            // Annular spawn area: strictly between 24 and 54 blocks from player
            double angle = rand.nextDouble(0, Math.PI * 2);
            double dist = rand.nextDouble(24.0, 54.0);
            int spawnX = (int) (player.x + Math.cos(angle) * dist);
            int spawnZ = (int) (player.z + Math.sin(angle) * dist);

            // Double check Euclidean distance to all players >= 24 blocks
            boolean tooClose = false;
            for (Player p : players.values()) {
                double dx = p.x - spawnX;
                double dz = p.z - spawnZ;
                if ((dx * dx + dz * dz) < 576.0) { // 24^2
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }

            int chunkX = spawnX >> 4;
            int chunkZ = spawnZ >> 4;
            FullChunk chunk = level.getChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                continue;
            }

            int highestY = level.getHighestBlockAt(spawnX, spawnZ);
            if (highestY <= 2 || highestY >= 126) {
                continue;
            }

            // 1. Attempt daytime animal spawning on surface
            if (tryAnimal) {
                Vector3 spawnPos = new Vector3(spawnX, highestY, spawnZ);
                Block floorBlock = level.getBlock(new Vector3(spawnX, highestY - 1, spawnZ));
                Block spawnBlock = level.getBlock(spawnPos);
                Block aboveBlock = level.getBlock(new Vector3(spawnX, highestY + 1, spawnZ));

                // Animals require grass block on surface with open sky
                if (floorBlock.getId() == Block.GRASS
                        && !spawnBlock.isSolid() && !(spawnBlock instanceof BlockLiquid)
                        && !aboveBlock.isSolid() && !(aboveBlock instanceof BlockLiquid)
                        && level.canBlockSeeSky(spawnPos)) {

                    String mobType = ANIMAL_TYPES[rand.nextInt(ANIMAL_TYPES.length)];
                    int packSize = rand.nextInt(2, 5); // Pack of 2-4
                    for (int i = 0; i < packSize && animalCount < MAX_ANIMALS_PER_LEVEL; i++) {
                        double ox = (i == 0) ? 0 : rand.nextDouble(-2.0, 2.0);
                        double oz = (i == 0) ? 0 : rand.nextDouble(-2.0, 2.0);
                        spawnEntity(mobType, chunk, spawnX + ox + 0.5, highestY, spawnZ + oz + 0.5);
                        animalCount++;
                    }
                    continue;
                }
            }

            // 2. Attempt monster spawning (Surface at NIGHT, or Underground Caves DAY/NIGHT)
            if (tryMonster) {
                // If it's night, we can attempt a surface spawn
                if (isNight) {
                    Vector3 spawnPos = new Vector3(spawnX, highestY, spawnZ);
                    Block floorBlock = level.getBlock(new Vector3(spawnX, highestY - 1, spawnZ));
                    Block spawnBlock = level.getBlock(spawnPos);
                    Block aboveBlock = level.getBlock(new Vector3(spawnX, highestY + 1, spawnZ));

                    // Surface check at night: floor is solid, not liquid, block light <= 7 (no torches/lamps)
                    if (floorBlock.isSolid() && !(floorBlock instanceof BlockLiquid)
                            && !spawnBlock.isSolid() && !(spawnBlock instanceof BlockLiquid)
                            && !aboveBlock.isSolid() && !(aboveBlock instanceof BlockLiquid)) {

                        int blockLight = level.getBlockLightAt(spawnX, highestY, spawnZ);
                        if (blockLight <= 7) {
                            String mobType = MONSTER_TYPES[rand.nextInt(MONSTER_TYPES.length)];
                            int packSize = rand.nextInt(1, 4); // Pack of 1-3
                            for (int i = 0; i < packSize && monsterCount < MAX_MONSTERS_PER_LEVEL; i++) {
                                double ox = (i == 0) ? 0 : rand.nextDouble(-1.5, 1.5);
                                double oz = (i == 0) ? 0 : rand.nextDouble(-1.5, 1.5);
                                spawnEntity(mobType, chunk, spawnX + ox + 0.5, highestY, spawnZ + oz + 0.5);
                                monsterCount++;
                            }
                            continue;
                        }
                    }
                }

                // Underground Cave spawn attempt (valid during BOTH day and night)
                if (highestY > 12) {
                    int searchStartY = rand.nextInt(8, Math.max(10, highestY - 3));
                    int caveY = -1;
                    for (int y = searchStartY; y >= 6; y--) {
                        Block floor = level.getBlock(new Vector3(spawnX, y - 1, spawnZ));
                        Block air1 = level.getBlock(new Vector3(spawnX, y, spawnZ));
                        Block air2 = level.getBlock(new Vector3(spawnX, y + 1, spawnZ));

                        if (floor.isSolid() && !(floor instanceof BlockLiquid)
                                && !air1.isSolid() && !(air1 instanceof BlockLiquid)
                                && !air2.isSolid() && !(air2 instanceof BlockLiquid)) {
                            caveY = y;
                            break;
                        }
                    }

                    if (caveY != -1) {
                        Vector3 cavePos = new Vector3(spawnX, caveY, spawnZ);
                        // Must be in a cave (cannot see sky)
                        if (!level.canBlockSeeSky(cavePos)) {
                            int blockLight = level.getBlockLightAt(spawnX, caveY, spawnZ);
                            int skyLight = level.getBlockSkyLightAt(spawnX, caveY, spawnZ) - level.calculateSkylightSubtracted(1);
                            int totalLight = Math.max(blockLight, skyLight);

                            if (totalLight <= 7) {
                                String mobType = MONSTER_TYPES[rand.nextInt(MONSTER_TYPES.length)];
                                int packSize = rand.nextInt(1, 3);
                                for (int i = 0; i < packSize && monsterCount < MAX_MONSTERS_PER_LEVEL; i++) {
                                    double ox = (i == 0) ? 0 : rand.nextDouble(-1.5, 1.5);
                                    double oz = (i == 0) ? 0 : rand.nextDouble(-1.5, 1.5);
                                    spawnEntity(mobType, chunk, spawnX + ox + 0.5, caveY, spawnZ + oz + 0.5);
                                    monsterCount++;
                                }
                            }
                        }
                    }
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
