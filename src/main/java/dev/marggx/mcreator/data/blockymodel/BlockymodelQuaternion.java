package dev.marggx.mcreator.data.blockymodel;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import org.joml.Quaterniond;

import javax.annotation.Nonnull;

public class BlockymodelQuaternion {
    @Nonnull
    public static final BuilderCodec<BlockymodelQuaternion> CODEC = BuilderCodec.builder(BlockymodelQuaternion.class, BlockymodelQuaternion::new)
            .append(new KeyedCodec<>("x", Codec.DOUBLE, true, true), (i, v) -> i.x = v, i -> i.x)
            .add()
            .append(new KeyedCodec<>("y", Codec.DOUBLE, true, true), (i, v) -> i.y = v, i -> i.y)
            .add()
            .append(new KeyedCodec<>("z", Codec.DOUBLE, true, true), (i, v) -> i.z = v, i -> i.z)
            .add()
            .append(new KeyedCodec<>("w", Codec.DOUBLE, true, true), (i, v) -> i.w = v, i -> i.w)
            .add()
            .build();

    public double x;
    public double y;
    public double z;
    public double w;

    public BlockymodelQuaternion() {
    }

    public BlockymodelQuaternion(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public static BlockymodelQuaternion fromVector3f(Vector3f vector3f) {
        return fromVector3d(vector3f.toVector3d());
    }

    public static BlockymodelQuaternion fromVector3d(Vector3d vector3d) {
        Vector3d vec = vector3d.clone();
        rotateYVector3d(vec, Math.PI);
        Quaterniond originalQuat = new Quaterniond().rotationYXZ(vec.getY(), vec.getX(), vec.getZ());
        return new BlockymodelQuaternion(originalQuat.x(), originalQuat.y(), originalQuat.z(), originalQuat.w());
    }

    public static Vector3d rotateYVector3d(Vector3d v, double rads) {
        double cos = Math.cos(rads);
        double sin = Math.sin(rads);
        double cx = v.x * cos + v.z * sin;
        double cz = v.x * -sin + v.z * cos;
        v.x = cx;
        v.z = cz;
        return v;
    }

    public static BlockymodelQuaternion getLocalQuat(Vector3f baseRotation, Vector3f toLocalRotation) {
        Vector3d baseOrientation = rotateYVector3d(baseRotation.toVector3d(), Math.PI);
        Vector3d toLocal = rotateYVector3d(toLocalRotation.toVector3d(), Math.PI);
        Quaterniond originalQuat = new Quaterniond().rotationYXZ(baseOrientation.getY(), baseOrientation.getX(), baseOrientation.getZ());
        originalQuat.invert();
        Quaterniond toLocalQuat = new Quaterniond().rotationYXZ(toLocal.getY(), toLocal.getX(), toLocal.getZ());
        Quaterniond result = originalQuat.mul(toLocalQuat);
        return new BlockymodelQuaternion(result.x(), result.y(), result.z(), result.w());
    }
}
