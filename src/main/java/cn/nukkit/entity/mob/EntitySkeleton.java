package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author PikyCZ
 */
public class EntitySkeleton extends EntityMob {

    public static final int NETWORK_ID = 34;

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    public EntitySkeleton(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        this.setMaxHealth(20);
    }

    @Override
    public float getWidth() {
        return 0.65f;
    }

    @Override
    public float getHeight() {
        return 1.8f;
    }

    @Override
    public String getName() {
        return "Skeleton";
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
        pk.metadata = this.dataProperties;
        player.dataPacket(pk);

        // Equip Skeleton with Bow in hand
        MobEquipmentPacket epk = new MobEquipmentPacket();
        epk.eid = this.getId();
        epk.item = Item.get(Item.BOW);
        epk.slot = 0;
        epk.selectedSlot = 0;
        player.dataPacket(epk);

        super.spawnTo(player);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        boolean hasUpdate = super.onUpdate(currentTick);

        // Sunlight burning logic
        if (this.isAlive() && currentTick % 20 == 0) {
            int time = this.level.getTime() % Level.TIME_FULL;
            boolean isDay = (time < 12500 || time > 23500);
            if (isDay && !this.level.isRaining() && !this.level.isThundering()) {
                if (!this.isInsideOfWater() && this.level.canBlockSeeSky(this)) {
                    this.setOnFire(8);
                }
            }
        }

        return hasUpdate;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();
        int bones = ThreadLocalRandom.current().nextInt(0, 3);
        if (bones > 0) drops.add(Item.get(Item.BONE, 0, bones));
        int arrows = ThreadLocalRandom.current().nextInt(0, 3);
        if (arrows > 0) drops.add(Item.get(Item.ARROW, 0, arrows));
        if (ThreadLocalRandom.current().nextInt(100) < 5) {
            drops.add(Item.get(Item.BOW));
        }
        return drops.toArray(new Item[0]);
    }
}
