package dev.modsfinder.craftshop;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SceneProject {
    public int formatVersion = 3;
    public String canvasImage = "";
    public String renderedImage = "";
    public String selectedObjectId = "";
    public String name = "Untitled Scene";
    public boolean ambientOcclusion = true;
    public float lightYaw = 45.0F;
    public float lightPitch = 73.0F;
    public float ambientLight = 0.58F;
    public Camera camera = new Camera();
    public List<SceneObject> objects = new ArrayList<>();

    public static final class Camera {
        public float x = 0.0F;
        public float y = 2.0F;
        public float z = 8.0F;
        public float yaw = 0.0F;
        public float pitch = 12.0F;
        public float zoom = 1.0F;
        public float targetX = 0.0F;
        public float targetY = 0.0F;
        public float targetZ = 0.0F;
    }

    public static final class SceneObject {
        public String id = UUID.randomUUID().toString();
        public String name = "Object";
        public String kind = "mesh_cube";
        public String assetId = "minecraft:stone";
        public Map<String, String> blockProperties = new LinkedHashMap<>();
        public Transform transform = new Transform();
        public boolean visible = true;
        public boolean locked = false;
    }

    public static final class Transform {
        public float x = 0.0F;
        public float y = 0.0F;
        public float z = 0.0F;
        public float rotationX = 0.0F;
        public float rotationY = 0.0F;
        public float rotationZ = 0.0F;
        public float scaleX = 1.0F;
        public float scaleY = 1.0F;
        public float scaleZ = 1.0F;
    }
}
