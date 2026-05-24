package com.frostlogic.warproject.client.renderer;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.server.transport.TransportNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Renders {@link TransportNpcEntity} as a humanoid (player-like) model with a
 * faction-specific military skin. Head-shake and arm-wave animations are
 * driven by the vanilla rig:
 * <ul>
 *   <li>Head shake — entity tick() rewrites {@code yHeadRot}; the model's
 *       built-in head bone interpolates and follows.</li>
 *   <li>Arm wave — {@code mob.swing(MAIN_HAND)} triggers
 *       {@code Mob#swingTime / swingingArm}, which {@link HumanoidModel} renders
 *       as the standard "use item" swing animation.</li>
 * </ul>
 * No custom render hooks are needed — keeping this renderer thin matches the
 * approach used by {@code FactionNpcRenderer}.
 */
public class TransportNpcRenderer extends HumanoidMobRenderer<TransportNpcEntity, HumanoidModel<TransportNpcEntity>> {

    private static final ResourceLocation ZARNAVIA_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "warproject", "textures/entity/transport_npc_zarnavia.png");
    private static final ResourceLocation CHERNOGRYAD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "warproject", "textures/entity/transport_npc_chernogryad.png");

    public TransportNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull TransportNpcEntity entity) {
        return entity.getFactionId() == FactionId.CHERNOGRYAD ? CHERNOGRYAD_TEXTURE : ZARNAVIA_TEXTURE;
    }
}
