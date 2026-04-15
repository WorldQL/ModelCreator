package dev.marggx.mcreator.services;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockTypeTextures;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.marggx.mcreator.data.blockymodel.*;
import dev.marggx.mcreator.data.extras.BaseModel;
import dev.marggx.mcreator.data.extras.Model;
import dev.marggx.mcreator.utils.Logger;
import org.bson.BsonDocument;
import org.joml.Quaterniond;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static dev.marggx.mcreator.data.blockymodel.BlockymodelQuaternion.rotateYVector3d;

public class BlockymodelService {

    private static final BlockymodelService INSTANCE = new BlockymodelService();
    private static final Logger LOGGER = Logger.get();
    public static BlockymodelService get() {
        return INSTANCE;
    }

    public Model loadModelFromHolder(Holder<EntityStore> holder) {
        Model model = new Model();
        model.setHolder(holder);
        String id = getHolderId(holder);
        if (id == null) return null;

        model.setId(id);

        ModelComponent modelComponent = holder.getComponent(ModelComponent.getComponentType());
        if (modelComponent != null) {
            model.setType(Model.ModelType.MODEL);
            model.setPath(modelComponent.getModel().getModel());
            model.setTexturePath(modelComponent.getModel().getTexture());
            model.setAttachedModels(modelComponent.getModel().getAttachments());
        } else {
            getAndSetModelAndTexturePaths(model);
        }

        return model;
    }

    public BlockymodelBase loadBlockymodelBase(String path) {
        Path p = getBlockymodelPathFromAnyPack(path);
        if (p == null) return null;
        return load(p);
    }

    public String getHolderId(Holder<EntityStore> holder) {
        ItemComponent itemComp = holder.getComponent(ItemComponent.getComponentType());

        if (itemComp != null) {
            ItemStack itemStack = itemComp.getItemStack();
            if (itemStack != null) {
                Item item = getItemFromAssetsById(itemStack.getItemId());
                if (item != null) {
                    return item.getId();
                }
            }
        }

        BlockEntity blockEntity = holder.getComponent(BlockEntity.getComponentType());
        if (blockEntity != null) return blockEntity.getBlockTypeKey();

        ModelComponent modelComponent = holder.getComponent(ModelComponent.getComponentType());
        if (modelComponent != null) return modelComponent.getModel().getModelAssetId();

        return null;
    }

    public void saveBlockymodel(BlockymodelBase base, Path p) {
        if (!p.isAbsolute()) {
            p = p.toAbsolutePath().normalize();
        }

        BsonUtil.writeDocument(p, BlockymodelBase.CODEC.encode(base, new ExtraInfo())).join();
    }

    public Path getBlockymodelPathForPack(@Nonnull String name) {
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            if (!pack.getName().equals(name)) continue;
            Path blockmodelPackPath = this.getBlockymodelPathForPack(pack);
            if (Files.isDirectory(blockmodelPackPath)) return blockmodelPackPath;
            try {
                Files.createDirectories(blockmodelPackPath);
            } catch (Exception e) {
                LOGGER.severe("Failed to create directory for blockymodel in pack '%s'", name);
                return null;
            }
        }

        return null;
    }

    public BlockymodelBase createBlockymodelBase(BaseModel model) {
        List<Blockymodel> blockymodels = model.blockymodels();
        if (blockymodels.isEmpty()) return null;

        var children = blockymodels.toArray(new Blockymodel[0]);
        Blockymodel rootModel = new Blockymodel(
                "599",
                "Root",
                new BlockymodelVector3d(0, 0, 0),
                BlockymodelQuaternion.fromVector3d(new Vector3d(0, Math.PI / 2, 0)),
                new BlockymodelShape(
                        new BlockymodelVector3d(),
                        new BlockymodelVector3d(),
                        new HashMap<>(),
                        BlockymodelShapeType.None,
                        new BlockymodelShapeSettings()
                ),
                children
        );
        scaleBlockymodel(rootModel, 2.0);

        return new BlockymodelBase(null, new Blockymodel[]{rootModel});
    }

    public boolean saveBlockymodelBase(BaseModel base) {
        try {
            BlockymodelBase blockymodelBase = createBlockymodelBase(base);
            if (blockymodelBase == null) {
                LOGGER.severe("Failed, model.blockymodel() seem to be empty for: " + base.name());
                return false;
            }
            LOGGER.severe("Node Count: " + countNodes(blockymodelBase) + " for " + base.name());

            Path p = getBlockymodelPathForPack(base.pack());
            if (p == null) return false;
            p = p.resolve("VFX/Merged");
            if (!Files.exists(p)) {
                Files.createDirectories(p);
            }
            p = p.resolve(base.name() + ".blockymodel");
            saveBlockymodel(blockymodelBase, p);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Exception while saving Blockymodel: " + e);
            return false;
        }
    }

    private Path getBlockymodelPathForPack(@Nonnull AssetPack pack) {
        return pack.getRoot().resolve("Common");
    }

    private BlockymodelBase load(Path path) {
        Path p = path.toAbsolutePath().normalize();
        if (!Files.exists(p)) return null;

        BsonDocument doc = BsonUtil.readDocument(p).join();
        return BlockymodelBase.CODEC.decode(doc, new ExtraInfo());
    }

    private Path getHytaleAssetRootPath() {
        return AssetModule.get().getBaseAssetPack().getRoot();
    }

    private Path getBlockymodelPathFromAnyPack(@Nonnull String name) {
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            Path blockmodelPackPath = this.getBlockymodelPathForPack(pack);
            Path blockmodelPath = blockmodelPackPath.resolve(name);
            if (Files.exists(blockmodelPath)) return blockmodelPath;
        }

        return null;
    }

    private void getAndSetModelAndTexturePaths(Model model) {
        Item item = getItemFromAssetsById(model.id());
        if (item.hasBlockType()) {
            model.setType(Model.ModelType.BLOCK);
        } else {
            model.setType(Model.ModelType.ITEM);
        }
        model.setPath(getModelPathFromItem(item));
        String texturePath = getTexturePathFromItem(item);
        model.setTexturePath(texturePath);
    }

    private String getModelPathFromItem(@Nonnull Item item) {
        String modelPath = item.getModel();
        String modelPathBlockType = null;
        if (item.hasBlockType()) {
            modelPathBlockType = getModelPathFromBlockType(item.getId());
        }
        return modelPathBlockType != null ? modelPathBlockType : modelPath;
    }

    private String getModelPathFromBlockType(String blockTypeKey) {
        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeKey);
        if (blockType == null) return null;

        String customModel = blockType.getCustomModel();
        if (customModel != null) return customModel;

        return "Blocks/block.blockymodel";
    }

    private String getTexturePathFromItem(Item item) {
        String texturePath = item.getTexture();
        if (item.hasBlockType()) {
            return getTexturePathFromBlockType(item.getId());
        }
        return texturePath;
    }

    private String getTexturePathFromBlockType(String blockTypeKey) {
        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeKey);
        if (blockType == null) return null;

        BlockTypeTextures[] textures = blockType.getTextures();
        if (textures != null && textures.length > 0) return textures[0].getUp();

        CustomModelTexture[] customTextures = blockType.getCustomModelTexture();
        if (customTextures == null || customTextures.length == 0) return null;
        return customTextures[0].getTexture();
    }

    private Item getItemFromAssetsById(String id) {
        return Item.getAssetMap().getAsset(id);
    }

    public void scaleBlockymodel(BlockymodelBase blockymodel, double scale) {
        Blockymodel[] nodes = blockymodel.getNodes();

        for (Blockymodel node : nodes) {
            scaleBlockymodel(node, scale);
        }
    }

    public boolean setHeadRotation(BlockymodelBase blockymodel, BlockymodelQuaternion rotation) {
        Blockymodel[] nodes = blockymodel.getNodes();
        boolean changed = false;
        for (Blockymodel node : nodes) {
            if (node.name.equals("Head")) {
                node.orientation = rotation;
                changed = true;
            }

            if (setHeadRotation(node, rotation)) changed = true;
        }
        return changed;
    }

    private boolean setHeadRotation(Blockymodel blockymodel, BlockymodelQuaternion rotation) {
        boolean changed = false;
        if (blockymodel.name.equals("Head")) {
            blockymodel.orientation = rotation;
            changed = true;
        }

        if (blockymodel.children == null) return changed;
        for (Blockymodel child : blockymodel.children) {
            if (setHeadRotation(child, rotation)) changed = true;
        }
        return changed;
    }

    private void scaleBlockymodel(Blockymodel model, double scale) {
        if (model.position != null) {
            model.position.scale(scale);
        }

        if (model.shape != null) {
            if (model.shape.stretch != null) {
                model.shape.stretch.scale(scale);
            }
            if (model.shape.offset != null) {
                model.shape.offset.scale(scale);
            }
        }

        if (model.children == null) return;
        for (Blockymodel child : model.children) {
            scaleBlockymodel(child, scale);
        }
    }

    private void rotateBlockymodel(Blockymodel model, double rads) {
        if (model.position != null) {
            rotateYVector3d(model.position, rads);
        }

        if (model.orientation != null) {
            var quat = new Quaterniond(model.orientation.x, model.orientation.y, model.orientation.z, model.orientation.w);
            quat.rotateLocalY(rads);
            model.orientation.x = quat.x;
            model.orientation.y = quat.y;
            model.orientation.z = quat.z;
            model.orientation.w = quat.w;
        }

        if (model.children == null) return;
        for (Blockymodel child : model.children) rotateBlockymodel(child, rads);
    }

    public int countNodes(BlockymodelBase base) {
        int counter = 1;
        Blockymodel[] nodes = base.getNodes();

        if (nodes == null || nodes.length == 0) return 0;

        for (Blockymodel node : nodes) {
            counter += countNodes(node);
        }

        return counter;
    }

    public int countNodes(Blockymodel blockyNode) {
        int counter = 1;
        Blockymodel[] nodes = blockyNode.children;

        if (nodes == null) return counter;

        for (Blockymodel node : nodes) {
            counter += countNodes(node);
        }
        return counter;
    }

    public void addAttachments(Model model, BaseModel base) {
        ModelAttachment[] attachments = model.attachedModels();
        if (attachments == null) return;

        for (ModelAttachment attachment : attachments) {
            BlockymodelBase blockymodelBase = BlockymodelService.get().loadBlockymodelBase(attachment.getModel());
            if (blockymodelBase == null) continue;

            Blockymodel[] nodes = blockymodelBase.getNodes();
            for (Blockymodel node : nodes) {
                TextureService.get().handleTexture(node, attachment.getTexture(), base);
                model.blockymodel().addNode(node);
            }
        }
    }
}
