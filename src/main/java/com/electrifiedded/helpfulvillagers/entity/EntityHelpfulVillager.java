package com.electrifiedded.helpfulvillagers.entity;

import com.electrifiedded.helpfulvillagers.HelpfulVillagers;
import com.electrifiedded.helpfulvillagers.Messages;
import com.electrifiedded.helpfulvillagers.ai.AIFollowLeader;
import com.electrifiedded.helpfulvillagers.ai.AIGuardMelee;
import com.electrifiedded.helpfulvillagers.ai.AIGuardRanged;
import com.electrifiedded.helpfulvillagers.ai.AIGuardTarget;
import com.electrifiedded.helpfulvillagers.ai.AIMate;
import com.electrifiedded.helpfulvillagers.ai.AIWork;
import com.electrifiedded.helpfulvillagers.ai.CraftTask;
import com.electrifiedded.helpfulvillagers.ai.job.Job;
import com.electrifiedded.helpfulvillagers.craft.RecipeIndex;
import com.electrifiedded.helpfulvillagers.network.Net;
import com.electrifiedded.helpfulvillagers.village.GuildHall;
import com.electrifiedded.helpfulvillagers.village.InventoryUtil;
import com.electrifiedded.helpfulvillagers.village.VillageManager;
import com.electrifiedded.helpfulvillagers.village.VillageRecord;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAIMoveIndoors;
import net.minecraft.entity.ai.EntityAIOpenDoor;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAIWatchClosest2;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A villager that works. Unlike the original (one entity class per job, swapped on every job change)
 * this is one entity whose job is a synced value; behaviour lives in a {@link Job} object.
 */
public class EntityHelpfulVillager extends EntityVillager {

    private static final DataParameter<Integer> JOB = EntityDataManager.createKey(EntityHelpfulVillager.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> LEADER = EntityDataManager.createKey(EntityHelpfulVillager.class, DataSerializers.VARINT);

    public final VillagerInventory inventory = new VillagerInventory(this);
    /** items this villager was taught to craft in addition to its job's list */
    public final List<ItemStack> taught = new ArrayList<>();

    private Job job;
    private VillageRecord village;
    private GuildHall hall;
    private BlockPos savedHallDoor;
    private Entity leader;
    private UUID leaderId;
    public CraftTask craftTask;
    private NBTTagCompound pendingJobData;
    private NBTTagCompound pendingCraftData;
    private int pickupRadius = 1;
    private boolean wasDay;

    public EntityHelpfulVillager(World world) {
        super(world);
        setCanPickUpLoot(false);
        stepHeight = 1.0F;
        ((PathNavigateGround) getNavigator()).setBreakDoors(true);
        ((PathNavigateGround) getNavigator()).setEnterDoors(true);
        setDropChance(net.minecraft.inventory.EntityEquipmentSlot.MAINHAND, 2.0F);
        for (net.minecraft.inventory.EntityEquipmentSlot s : VillagerInventory.EQUIPMENT) setDropChance(s, 2.0F);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(JOB, 0);
        dataManager.register(LEADER, -1);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
    }

    @Override
    protected void initEntityAI() {
        // runs from the Entity constructor: no field access here
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new AvoidZombies(this));
        // cornered (no flee spot for AvoidZombies) or hit by anything else: run instead of standing there
        tasks.addTask(1, new net.minecraft.entity.ai.EntityAIPanic(this, 0.75D) {
            @Override
            public boolean shouldExecute() {
                return !getJob().isGuard() && super.shouldExecute();
            }
        });
        tasks.addTask(2, new AIGuardRanged(this));
        tasks.addTask(2, new AIGuardMelee(this));
        tasks.addTask(3, new AIFollowLeader(this));
        tasks.addTask(4, new AIWork(this));
        tasks.addTask(5, new AIMate(this));
        tasks.addTask(6, new EntityAIMoveIndoors(this));
        tasks.addTask(7, new StayInAtNight(this));
        tasks.addTask(8, new EntityAIOpenDoor(this, true));
        tasks.addTask(9, new EntityAIWatchClosest2(this, EntityPlayer.class, 3.0F, 1.0F));
        tasks.addTask(10, new EntityAIWanderAvoidWater(this, 0.6D));
        tasks.addTask(11, new EntityAIWatchClosest(this, EntityLivingBase.class, 8.0F));
        targetTasks.addTask(1, new GuardHurtBy(this));
        targetTasks.addTask(2, new AIGuardTarget(this));
    }

    /** Non-guards run from zombies like vanilla villagers; guards stand and fight. */
    private static final class AvoidZombies extends EntityAIAvoidEntity<EntityZombie> {
        private final EntityHelpfulVillager v;

        AvoidZombies(EntityHelpfulVillager v) {
            super(v, EntityZombie.class, 8.0F, 0.6D, 0.6D);
            this.v = v;
        }

        @Override
        public boolean shouldExecute() {
            return !v.getJob().isGuard() && super.shouldExecute();
        }
    }

    /** Vanilla's night-time door lock, except while on an errand (crafting, following, guarding). */
    private static final class StayInAtNight extends EntityAIRestrictOpenDoor {
        private final EntityHelpfulVillager v;

        StayInAtNight(EntityHelpfulVillager v) {
            super(v);
            this.v = v;
        }

        private boolean busy() {
            return v.craftTask != null || v.hasLeader() || v.getJob().isGuard();
        }

        @Override
        public boolean shouldExecute() {
            return !busy() && super.shouldExecute();
        }

        @Override
        public boolean shouldContinueExecuting() {
            return !busy() && super.shouldContinueExecuting();
        }
    }

    private static final class GuardHurtBy extends EntityAIHurtByTarget {
        private final EntityHelpfulVillager v;

        GuardHurtBy(EntityHelpfulVillager v) {
            super(v, true);
            this.v = v;
        }

        @Override
        public boolean shouldExecute() {
            return v.getJob().isGuard() && !(v.getRevengeTarget() instanceof EntityPlayer) && super.shouldExecute();
        }
    }

    // --- job ------------------------------------------------------------------------------------------

    public Profession getJob() {
        return Profession.byId(dataManager.get(JOB));
    }

    public void setJob(Profession p) {
        if (p == getJob()) return;
        if (job != null) job.stop();
        if (craftTask != null) craftTask.abort();
        dataManager.set(JOB, p.ordinal());
        job = null;
        hall = null;
        savedHallDoor = null;
        if (!world.isRemote) {
            // a new job means the old tool goes into the bag
            ItemStack held = getHeldItemMainhand();
            if (!held.isEmpty() && !p.isTool(held)) {
                setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                inventory.addOrDrop(held);
            }
            equipToolFromInventory();
        }
    }

    public Job job() {
        if (job == null || job.profession() != getJob()) {
            job = Job.create(this, getJob());
            if (pendingJobData != null) {
                job.read(pendingJobData);
                pendingJobData = null;
            }
        }
        return job;
    }

    public boolean hasTool() {
        return getJob().isTool(getHeldItemMainhand());
    }

    /** Moves a valid tool from storage into the hand if the hand has none. */
    public boolean equipToolFromInventory() {
        if (hasTool()) return true;
        int slot = inventory.find(getJob()::isTool);
        if (slot < 0) return false;
        inventory.equipTool(slot);
        return true;
    }

    public List<ItemStack> knownCraftables() {
        Set<String> seen = new LinkedHashSet<>();
        List<ItemStack> out = new ArrayList<>();
        for (net.minecraft.item.Item item : getJob().craftables) {
            for (ItemStack s : RecipeIndex.outputsOf(item)) if (seen.add(RecipeIndex.key(s))) out.add(s);
        }
        for (ItemStack t : taught) if (seen.add(RecipeIndex.key(t))) out.add(t.copy());
        return out;
    }

    public boolean canCraft(ItemStack stack) {
        if (getJob().craftables.contains(stack.getItem())) return true;
        for (ItemStack t : taught) if (InventoryUtil.same(t, stack)) return true;
        return false;
    }

    // --- village and hall -----------------------------------------------------------------------------

    public VillageRecord village() {
        return village;
    }

    public GuildHall hall() {
        return hall;
    }

    private void refreshHome() {
        VillageManager vm = VillageManager.get(world);
        BlockPos pos = new BlockPos(this);
        VillageRecord found = vm.find(world, pos);
        if (found == null && village != null && village.isInside(posX, posY, posZ)) found = village;
        village = found;
        if (village == null) { hall = null; return; }
        Profession p = getJob();
        if (!p.needsHall()) { hall = null; return; }
        GuildHall h = null;
        if (hall != null) h = village.hallAt(p, hall.door);
        if (h == null && savedHallDoor != null) h = village.hallAt(p, savedHallDoor);
        if (h == null) h = village.nearestHall(p, pos);
        hall = h;
        if (h != null) savedHallDoor = h.door;
    }

    // --- leader ---------------------------------------------------------------------------------------

    public Entity getLeader() {
        if (world.isRemote) {
            int id = dataManager.get(LEADER);
            return id < 0 ? null : world.getEntityByID(id);
        }
        return leader;
    }

    public boolean hasLeader() {
        return world.isRemote ? dataManager.get(LEADER) >= 0 : leader != null;
    }

    public void setLeader(Entity e) {
        leader = e;
        leaderId = e == null ? null : e.getUniqueID();
        dataManager.set(LEADER, e == null ? -1 : e.getEntityId());
        getNavigator().clearPath();
    }

    private void refreshLeader() {
        if (leader != null && (!leader.isEntityAlive() || leader.world != world)) {
            setLeader(null);
        }
        if (leader == null && leaderId != null && world instanceof net.minecraft.world.WorldServer) {
            Entity e = ((net.minecraft.world.WorldServer) world).getEntityFromUuid(leaderId);
            if (e != null && e.isEntityAlive()) setLeader(e);
        }
    }

    // --- ticking --------------------------------------------------------------------------------------

    @Override
    public void onLivingUpdate() {
        updateArmSwingProgress();
        super.onLivingUpdate();
        if (world.isRemote) return;
        if (ticksExisted % 40 == 1 || (village == null && ticksExisted % 10 == 0)) refreshHome();
        if (ticksExisted % 20 == 7) refreshLeader();
        if (ticksExisted % 10 == 3) unstick();
        EntityLivingBase target = getAttackTarget();
        if (target != null && (!target.isEntityAlive() || target.world != world || getDistanceSq(target) > 48 * 48)) setAttackTarget(null);
        if (ticksExisted % 5 == 0) pickUpItems();
        if (ticksExisted % 100 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
        if (ticksExisted % 60 == 13) {
            equipToolFromInventory();
            if (getJob().isGuard()) inventory.equipBestArmor();
        }
        boolean day = world.isDaytime();
        if (day && !wasDay) job().onNewDay();
        wasDay = day;
        if (pendingCraftData != null && village != null) {
            craftTask = CraftTask.restore(this, pendingCraftData);
            pendingCraftData = null;
        }
    }

    private double stuckX, stuckY, stuckZ;
    private int stuckSteps;

    /**
     * Movement watchdog (every 10 ticks): has a path but is not moving. First opens doors around, hops and
     * slides to the middle of its cell (corner / door-frame wedges), then steps to the next path point.
     */
    private void unstick() {
        net.minecraft.pathfinding.Path path = getNavigator().getPath();
        double dx = posX - stuckX, dy = posY - stuckY, dz = posZ - stuckZ;
        stuckX = posX;
        stuckY = posY;
        stuckZ = posZ;
        if (path == null || path.isFinished() || isRiding() || dx * dx + dy * dy + dz * dz > 0.04) {
            stuckSteps = 0;
            return;
        }
        stuckSteps++;
        if (stuckSteps == 2 || stuckSteps == 4) {
            BlockPos feet = new BlockPos(this);
            openDoor(feet);
            for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) openDoor(feet.offset(f));
            motionX += (feet.getX() + 0.5 - posX) * 0.3;
            motionZ += (feet.getZ() + 0.5 - posZ) * 0.3;
            if (onGround) getJumpHelper().setJumping();
        } else if (stuckSteps >= 7) {
            stuckSteps = 0;
            Vec3d next = path.getVectorFromIndex(this, path.getCurrentPathIndex());
            if (getDistanceSq(next.x, next.y, next.z) < 0.25 && path.getCurrentPathIndex() + 1 < path.getCurrentPathLength()) {
                next = path.getVectorFromIndex(this, path.getCurrentPathIndex() + 1);
            }
            AxisAlignedBB to = getEntityBoundingBox().offset(next.x - posX, next.y - posY, next.z - posZ);
            if (getDistanceSq(next.x, next.y, next.z) < 9 && world.getCollisionBoxes(this, to).isEmpty() && !world.containsAnyLiquid(to)) {
                setPositionAndUpdate(next.x, next.y, next.z);
            } else {
                getNavigator().clearPath();
            }
        }
    }

    private void openDoor(BlockPos p) {
        net.minecraft.block.state.IBlockState s = world.getBlockState(p);
        if (s.getBlock() instanceof net.minecraft.block.BlockDoor && s.getMaterial() == net.minecraft.block.material.Material.WOOD
                && !net.minecraft.block.BlockDoor.isOpen(world, p)) {
            ((net.minecraft.block.BlockDoor) s.getBlock()).toggleDoor(world, p, true);
        }
    }

    public void setPickupRadius(int r) {
        pickupRadius = r;
    }

    private void pickUpItems() {
        if (isChild() || !isEntityAlive()) return;
        AxisAlignedBB box = getEntityBoundingBox().grow(pickupRadius, 1, pickupRadius);
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, box)) {
            if (item.isDead || item.cannotPickup()) continue;
            ItemStack stack = item.getItem();
            ItemStack rest = inventory.add(stack);
            if (rest.getCount() != stack.getCount()) {
                onItemPickup(item, stack.getCount() - rest.getCount());
                if (rest.isEmpty()) item.setDead();
                else item.setItem(rest);
            }
        }
    }

    public void onInventoryChanged() {}

    public void swing() {
        swingArm(EnumHand.MAIN_HAND);
    }

    /** Damages the held tool by one; breaks it like a player would. */
    public void damageTool(int amount) {
        ItemStack held = getHeldItemMainhand();
        if (held.isEmpty() || !held.isItemStackDamageable()) return;
        held.damageItem(amount, this);
        if (held.isEmpty()) setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** For a solid target (chest, workbench...) the nearest free cell next to it; otherwise the position itself. */
    public BlockPos standSpot(BlockPos pos) {
        if (!world.getBlockState(pos).getMaterial().blocksMovement()) return pos;
        BlockPos best = pos;
        double bd = Double.MAX_VALUE;
        for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos n = pos.offset(f).up(dy);
                if (world.getBlockState(n).getMaterial().blocksMovement() || world.getBlockState(n.up()).getMaterial().blocksMovement()) continue;
                if (!world.getBlockState(n.down()).getMaterial().blocksMovement()) continue;
                double d = getDistanceSqToCenter(n);
                if (d < bd) { bd = d; best = n; }
            }
        }
        return best;
    }

    public boolean moveTo(BlockPos pos, double speed) {
        if (getNavigator().tryMoveToXYZ(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed)) return true;
        Vec3d towards = net.minecraft.entity.ai.RandomPositionGenerator.findRandomTargetBlockTowards(this, 12, 6,
                new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        return towards != null && getNavigator().tryMoveToXYZ(towards.x, towards.y, towards.z, speed);
    }

    public boolean moveTo(Entity e, double speed) {
        return getNavigator().tryMoveToEntityLiving(e, speed) || moveTo(new BlockPos(e), speed);
    }

    public boolean isNear(BlockPos pos, double dist) {
        return getDistanceSqToCenter(pos) <= dist * dist;
    }

    // --- combat ---------------------------------------------------------------------------------------

    @Override
    public boolean attackEntityAsMob(Entity target) {
        float dmg = (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        int knock = 0;
        if (target instanceof EntityLivingBase) {
            dmg += EnchantmentHelper.getModifierForCreature(getHeldItemMainhand(), ((EntityLivingBase) target).getCreatureAttribute());
            knock += EnchantmentHelper.getKnockbackModifier(this);
        }
        swing();
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
        if (hit) {
            if (knock > 0 && target instanceof EntityLivingBase) {
                ((EntityLivingBase) target).knockBack(this, knock * 0.5F,
                        MathHelper.sin(rotationYaw * 0.017453292F), -MathHelper.cos(rotationYaw * 0.017453292F));
            }
            int fire = EnchantmentHelper.getFireAspectModifier(this);
            if (fire > 0) target.setFire(fire * 4);
            applyEnchantments(this, target);
            damageTool(1);
        }
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean hurt = super.attackEntityFrom(source, amount);
        if (hurt && !world.isRemote && village != null && source.getTrueSource() instanceof EntityLivingBase
                && !(source.getTrueSource() instanceof EntityPlayer)) {
            village.lastAggressor = (EntityLivingBase) source.getTrueSource();
        }
        return hurt;
    }

    @Override
    protected void damageArmor(float damage) {
        damage = Math.max(1.0F, damage / 4.0F);
        for (net.minecraft.inventory.EntityEquipmentSlot slot : VillagerInventory.EQUIPMENT) {
            if (slot.getSlotType() != net.minecraft.inventory.EntityEquipmentSlot.Type.ARMOR) continue;
            ItemStack s = getItemStackFromSlot(slot);
            if (s.getItem() instanceof net.minecraft.item.ItemArmor) s.damageItem((int) damage, this);
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        if (!world.isRemote) {
            if (craftTask != null) craftTask.abort();
            if (job != null) job.stop();
            inventory.dropAll();
            ITextComponent msg = getCombatTracker().getDeathMessage();
            Messages.broadcast(world, getPosition(), Messages.Kind.DEATH, msg);
        }
        super.onDeath(cause);
    }

    // --- interaction ----------------------------------------------------------------------------------

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == Items.NAME_TAG || held.getItem() == Items.SPAWN_EGG || held.getItem() == Items.LEAD) return false;
        if (player.isSneaking() || hand != EnumHand.MAIN_HAND || !isEntityAlive()) return false;
        if (world.isRemote) return true;
        EntityHelpfulVillager guard = GuardAssignments.take(player);
        if (guard != null) {
            if (guard != this && guard.isEntityAlive()) {
                guard.setLeader(this);
                player.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.guarding", guard.getDisplayName(), getDisplayName()));
                return true;
            }
            player.sendMessage(new TextComponentTranslation("helpfulvillagers.msg.guard_cancelled"));
        }
        if (isChild()) return true;
        getNavigator().clearPath();
        Net.openDialog((EntityPlayerMP) player, this);
        return true;
    }

    @Override
    public ITextComponent getDisplayName() {
        TextComponentTranslation job = new TextComponentTranslation(getJob().translationKey());
        if (hasCustomName()) {
            TextComponentTranslation t = new TextComponentTranslation("helpfulvillagers.name.named", getCustomNameTag(), job);
            t.getStyle().setHoverEvent(getHoverEvent());
            return t;
        }
        return job;
    }

    @Override
    public EntityVillager createChild(EntityAgeable mate) {
        EntityHelpfulVillager child = new EntityHelpfulVillager(world);
        child.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(child)), null);
        return child;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    // --- persistence ----------------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("HVJob", getJob().ordinal());
        tag.setTag("HVInventory", inventory.write());
        NBTTagList t = new NBTTagList();
        for (ItemStack s : taught) t.appendTag(s.writeToNBT(new NBTTagCompound()));
        tag.setTag("HVTaught", t);
        if (savedHallDoor != null) tag.setTag("HVHall", NBTUtil.createPosTag(savedHallDoor));
        if (leaderId != null) tag.setUniqueId("HVLeader", leaderId);
        if (job != null) tag.setTag("HVJobData", job.write());
        else if (pendingJobData != null) tag.setTag("HVJobData", pendingJobData);
        if (craftTask != null) tag.setTag("HVCraft", craftTask.write());
        else if (pendingCraftData != null) tag.setTag("HVCraft", pendingCraftData);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataManager.set(JOB, Profession.byId(tag.getInteger("HVJob")).ordinal());
        inventory.read(tag.getTagList("HVInventory", Constants.NBT.TAG_COMPOUND));
        taught.clear();
        NBTTagList t = tag.getTagList("HVTaught", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < t.tagCount(); i++) {
            ItemStack s = new ItemStack(t.getCompoundTagAt(i));
            if (!s.isEmpty()) taught.add(s);
        }
        savedHallDoor = tag.hasKey("HVHall") ? NBTUtil.getPosFromTag(tag.getCompoundTag("HVHall")) : null;
        leaderId = tag.hasUniqueId("HVLeader") ? tag.getUniqueId("HVLeader") : null;
        pendingJobData = tag.hasKey("HVJobData") ? tag.getCompoundTag("HVJobData") : null;
        pendingCraftData = tag.hasKey("HVCraft") ? tag.getCompoundTag("HVCraft") : null;
        job = null;
        setCanPickUpLoot(false);
    }

    /** Copies age and position from a vanilla villager this one replaces. */
    public void copyFromVanilla(EntityVillager v) {
        setLocationAndAngles(v.posX, v.posY, v.posZ, v.rotationYaw, v.rotationPitch);
        setGrowingAge(v.getGrowingAge());
        if (v.hasCustomName()) setCustomNameTag(v.getCustomNameTag());
        HelpfulVillagers.LOG.debug("Replaced vanilla villager {} at {}", v.getEntityId(), v.getPosition());
    }
}
