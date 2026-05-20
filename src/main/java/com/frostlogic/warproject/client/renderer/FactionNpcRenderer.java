package com.frostlogic.warproject.client.renderer;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Renders {@link FactionNpcEntity} as a humanoid (player-like) model
 * with a faction-specific military skin texture.
 * <p>
 * Texture paths:
 * <ul>
 *   <li>{@code warproject:textures/entity/faction_npc_zarnavia.png}</li>
 *   <li>{@code warproject:textures/entity/faction_npc_chernogryad.png}</li>
 * </ul>
 */
public class FactionNpcRenderer extends HumanoidMobRenderer<FactionNpcEntity, HumanoidModel<FactionNpcEntity>> {

    private static final ResourceLocation ZARNAVIA_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "warproject", "textures/entity/faction_npc_zarnavia.png");
    private static final ResourceLocation CHERNOGRYAD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "warproject", "textures/entity/faction_npc_chernogryad.png");

    public FactionNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull FactionNpcEntity entity) {
        return entity.getFactionId() == FactionId.CHERNOGRYAD ? CHERNOGRYAD_TEXTURE : ZARNAVIA_TEXTURE;
    }

}
