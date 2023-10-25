/*
 * Copyright 2022, 2023 EyezahMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.cosmetica.cosmetica.cosmetics.model;

import cc.cosmetica.api.Box;
import cc.cosmetica.api.Model;
import cc.cosmetica.cosmetica.CosmeticaSkinManager;
import cc.cosmetica.cosmetica.utils.DebugMode;
import cc.cosmetica.cosmetica.utils.Scheduler;
import cc.cosmetica.cosmetica.utils.textures.AnimatedTexture;
import cc.cosmetica.cosmetica.utils.textures.ModelSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Models {
	private static Map<String, BakedModel> BAKED_MODELS = new HashMap<>();
	private static Set<BakedModel> NEW_BAKED_MODELS = new HashSet<>();
	private static Map<String, BakableModel> LOADED_MODELS = new HashMap<>();
	private static final float RANDOM_NEXT_FLOAT = 0.211f; // generated by random.org. Guaranteed to be random.
	public static ModelBakery thePieShopDownTheRoad;

	/**
	 * PLEASE DO NOT CALL THIS DIRECTLY (call Cosmetics#clearAllCaches(), or resetTextureBasedCaches() to clear texture related caches)
	 */
	public static void resetCaches() {
		LOADED_MODELS = new HashMap<>();
		resetTextureBasedCaches();
	}

	public static void resetTextureBasedCaches() {
		BAKED_MODELS = new HashMap<>();
	}

	public static Collection<String> getCachedModels() {
		return LOADED_MODELS.keySet();
	}

	@Nullable
	public static BakedModel getBakedModel(BakableModel unbaked) {
		if (unbaked.id().charAt(0) == '-') return null; // help i wrote this at 1:!5am
		boolean compute = !BAKED_MODELS.containsKey(unbaked.id());

		if (compute) {
			DebugMode.log("Computing Baked Model: " + unbaked.id());
			BAKED_MODELS.put(unbaked.id(), null); // searching

			final ResourceLocation location = unbaked.image();
			AbstractTexture modelTexture = Minecraft.getInstance().getTextureManager().getTexture(location, null);

			if (modelTexture instanceof AnimatedTexture) {
				ModelSprite sprite = new ModelSprite(location, (AnimatedTexture) modelTexture);

				ModelBaker ratatouille = new ModelBaker() {
					@Override
					public UnbakedModel getModel(ResourceLocation resourceLocation) {
						return unbaked.model();
					}

					@Override
					@Nullable
					public BakedModel bake(ResourceLocation resourceLocation, ModelState modelState) {
						return this.getModel(resourceLocation).bake(this, l -> sprite, modelState, resourceLocation /*this resource location in bake is just used for debugging in the case of errors*/);
					}
				};

				BakedModel model = ratatouille.bake(location, BlockModelRotation.X0_Y0);

				NEW_BAKED_MODELS.add(model);
				BAKED_MODELS.put(unbaked.id(), model);

				// hack to prevent texture persistence onto other models. See: Line ~+14.
				Scheduler.scheduleTask(Scheduler.Location.TEXTURE_TICK, () -> {
					NEW_BAKED_MODELS.remove(model);
				});

				return model;
			}
		}

		BakedModel result = BAKED_MODELS.get(unbaked.id());

		if (NEW_BAKED_MODELS.contains(result)) { // Don't render on first tick as a temporary hack to fix texture persistence over to new model.
			return null;
		}
		return result;
	}

	public static void removeBakedModel(String id) {
		DebugMode.log("Deallocating baked model, {}", id);
		BAKED_MODELS.remove(id);
	}

	/**
	 * @returns the existing or created bakable model. Will be null if there is an error creating the model
	 * @implNote used when adding the BakableModel to player data
	 */
	@Nullable
	public static BakableModel createBakableModel(Model model) {
		String location = model.getId();

		if (location.isEmpty()) return null;

		Box bounds = model.getBoundingBox();

		if (model.isBuiltin()) {
			return LOADED_MODELS.computeIfAbsent(location, l -> new BakableModel(location, model.getName(), null, null, 0, bounds));
		}

		return LOADED_MODELS.computeIfAbsent(location, l -> {
			try (InputStream is = new ByteArrayInputStream(model.getModel().getBytes(StandardCharsets.UTF_8))) {
				BlockModel blockModel = BlockModel.fromStream(new InputStreamReader(is, StandardCharsets.UTF_8));
				blockModel.name = l;
				return new BakableModel(location, model.getName(), blockModel, CosmeticaSkinManager.processModel(model), model.flags(), bounds);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
	}

	public static void renderModel(BakedModel model, PoseStack stack, MultiBufferSource multiBufferSource, ResourceLocation texture, int packedLight) {
		stack.pushPose();
		boolean isGUI3D = model.isGui3d();
		float transformStrength = 0.25F;
		float rotation = 0.0f;
		float transform = model.getTransforms().getTransform(ItemTransforms.TransformType.GROUND).scale.y();
		stack.translate(0.0D, rotation + transformStrength * transform, 0.0D);
		float xScale = model.getTransforms().ground.scale.x();
		float yScale = model.getTransforms().ground.scale.y();
		float zScale = model.getTransforms().ground.scale.z();

		stack.pushPose();

		final ItemTransforms.TransformType transformType = ItemTransforms.TransformType.FIXED;
		int overlayTyp = OverlayTexture.NO_OVERLAY;
		// ItemRenderer#render start
		stack.pushPose();

		model.getTransforms().getTransform(transformType).apply(false, stack);
		stack.translate(-0.5D, -0.5D, -0.5D);

		RenderType renderType = RenderType.entityTranslucent(texture); // hopefully this is the right one
		VertexConsumer vertexConsumer4 = multiBufferSource.getBuffer(renderType);
		renderModelLists(model, packedLight, overlayTyp, stack, vertexConsumer4);

		stack.popPose();
		// ItemRenderer#render end

		stack.popPose();
		if (!isGUI3D) {
			stack.translate(0.0F * xScale, 0.0F * yScale, 0.09375F * zScale);
		}

		stack.popPose();
	}

	// vanilla code that I don't want to rewrite:

	private static void renderModelLists(BakedModel bakedModel, int packedLight, int overlayType, PoseStack poseStack, VertexConsumer vertexConsumer) {
		RandomSource randomSource = RandomSource.create();
		final long seed = 42L;
		Direction[] var10 = Direction.values();
		int var11 = var10.length;

		for(int var12 = 0; var12 < var11; ++var12) {
			Direction direction = var10[var12];
			randomSource.setSeed(seed);
			renderQuadList(poseStack, vertexConsumer, bakedModel.getQuads(null, direction, randomSource), packedLight, overlayType);
		}

		randomSource.setSeed(seed);
		renderQuadList(poseStack, vertexConsumer, bakedModel.getQuads(null, null, randomSource), packedLight, overlayType);
	}

	private static void renderQuadList(PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> list, int i, int j) {
		PoseStack.Pose pose = poseStack.last();
		Iterator var9 = list.iterator();

		while(var9.hasNext()) {
			BakedQuad bakedQuad = (BakedQuad)var9.next();
			int k = -1;

			float f = (float)(k >> 16 & 255) / 255.0F;
			float g = (float)(k >> 8 & 255) / 255.0F;
			float h = (float)(k & 255) / 255.0F;
			vertexConsumer.putBulkData(pose, bakedQuad, f, g, h, i, j);
		}
	}
}
