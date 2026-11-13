package com.electrifiedded.helpfulvillagers.client;

import com.electrifiedded.archaiclib.original.OriginalModAssets;
import com.electrifiedded.helpfulvillagers.HelpfulVillagers;
import com.electrifiedded.helpfulvillagers.entity.EntityHelpfulVillager;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.util.ResourceLocation;

/**
 * Villagers are drawn as bipeds with the 64x32 skins of the original mod, so they can hold tools and
 * wear armour. Without the original jar a vanilla skin is used so the mod still works.
 */
public class RenderHelpfulVillager extends RenderBiped<EntityHelpfulVillager> {

    private static final ResourceLocation[] SKINS = new ResourceLocation[com.electrifiedded.helpfulvillagers.entity.Profession.values().length];
    private static final ResourceLocation FALLBACK = new ResourceLocation("textures/entity/steve.png");

    static {
        for (com.electrifiedded.helpfulvillagers.entity.Profession p : com.electrifiedded.helpfulvillagers.entity.Profession.values()) {
            SKINS[p.ordinal()] = new ResourceLocation(HelpfulVillagers.MODID, "textures/entity/villager/" + p.name + ".png");
        }
    }

    private final ModelBiped legacyModel;
    private final ModelBiped fallbackModel;

    public RenderHelpfulVillager(RenderManager manager) {
        super(manager, new ModelBiped(0.0F, 0.0F, 64, 32), 0.5F);
        legacyModel = (ModelBiped) mainModel;
        fallbackModel = new ModelPlayer(0.0F, false);
        addLayer(new LayerBipedArmor(this));
    }

    private static boolean haveOriginals() {
        return OriginalModAssets.isPresent(HelpfulVillagers.MODID);
    }

    @Override
    public void doRender(EntityHelpfulVillager entity, double x, double y, double z, float entityYaw, float partialTicks) {
        mainModel = haveOriginals() ? legacyModel : fallbackModel;
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityHelpfulVillager entity) {
        return haveOriginals() ? SKINS[entity.getJob().ordinal()] : FALLBACK;
    }
}
