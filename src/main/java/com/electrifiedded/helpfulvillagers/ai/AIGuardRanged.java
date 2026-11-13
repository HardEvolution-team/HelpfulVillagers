package com.electrifiedded.helpfulvillagers.ai;

import com.electrifiedded.helpfulvillagers.ModConfig;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import com.electrifiedded.helpfulvillagers.entity.Profession;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;

/** Archers keep their distance and shoot arrows from their bag (or free ones with infiniteArrows). */
public class AIGuardRanged extends EntityAIBase {

    private static final double MAX_RANGE = 16.0D;
    private final EntityHelpfulVillager guard;
    private int seeTime;
    private int drawTicks;

    public AIGuardRanged(EntityHelpfulVillager guard) {
        this.guard = guard;
        setMutexBits(3);
    }

    private boolean hasArrows() {
        return ModConfig.infiniteArrows || guard.inventory.count(s -> s.getItem() instanceof ItemArrow) > 0;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = guard.getAttackTarget();
        return t != null && t.isEntityAlive() && guard.getJob() == Profession.ARCHER && guard.hasTool() && hasArrows();
    }

    @Override
    public void resetTask() {
        guard.resetActiveHand();
        seeTime = 0;
        drawTicks = 0;
        if (guard.getAttackTarget() != null && !guard.getAttackTarget().isEntityAlive()) guard.setAttackTarget(null);
    }

    @Override
    public void updateTask() {
        EntityLivingBase t = guard.getAttackTarget();
        if (t == null) return;
        double d = guard.getDistanceSq(t);
        boolean see = guard.getEntitySenses().canSee(t);
        seeTime = see ? Math.max(0, seeTime) + 1 : Math.min(0, seeTime) - 1;
        if (d > MAX_RANGE * MAX_RANGE * 0.75 || !see) guard.getNavigator().tryMoveToEntityLiving(t, 0.7D);
        else if (d < 25) guard.getNavigator().clearPath();
        else guard.getNavigator().clearPath();
        guard.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        if (!guard.isHandActive()) {
            if (see && seeTime > 5 && d < MAX_RANGE * MAX_RANGE) {
                guard.setActiveHand(EnumHand.MAIN_HAND);
                drawTicks = 0;
            }
            return;
        }
        if (!see && seeTime < -40) {
            guard.resetActiveHand();
            return;
        }
        if (++drawTicks >= 20 && see) {
            guard.resetActiveHand();
            shoot(t, 1.0F);
        }
    }

    private void shoot(EntityLivingBase target, float pull) {
        ItemStack arrowStack;
        if (ModConfig.infiniteArrows) {
            arrowStack = new ItemStack(Items.ARROW);
        } else {
            arrowStack = guard.inventory.extract(s -> s.getItem() instanceof ItemArrow, 1);
            if (arrowStack.isEmpty()) return;
        }
        EntityArrow arrow = ((ItemArrow) arrowStack.getItem()).createArrow(guard.world, arrowStack, guard);
        if (arrow instanceof EntityTippedArrow && arrowStack.getItem() == Items.ARROW) ((EntityTippedArrow) arrow).setPotionEffect(arrowStack);
        double dx = target.posX - guard.posX;
        double dy = target.getEntityBoundingBox().minY + target.height / 3.0F - arrow.posY;
        double dz = target.posZ - guard.posZ;
        double flat = MathHelper.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2D, dz, 1.6F, 14 - guard.world.getDifficulty().getId() * 4);
        ItemStack bow = guard.getHeldItemMainhand();
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, bow);
        int punch = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, bow);
        arrow.setDamage(2.0D + guard.getRNG().nextGaussian() * 0.25D + power * 0.5D + (power > 0 ? 0.5D : 0));
        if (punch > 0) arrow.setKnockbackStrength(punch);
        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, bow) > 0) arrow.setFire(100);
        arrow.pickupStatus = ModConfig.infiniteArrows ? EntityArrow.PickupStatus.DISALLOWED : EntityArrow.PickupStatus.ALLOWED;
        guard.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (guard.getRNG().nextFloat() * 0.4F + 0.8F));
        guard.world.spawnEntity(arrow);
        guard.damageTool(1);
    }
}
