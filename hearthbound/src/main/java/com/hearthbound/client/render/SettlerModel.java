package com.hearthbound.client.render;

import com.hearthbound.Hearthbound;
import com.hearthbound.entity.SettlerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Humanoid model with optional pointed ears (for cultures like the elves). */
public class SettlerModel extends HumanoidModel<SettlerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Hearthbound.id("settler"), "main");

    private final ModelPart leftEar;
    private final ModelPart rightEar;

    public SettlerModel(ModelPart root) {
        super(root);
        this.leftEar = head.getChild("left_ear");
        this.rightEar = head.getChild("right_ear");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(56, 0).addBox(-1f, -4f, -1f, 1f, 4f, 2f),
                PartPose.offsetAndRotation(-4f, -4f, 0f, -0.2f, 0f, -0.45f));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0f, -4f, -1f, 1f, 4f, 2f),
                PartPose.offsetAndRotation(4f, -4f, 0f, -0.2f, 0f, 0.45f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SettlerEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        boolean ears = e.ears();
        leftEar.visible = ears;
        rightEar.visible = ears;
    }
}
