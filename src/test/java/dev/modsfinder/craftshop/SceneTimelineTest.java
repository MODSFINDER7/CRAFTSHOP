package dev.modsfinder.craftshop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneTimelineTest {
    @Test
    void interpolatesPositionScaleAndShortestRotationPath() {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.id = "animated";
        project.objects.add(object);

        object.transform.x = 0.0F;
        object.transform.rotationY = 350.0F;
        object.transform.scaleX = 1.0F;
        SceneTimeline.setKeyframe(project, object.id, 0);

        object.transform.x = 10.0F;
        object.transform.rotationY = 10.0F;
        object.transform.scaleX = 3.0F;
        SceneTimeline.setKeyframe(project, object.id, 10);

        SceneTimeline.applyFrame(project, 5);

        assertEquals(5.0F, object.transform.x, 0.0001F);
        assertEquals(360.0F, object.transform.rotationY, 0.0001F);
        assertEquals(2.0F, object.transform.scaleX, 0.0001F);
        assertEquals(5, project.timelineFrame);
    }

    @Test
    void replacesKeyframeAtSameFrameAndSupportsRemoval() {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.id = "animated";
        project.objects.add(object);

        object.transform.x = 2.0F;
        SceneTimeline.setKeyframe(project, object.id, 12);
        object.transform.x = 8.0F;
        SceneTimeline.setKeyframe(project, object.id, 12);

        assertEquals(1, project.keyframes.size());
        assertEquals(8.0F, project.keyframes.get(0).transform.x);
        assertTrue(SceneTimeline.removeKeyframe(project, object.id, 12));
        assertFalse(SceneTimeline.hasKeyframe(project, object.id, 12));
    }

    @Test
    void clampsFramesAndDropsOrphanKeyframes() {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.id = "valid";
        project.objects.add(object);

        SceneProject.Keyframe valid = new SceneProject.Keyframe();
        valid.objectId = "valid";
        valid.frame = 9000;
        valid.transform.x = Float.NaN;
        project.keyframes.add(valid);

        SceneProject.Keyframe orphan = new SceneProject.Keyframe();
        orphan.objectId = "deleted-object";
        project.keyframes.add(orphan);

        SceneTimeline.normalize(project);

        assertEquals(1, project.keyframes.size());
        assertEquals(2400, project.keyframes.get(0).frame);
        assertEquals(0.0F, project.keyframes.get(0).transform.x);
    }

    @Test
    void holdsFirstAndLastKeyframeOutsideAnimatedRange() {
        SceneProject project = new SceneProject();
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.id = "animated";
        project.objects.add(object);

        object.transform.x = 4.0F;
        SceneTimeline.setKeyframe(project, object.id, 20);
        object.transform.x = 14.0F;
        SceneTimeline.setKeyframe(project, object.id, 40);

        SceneTimeline.applyFrame(project, 0);
        assertEquals(4.0F, object.transform.x);
        SceneTimeline.applyFrame(project, 80);
        assertEquals(14.0F, object.transform.x);
    }
}
