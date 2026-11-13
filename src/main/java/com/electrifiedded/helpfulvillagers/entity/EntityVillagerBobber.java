package com.electrifiedded.helpfulvillagers.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * A fishing bobber cast by a villager. Vanilla's hook needs a player angler, so this is a small
 * stand-in: it flies to the target, floats, dips when a fish bites, and is reeled in by the fisherman.
 */
public class EntityVillagerBobber extends Entity {

    private static final DataParameter<Integer> ANGLER = EntityDataManager.createKey(EntityVillagerBobber.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> BITE = EntityDataManager.createKey(EntityVillagerBobber.class, DataSerializers.BOOLEAN);

    private double tx, ty, tz;
    private int flight;
    private int life;

    public EntityVillagerBobber(World world) {
        super(world);
        setSize(0.25F, 0.25F);
        noClip = true;
    }

    public EntityVillagerBobber(World world, EntityHelpfulVillager angler, double tx, double ty, double tz) {
        this(world);
        dataManager.set(ANGLER, angler.getEntityId());
        setPosition(angler.posX, angler.posY + angler.getEyeHeight(), angler.posZ);
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    @Override
    protected void entityInit() {
        dataManager.register(ANGLER, -1);
        dataManager.register(BITE, false);
    }

    public Entity getAngler() {
        int id = dataManager.get(ANGLER);
        return id < 0 ? null : world.getEntityByID(id);
    }

    public boolean hasBite() {
        return dataManager.get(BITE);
    }

    public void setBite(boolean bite) {
        dataManager.set(BITE, bite);
        if (bite && world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.WATER_BUBBLE, posX, posY + 0.2, posZ, 8, 0.1, 0, 0.1, 0.1);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.WATER_WAKE, posX, posY + 0.2, posZ, 8, 0.1, 0, 0.1, 0.05);
            playSound(net.minecraft.init.SoundEvents.ENTITY_BOBBER_SPLASH, 0.25F, 1.0F);
        }
    }

    public boolean landed() {
        return flight >= 12;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) return;
        Entity angler = getAngler();
        if (angler == null || !angler.isEntityAlive() || getDistanceSq(angler) > 32 * 32 || ++life > 20 * 120) {
            setDead();
            return;
        }
        if (flight < 12) {
            flight++;
            double f = flight / 12.0;
            double arc = Math.sin(f * Math.PI) * 1.5;
            setPosition(posX + (tx - posX) * f, posY + (ty - posY) * f + arc * 0.15, posZ + (tz - posZ) * f);
            if (flight == 12) playSound(net.minecraft.init.SoundEvents.ENTITY_BOBBER_SPLASH, 0.15F, 1.4F);
        } else {
            double bob = hasBite() ? -0.25 : MathHelper.sin(life * 0.15F) * 0.04;
            setPosition(tx, ty + bob, tz);
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        setDead(); // bobbers are not worth saving
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {}

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }
}
