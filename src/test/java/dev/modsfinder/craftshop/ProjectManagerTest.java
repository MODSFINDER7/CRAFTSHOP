package dev.modsfinder.craftshop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectManagerTest {
    @Test
    void removesNullEntriesAndRepairsDuplicateObjectIds() throws Exception {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject first = object("duplicate");
        SceneProject.SceneObject second = object("duplicate");
        project.objects.add(first);
        project.objects.add(null);
        project.objects.add(second);

        ProjectManager.normalizeProject(project);

        assertEquals(2, project.objects.size());
        assertNotEquals(first.id, second.id);
        assertEquals("duplicate", first.id);
        assertDoesNotThrow(() -> java.util.UUID.fromString(second.id));
    }

    @Test
    void clampsNonFiniteAndOutOfRangeCameraAndTransformValues() throws Exception {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject object = object("safe");
        project.objects.add(object);
        project.camera.x = Float.NaN;
        project.camera.pitch = 180.0F;
        project.camera.zoom = Float.POSITIVE_INFINITY;
        project.lightPitch = -50.0F;
        project.ambientLight = 2.0F;
        object.transform.x = Float.NEGATIVE_INFINITY;
        object.transform.rotationY = 900000.0F;
        object.transform.scaleX = 0.001F;
        object.transform.scaleY = Float.NaN;
        object.transform.scaleZ = 100.0F;

        ProjectManager.normalizeProject(project);

        assertEquals(0.0F, project.camera.x);
        assertEquals(89.0F, project.camera.pitch);
        assertEquals(1.0F, project.camera.zoom);
        assertEquals(10.0F, project.lightPitch);
        assertEquals(0.95F, project.ambientLight);
        assertEquals(0.0F, object.transform.x);
        assertEquals(360000.0F, object.transform.rotationY);
        assertEquals(0.05F, object.transform.scaleX);
        assertEquals(1.0F, object.transform.scaleY);
        assertEquals(20.0F, object.transform.scaleZ);
    }

    @Test
    void repairsMissingFieldsAndClearsInvalidSelection() throws Exception {
        SceneProject project = new SceneProject();
        project.name = " ";
        project.canvasImage = null;
        project.renderedImage = null;
        project.selectedObjectId = "missing";
        project.camera = null;
        project.objects = null;
        project.formatVersion = 0;

        ProjectManager.normalizeProject(project);

        assertEquals("Untitled Scene", project.name);
        assertEquals("", project.canvasImage);
        assertEquals("", project.renderedImage);
        assertEquals("", project.selectedObjectId);
        assertNotNull(project.camera);
        assertNotNull(project.objects);
        assertEquals(4, project.formatVersion);
    }

    @Test
    void preservesValidSelectionAndNormalizesObjectDefaults() throws Exception {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject object = object("selected");
        object.name = " ";
        object.kind = null;
        object.assetId = null;
        object.transform = null;
        project.objects.add(object);
        project.selectedObjectId = object.id;

        ProjectManager.normalizeProject(project);

        assertEquals(object.id, project.selectedObjectId);
        assertEquals("Object", object.name);
        assertEquals("mesh_cube", object.kind);
        assertEquals("minecraft:stone", object.assetId);
        assertNotNull(object.transform);
    }

    @Test
    void initializesMissingBlockStatePropertiesWithoutDiscardingSavedVariants() throws Exception {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject legacy = object("legacy");
        legacy.blockProperties = null;
        SceneProject.SceneObject imported = object("imported");
        imported.blockProperties.put("facing", "north");
        imported.blockProperties.put("waterlogged", "true");
        project.objects.add(legacy);
        project.objects.add(imported);

        ProjectManager.normalizeProject(project);

        assertNotNull(legacy.blockProperties);
        assertTrue(legacy.blockProperties.isEmpty());
        assertEquals("north", imported.blockProperties.get("facing"));
        assertEquals("true", imported.blockProperties.get("waterlogged"));
    }

    private static SceneProject.SceneObject object(String id) {
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.id = id;
        return object;
    }
}
