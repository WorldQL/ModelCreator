package dev.marggx.mcreator.services;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.builtin.buildertools.prefablist.AssetPrefabFileProvider;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.marggx.mcreator.data.blockymodel.BlockymodelVector3d;
import dev.marggx.mcreator.data.extras.BaseModel;
import dev.marggx.mcreator.data.extras.Model;
import dev.marggx.mcreator.utils.Logger;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HytaleService {

    private static final HytaleService INSTANCE = new HytaleService();
    private static final Logger LOGGER = Logger.get();
    private final AssetPrefabFileProvider assetProvider = new AssetPrefabFileProvider();
    public static HytaleService get() {
        return INSTANCE;
    }

    public List<Holder<EntityStore>> getEntitiesFromPrefab(String prefabPath) {
        Path path = PrefabStore.get().findAssetPrefabPath(prefabPath);
        if (path == null) {
            LOGGER.severe("Prefab not found: " + prefabPath);
            return null;
        }

        BlockSelection prefab = PrefabStore.get().getPrefab(path);
        return getEntitiesFromBlockSelection(prefab);
    }

    public BlockSelection getBlockSelectionFromPrefab(String prefabPath) {
        Path path = this.assetProvider.resolveVirtualPath(prefabPath);
        path = path != null ? path : PrefabStore.get().findAssetPrefabPath(prefabPath);
        BlockSelection prefab = null;
        if (path == null) {
            try {
                prefab = PrefabStore.get().getServerPrefab(prefabPath);
            } catch (Exception e) {
                LOGGER.severe("Failed to load prefab from path '%s': %s", prefabPath, e.getMessage());
            }
        } else {
            try {
                prefab = PrefabStore.get().getPrefab(path);
            } catch (Exception e) {
                LOGGER.severe("Failed to load prefab from path '%s': %s", prefabPath, e.getMessage());
            }
        }
        return prefab;
    }

    public List<Holder<EntityStore>> getEntitiesFromBlockSelection(BlockSelection selection) {
        int xMin = selection.getSelectionMin().getX();
        int zMin = selection.getSelectionMin().getZ();
        int width = selection.getSelectionMax().getX() - xMin;
        int depth = selection.getSelectionMax().getZ() - zMin;
        List<Holder<EntityStore>> entities = new ObjectArrayList<>();
        selection.forEachEntity(e -> entities.add(e.clone()));
        entities.forEach((e) -> {
            TransformComponent pos = e.getComponent(TransformComponent.getComponentType());
            if (pos == null) return;
            pos.getPosition().add(width, 0.0, depth);
        });
        return entities;
    }

    public List<Model> deduplicateModels(List<Model> models) {
        Map<String, String> modelCache = new HashMap<>();
        List<Model> dedupedModels = new ObjectArrayList<>();
        for (Model model : models) {
            Holder<EntityStore> holder = model.holder();
            TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
            if (transform == null) return null;

            BlockymodelVector3d position = BlockymodelVector3d.from(transform.getPosition());
            HeadRotation headRotation = holder.getComponent(HeadRotation.getComponentType());
            Vector3f rotationVector = MapperService.get().createRotationVector(model, headRotation, transform);
            String cacheKey = model.id() + position + rotationVector;
            if (modelCache.containsKey(cacheKey)) {
                continue;
            }
            modelCache.put(cacheKey, model.id());
            dedupedModels.add(model);
        }
        return dedupedModels;
    }


    public void createNewItem(BaseModel baseModel) throws IOException {
        String content = """
                {
                  "TranslationProperties": {
                    "Name": "server.items.<Name>.name"
                  },
                  "Icon": "Icons/ItemsGenerated/Debug_Block.png",
                  "Categories": [],
                  "Quality": "Developer",
                  "Interactions": {
                    "Primary": "Block_Primary",
                    "Secondary": "Block_Secondary"
                  },
                  "BlockType": {
                    "Material": "Solid",
                    "DrawType": "Model",
                    "CustomModel": "<ModelPath>",
                    "CustomModelTexture": [
                      {
                        "Texture": "<TexturePath>",
                        "Weight": 1
                      }
                    ],
                    "ParticleColor": "#0000f4"
                  },
                  "PlayerAnimationsId": "Block",
                  "Tags": {
                    "Type": [
                      "Debug"
                    ]
                  }
                }
                """;

        String name = createValidItemName(baseModel.name());

        content = content
                .replace("<Name>", name)
                .replace("<ModelPath>", "Blocks/" + baseModel.name() + ".blockymodel")
                .replace("<TexturePath>", "Blocks/" + baseModel.name() + ".png");

        Path outputPath = null;
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            if (!pack.getName().equals(baseModel.pack())) continue;
            outputPath = pack.getRoot().resolve("Server").resolve("Item").resolve("Items");
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            outputPath = outputPath.resolve(name + ".json");
        }
        if (outputPath == null) return;
        Files.writeString(outputPath, content);
    }

    public String createValidItemName(String name) {
        if (Character.isDigit(name.charAt(0))) {
            return "Merged_" + name;
        }

        if (Character.isUpperCase(name.charAt(0))) {
            return name;
        }

        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
