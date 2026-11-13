package com.electrifiedded.helpfulvillagers.client;

import com.electrifiedded.helpfulvillagers.entity.EntityVillagerBobber;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/** Draws the villager's bobber with the vanilla hook texture and a line to the rod, like the player's. */
public class RenderVillagerBobber extends Render<EntityVillagerBobber> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/fishing_hook.png");

    public RenderVillagerBobber(RenderManager manager) {
        super(manager);
    }

    @Override
    public void doRender(EntityVillagerBobber bobber, double x, double y, double z, float yaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        bindEntityTexture(bobber);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.rotate(180.0F - renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate((renderManager.options.thirdPersonView == 2 ? -1 : 1) * -renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        buf.begin(7, DefaultVertexFormats.POSITION_TEX_NORMAL);
        buf.pos(-0.5D, -0.5D, 0.0D).tex(0.0625D, 0.1875D).normal(0.0F, 1.0F, 0.0F).endVertex();
        buf.pos(0.5D, -0.5D, 0.0D).tex(0.125D, 0.1875D).normal(0.0F, 1.0F, 0.0F).endVertex();
        buf.pos(0.5D, 0.5D, 0.0D).tex(0.125D, 0.125D).normal(0.0F, 1.0F, 0.0F).endVertex();
        buf.pos(-0.5D, 0.5D, 0.0D).tex(0.0625D, 0.125D).normal(0.0F, 1.0F, 0.0F).endVertex();
        tess.draw();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();

        Entity angler = bobber.getAngler();
        if (!(angler instanceof EntityLivingBase)) return;
        EntityLivingBase a = (EntityLivingBase) angler;
        float body = (a.prevRenderYawOffset + (a.renderYawOffset - a.prevRenderYawOffset) * partialTicks) * 0.017453292F;
        double sin = MathHelper.sin(body), cos = MathHelper.cos(body);
        double hx = a.prevPosX + (a.posX - a.prevPosX) * partialTicks - cos * 0.35D - sin * 0.8D;
        double hy = a.prevPosY + a.getEyeHeight() + (a.posY - a.prevPosY) * partialTicks - 0.45D;
        double hz = a.prevPosZ + (a.posZ - a.prevPosZ) * partialTicks - sin * 0.35D + cos * 0.8D;
        double bx = bobber.prevPosX + (bobber.posX - bobber.prevPosX) * partialTicks;
        double by = bobber.prevPosY + (bobber.posY - bobber.prevPosY) * partialTicks + 0.25D;
        double bz = bobber.prevPosZ + (bobber.posZ - bobber.prevPosZ) * partialTicks;
        double dx = hx - bx, dy = hy - by, dz = hz - bz;
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        buf.begin(3, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 16; i++) {
            float t = i / 16.0F;
            buf.pos(x + dx * t, y + 0.25D + dy * (t * t + t) * 0.5D, z + dz * t).color(0, 0, 0, 255).endVertex();
        }
        tess.draw();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityVillagerBobber entity) {
        return TEXTURE;
    }
}
