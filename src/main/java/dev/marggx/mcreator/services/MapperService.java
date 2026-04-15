package dev.marggx.mcreator.services;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.marggx.mcreator.data.blockymodel.*;
import dev.marggx.mcreator.data.extras.BaseModel;
import dev.marggx.mcreator.data.extras.Model;
import dev.marggx.mcreator.utils.Logger;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MapperService {
    private static final MapperService INSTANCE = new MapperService();
    private static final Logger LOGGER = Logger.get();
    public static MapperService get() {
        return INSTANCE;
    }

    private final BlockymodelService blockymodelService = BlockymodelService.get();
    private final TextureService textureService = TextureService.get();
    private final HytaleService hytaleService = HytaleService.get();

    public boolean createBlockymodelFromPrefab(Path prefabPath, String pack, String name, boolean createNewItem) {
        BlockSelection prefab = PrefabStore.get().getPrefab(prefabPath);
        List<Model> blockymodels = getBlockymodelsAndEntitiesFromBlockSelection(prefab);
        if (blockymodels == null) return false;

        return createBlockymodelFromBlockSelection(blockymodels, prefab, pack, name, createNewItem);
    }

    public boolean createBlockymodelFromBlockSelection(BlockSelection selection, String pack, String name, boolean createNewItem) {
        List<Model> blockymodels = getBlockymodelsAndEntitiesFromBlockSelection(selection);
        if (blockymodels == null) return false;

        return createBlockymodelFromBlockSelection(blockymodels, selection, pack, name, createNewItem);
    }

    public boolean createBlockymodelFromBlockSelection(List<Model> blockymodels, BlockSelection selection, String pack, String name, boolean createNewItem) {
        BaseModel base = new BaseModel(600, selection, null, null, null, null);
        BaseModel model = createBlockymodel(blockymodels, base);

        model.setName(name);
        model.setPack(pack);
        boolean created = createNewModel(model);
        return created;
    }

    private boolean createNewModel(BaseModel model) {
        boolean valid = model.validate();
        if (!valid) return false;

        return blockymodelService.saveBlockymodelBase(model) && textureService.saveTexture(model) && saveModelAsset(model);
    }

    private boolean saveModelAsset(BaseModel model) {
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            if (!pack.getName().equals(model.pack())) continue;

            Path outputDir = pack.getRoot().resolve("Server/Models/Merged");
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                LOGGER.severe("Failed to create Server/Models directory for pack '%s'", model.pack());
                return false;
            }

            String name = model.name();
            double[] hitbox = calculateHitbox(model);
            double halfX = hitbox[0];
            double halfZ = hitbox[1];
            double height = hitbox[2];

            String json = """
                {
                  "Model": "VFX/Merged/%s.blockymodel",
                  "Texture": "VFX/Merged/%s.png",
                  "EyeHeight": %s,
                  "HitBox": {
                    "Max": { "X": %s, "Y": %s, "Z": %s },
                    "Min": { "X": %s, "Y": 0, "Z": %s }
                  },
                  "MinScale": 0.5,
                  "MaxScale": 4
                }""".formatted(name, name,
                    height / 2,
                    halfX, height, halfZ,
                    -halfX, -halfZ);

            try {
                Files.writeString(outputDir.resolve(name + ".json"), json);
            } catch (IOException e) {
                LOGGER.severe("Failed to write ModelAsset for '%s'", name);
                return false;
            }
            return true;
        }

        LOGGER.severe("No pack found with name '%s'", model.pack());
        return false;
    }

    // Returns { halfX, halfZ, height } in Hytale units
    private double[] calculateHitbox(BaseModel model) {
        double[] bounds = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                           -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };

        for (Blockymodel bm : model.blockymodels()) {
            collectBounds(bm, 0, 0, 0, bounds);
        }

        double sizeX = (bounds[3] - bounds[0]) / 32.0;
        double sizeZ = (bounds[5] - bounds[2]) / 32.0;
        double height = (bounds[4] - bounds[1]) / 32.0;

        return new double[] { sizeX / 2, sizeZ / 2, height };
    }

    private void collectBounds(Blockymodel node, double px, double py, double pz, double[] bounds) {
        double nx = px + (node.position != null ? node.position.getX() : 0);
        double ny = py + (node.position != null ? node.position.getY() : 0);
        double nz = pz + (node.position != null ? node.position.getZ() : 0);

        if (node.shape != null && node.shape.offset != null && node.shape.stretch != null) {
            double ox = nx + node.shape.offset.getX();
            double oy = ny + node.shape.offset.getY();
            double oz = nz + node.shape.offset.getZ();
            double sx = ox + node.shape.stretch.getX();
            double sy = oy + node.shape.stretch.getY();
            double sz = oz + node.shape.stretch.getZ();

            bounds[0] = Math.min(bounds[0], Math.min(ox, sx));
            bounds[1] = Math.min(bounds[1], Math.min(oy, sy));
            bounds[2] = Math.min(bounds[2], Math.min(oz, sz));
            bounds[3] = Math.max(bounds[3], Math.max(ox, sx));
            bounds[4] = Math.max(bounds[4], Math.max(oy, sy));
            bounds[5] = Math.max(bounds[5], Math.max(oz, sz));
        }

        if (node.children != null) {
            for (Blockymodel child : node.children) {
                collectBounds(child, nx, ny, nz, bounds);
            }
        }
    }

    private BaseModel createBlockymodel(List<Model> models, BaseModel base) {
        for (Model existingModel : models) {
            Blockymodel blockymodel = createBlockymodelFromExistingModel(base, existingModel);
            if (blockymodel == null) continue;

            base.incrementBlockyId();
            base.addBlockymodel(blockymodel);
        }

        return base;
    }

    private Blockymodel createBlockymodelFromExistingModel(BaseModel base, Model model) {
        Holder<EntityStore> holder = model.holder();
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        if (transform == null) {
            LOGGER.warning("Model " + model.id() + " has no transform component. Cannot create blockymodel.");
            return null;
        }

        BlockymodelVector3d position = BlockymodelVector3d.from(transform.getPosition());
        HeadRotation headRotation = holder.getComponent(HeadRotation.getComponentType());
        Vector3f rotationVector = createRotationVector(model, headRotation, transform);

        BlockymodelBase blockymodelBase = blockymodelService.loadBlockymodelBase(model.path());
        if (blockymodelBase == null) {
            LOGGER.warning("Model " + model.id() + " has no blockymodel. Cannot create blockymodel.");
            return null;
        }
        model.setBlockymodel(blockymodelBase);

        BlockymodelQuaternion orientation = BlockymodelQuaternion.fromVector3f(rotationVector);

        textureService.handleTexture(model, base);

        if (model.attachedModels() != null) {
            blockymodelService.addAttachments(model, base);
        }

        handleScale(base, model, holder, blockymodelBase);

        handleHeadRotation(rotationVector, model, holder, blockymodelBase);

        BlockymodelVector3d offset = BlockymodelVector3d.from(new Vector3d(0, -16.0, 0));

        boolean hasTransformRotation = hasTransformRotation(model, headRotation, transform);
        handlePosition(base, position, offset, hasTransformRotation);

        BlockymodelShape shape = new BlockymodelShape(
                offset,
                new BlockymodelVector3d(),
                new HashMap<>(),
                BlockymodelShapeType.None,
                new BlockymodelShapeSettings()
        );

        return new Blockymodel(
                base.getStrBlockyId(),
                model.id(),
                BlockymodelVector3d.from(position),
                orientation,
                shape,
                blockymodelBase.getNodes()
        );
    }

    public List<Model> getBlockymodelsAndEntitiesFromPrefab(String prefabName) {
        List<Holder<EntityStore>> entities = hytaleService.getEntitiesFromPrefab(prefabName);
        if (entities.isEmpty()) {
            LOGGER.warning("No entities found in prefab. Cannot create List<Model>.");
            return null;
        };
        return getModelsFromEntities(entities);
    }

    public List<Model> getBlockymodelsAndEntitiesFromBlockSelection(BlockSelection selection) {
        List<Holder<EntityStore>> entities = hytaleService.getEntitiesFromBlockSelection(selection);
        if (entities.isEmpty()) {
            LOGGER.warning("No entities found in selection. Cannot create List<Model>.");
            return null;
        }
        return getModelsFromEntities(entities);
    }

    public List<Model> getModelsFromEntities(List<Holder<EntityStore>> entities) {
        List<Model> list = new ObjectArrayList<>();
        for (Holder<EntityStore> entity : entities) {
            Model model = blockymodelService.loadModelFromHolder(entity);

            if (model == null || !model.validate()) {
                LOGGER.severe("Something went wrong when loading a model from an entity.", model);
                continue;
            }

            list.add(model);
        }
        return hytaleService.deduplicateModels(list);
    }

    public Vector3f createRotationVector(Model model, HeadRotation headRotation, TransformComponent transform) {
        Vector3f rotation = new Vector3f();
        if (headRotation == null) {
            return rotation.assign(transform.getRotation());
        }

        if (hasTransformRotation(model, headRotation, transform)) {
            rotation.assign(transform.getRotation());
            return rotation;
        }

        rotation.assign(headRotation.getRotation());

        return rotation;
    }

    private boolean hasTransformRotation(Model model, HeadRotation headRotation, TransformComponent transform) {
        if (headRotation == null) return false;
        if (model.getType() == Model.ModelType.MODEL) return true;
        return headRotation.getRotation().getX() == 0.0f && headRotation.getRotation().getZ() == 0.0f && (transform.getRotation().getX() != 0.0f || transform.getRotation().getZ() != 0.0f);
    }

    private void handlePosition(BaseModel base, BlockymodelVector3d position, BlockymodelVector3d offset, boolean hasTransformRotation) {
        position.setX(-position.getX());
        position.setZ(-position.getZ());

        //One Hytale unit = 32 Blockbench units
        position.scale(32.0);

        if (hasTransformRotation) {
            offset.setY(0.0);
            position.setY(position.getY() - 16.0);
        }

        double addX = 32.0;
        double addZ = 32.0;
        if (base.selection() != null) {
            int subX = base.selection().getSelectionMax().getX() - base.selection().getSelectionMin().getX();
            addX *= 1 + subX;
            int subZ = base.selection().getSelectionMax().getZ() - base.selection().getSelectionMin().getZ();
            addZ *= 1 + subZ;
        }

        position.add(addX, 16.0, addZ);
        position.round(4);
    }

    private void handleScale(BaseModel base, Model model, Holder<EntityStore> holder, BlockymodelBase blockymodelBase) {
        EntityScaleComponent scaleComponent = holder.getComponent(EntityScaleComponent.getComponentType());
        double scale = scaleComponent == null ? 1.0 : scaleComponent.getScale() / 2;
        if (model.getType() == Model.ModelType.MODEL) {
            ModelComponent modelComponent = holder.getComponent(ModelComponent.getComponentType());
            assert modelComponent != null;
            scale = modelComponent.getModel().getScale() / 2;
        }
        scale = MathUtil.round(scale, 4);

        blockymodelService.scaleBlockymodel(blockymodelBase, scale);
    }

    private void handleHeadRotation(Vector3f baseOrientation, Model model, Holder<EntityStore> holder, BlockymodelBase blockymodelBase) {
        if (model.getType() != Model.ModelType.MODEL) {
            return;
        }

        HeadRotation headRotation = holder.getComponent(HeadRotation.getComponentType());
        if (headRotation == null) {
            return;
        }

        BlockymodelQuaternion orientation = BlockymodelQuaternion.getLocalQuat(baseOrientation, headRotation.getRotation());
        boolean didWork = blockymodelService.setHeadRotation(blockymodelBase, orientation);
        if (!didWork) {
            LOGGER.severe("Failed to set head rotation for model: " + model.id());
        }
    }
}
