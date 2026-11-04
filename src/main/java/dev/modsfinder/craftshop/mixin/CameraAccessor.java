package dev.modsfinder.craftshop.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setRotation")
    void craftshop$setRotation(float yaw, float pitch);

    @Invoker("setPosition")
    void craftshop$setPosition(double x, double y, double z);

    @Accessor("cullFrustum")
    void craftshop$setCullFrustum(Frustum frustum);

    @Accessor("detached")
    void craftshop$setDetached(boolean detached);
}
