package dev.modsfinder.craftshop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneWorldTest {
    private SceneProject project;

    @AfterEach
    void clearCache() {
        SceneWorld.invalidate(project);
    }

    @Test
    void indexesBlocksAndEntitiesIntoSixteenBlockSections() {
        project = new SceneProject();
        SceneProject.SceneObject block = object("block-1", "minecraft_block", 1.0F, 2.0F, 3.0F);
        SceneProject.SceneObject entity = object("entity-1", "minecraft_entity", 17.0F, 18.0F, 19.0F);
        project.objects.add(block);
        project.objects.add(entity);

        SceneWorld world = SceneWorld.from(project);

        assertEquals(2, world.objectCount());
        assertEquals(2, world.chunkCount());
        assertEquals(1, world.blockCount());
        assertEquals(1, world.entityCount());
        assertEquals(List.of(block), world.chunkAt(1, 2, 3).objects());
        assertEquals(List.of(entity), world.chunkAt(17, 18, 19).objects());
    }

    @Test
    void usesFloorDivisionForNegativeChunkCoordinates() {
        project = new SceneProject();
        SceneProject.SceneObject object = object("negative", "minecraft_block", -0.1F, -16.1F, -16.0F);
        project.objects.add(object);

        SceneWorld world = SceneWorld.from(project);

        assertEquals(List.of(object), world.chunkAt(-1, -17, -16).objects());
        assertNull(world.chunkAt(0, 0, 0));
    }

    @Test
    void ignoresNullObjectsAndObjectsWithoutTransforms() {
        project = new SceneProject();
        project.objects.add(null);
        SceneProject.SceneObject invalid = object("invalid", "minecraft_block", 0, 0, 0);
        invalid.transform = null;
        project.objects.add(invalid);

        SceneWorld world = SceneWorld.from(project);

        assertEquals(0, world.objectCount());
        assertEquals(0, world.chunkCount());
        assertTrue(world.renderObjects(new SceneProject.Camera()).isEmpty());
    }

    @Test
    void hiddenObjectsStayIndexedButAreNotReturnedForRendering() {
        project = new SceneProject();
        SceneProject.SceneObject visible = object("visible", "minecraft_block", 0, 0, -4);
        SceneProject.SceneObject hidden = object("hidden", "minecraft_block", 0, 0, -5);
        hidden.visible = false;
        project.objects.add(visible);
        project.objects.add(hidden);
        SceneProject.Camera camera = cameraAtOrigin();

        SceneWorld world = SceneWorld.from(project);

        assertEquals(2, world.objectCount());
        assertEquals(1, world.renderObjects(camera).size());
        assertEquals("visible", world.renderObjects(camera).getFirst().id);
    }

    @Test
    void excludesObjectsBehindCamera() {
        project = new SceneProject();
        SceneProject.SceneObject inFront = object("front", "minecraft_block", 0, 0, -5);
        SceneProject.SceneObject behind = object("behind", "minecraft_block", 0, 0, 5);
        project.objects.add(inFront);
        project.objects.add(behind);

        List<SceneProject.SceneObject> rendered = SceneWorld.from(project).renderObjects(cameraAtOrigin());

        assertEquals(List.of(inFront), rendered);
    }

    @Test
    void rebuildsChunkIndexWhenObjectMovesAcrossChunkBoundary() {
        project = new SceneProject();
        SceneProject.SceneObject object = object("moving", "minecraft_block", 15.9F, 0, -4);
        project.objects.add(object);
        SceneWorld first = SceneWorld.from(project);

        object.transform.x = 16.1F;
        SceneWorld second = SceneWorld.from(project);

        assertNotSame(first, second);
        assertNull(second.chunkAt(15, 0, -4));
        assertEquals(List.of(object), second.chunkAt(16, 0, -4).objects());
    }

    @Test
    void reusesIndexWhenObjectMovesInsideSameChunk() {
        project = new SceneProject();
        SceneProject.SceneObject object = object("moving", "minecraft_block", 1, 0, -4);
        project.objects.add(object);
        SceneWorld first = SceneWorld.from(project);

        object.transform.x = 2;
        SceneWorld second = SceneWorld.from(project);

        assertSame(first, second);
        assertEquals(List.of(object), second.renderObjects(cameraAtOrigin()));
    }

    @Test
    void rebuildsIndexWhenObjectCollectionChanges() {
        project = new SceneProject();
        SceneProject.SceneObject firstObject = object("first", "minecraft_block", 0, 0, -4);
        project.objects.add(firstObject);
        SceneWorld first = SceneWorld.from(project);
        SceneProject.SceneObject secondObject = object("second", "minecraft_entity", 0, 0, -6);
        project.objects.add(secondObject);

        SceneWorld second = SceneWorld.from(project);

        assertNotSame(first, second);
        assertEquals(2, second.objectCount());
        assertEquals(1, second.entityCount());
    }


    @Test
    void sortsTranslucentSceneObjectsByCameraDepthRatherThanRadialDistance() {
        project = new SceneProject();
        SceneProject.SceneObject deeper = object("deeper", "minecraft_block", 0, 0, -5);
        SceneProject.SceneObject nearerButOffset = object("offset", "minecraft_block", 4, 0, -4);
        project.objects.add(nearerButOffset);
        project.objects.add(deeper);

        List<SceneProject.SceneObject> rendered = SceneWorld.from(project).renderObjects(cameraAtOrigin());

        assertEquals(List.of(deeper, nearerButOffset), rendered);
    }

    @Test
    void excludesObjectsFarOutsideTheHorizontalCameraFrustum() {
        project = new SceneProject();
        SceneProject.SceneObject centered = object("centered", "minecraft_block", 0, 0, -5);
        SceneProject.SceneObject outside = object("outside", "minecraft_block", 100, 0, -5);
        project.objects.add(centered);
        project.objects.add(outside);

        List<SceneProject.SceneObject> rendered = SceneWorld.from(project).renderObjects(cameraAtOrigin());

        assertEquals(List.of(centered), rendered);
    }

    @Test
    void followsCameraYawWhenCullingObjects() {
        project = new SceneProject();
        SceneProject.SceneObject inFront = object("yaw-front", "minecraft_block", -5, 0, 0);
        SceneProject.SceneObject behind = object("yaw-behind", "minecraft_block", 5, 0, 0);
        project.objects.add(inFront);
        project.objects.add(behind);
        SceneProject.Camera camera = cameraAtOrigin();
        camera.yaw = 90.0F;

        List<SceneProject.SceneObject> rendered = SceneWorld.from(project).renderObjects(camera);

        assertEquals(List.of(inFront), rendered);
    }

    @Test
    void rejectsObjectsOutsideTheVerticalCameraFrustum() {
        project = new SceneProject();
        SceneProject.SceneObject centered = object("centered", "minecraft_block", 0, 0, -5);
        SceneProject.SceneObject above = object("above", "minecraft_block", 0, 100, -5);
        project.objects.add(centered);
        project.objects.add(above);

        List<SceneProject.SceneObject> rendered = SceneWorld.from(project).renderObjects(cameraAtOrigin());

        assertEquals(List.of(centered), rendered);
    }

    @Test
    void followsCameraPitchWhenCullingObjects() {
        project = new SceneProject();
        SceneProject.SceneObject inFront = object("pitch-front", "minecraft_block", 0, -5, 0);
        SceneProject.SceneObject behind = object("pitch-behind", "minecraft_block", 0, 5, 0);
        project.objects.add(inFront);
        project.objects.add(behind);
        SceneProject.Camera camera = cameraAtOrigin();
        camera.pitch = 90.0F;

        List<SceneProject.SceneObject> rendered = SceneWorld.from(project).renderObjects(camera);

        assertEquals(List.of(inFront), rendered);
    }

    private static SceneProject.Camera cameraAtOrigin() {
        SceneProject.Camera camera = new SceneProject.Camera();
        camera.x = 0;
        camera.y = 0;
        camera.z = 0;
        camera.yaw = 0;
        camera.pitch = 0;
        return camera;
    }

    private static SceneProject.SceneObject object(String id, String kind,
                                                    float x, float y, float z) {
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.id = id;
        object.kind = kind;
        object.transform.x = x;
        object.transform.y = y;
        object.transform.z = z;
        return object;
    }
}
