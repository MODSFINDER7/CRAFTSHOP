package dev.modsfinder.craftshop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SceneTimeline {
    private SceneTimeline() {}

    public static void normalize(SceneProject project) {
        if (project.keyframes == null) project.keyframes = new ArrayList<>();
        project.keyframes.removeIf(key -> key == null || key.objectId == null || key.objectId.isBlank()
                || key.transform == null || project.objects.stream().noneMatch(o -> key.objectId.equals(o.id)));
        for (SceneProject.Keyframe key : project.keyframes) {
            key.frame = Math.max(0, Math.min(2400, key.frame));
            normalizeTransform(key.transform);
        }
        project.keyframes.sort(Comparator.comparingInt((SceneProject.Keyframe k) -> k.frame)
                .thenComparing(k -> k.objectId));
        List<SceneProject.Keyframe> unique = new ArrayList<>();
        for (SceneProject.Keyframe key : project.keyframes) {
            unique.removeIf(old -> old.frame == key.frame && old.objectId.equals(key.objectId));
            unique.add(key);
        }
        unique.sort(Comparator.comparingInt((SceneProject.Keyframe k) -> k.frame)
                .thenComparing(k -> k.objectId));
        project.keyframes = unique;
        project.timelineFrame = Math.max(0, Math.min(2400, project.timelineFrame));
    }

    public static void setKeyframe(SceneProject project, String objectId, int frame) {
        if (project == null || objectId == null || objectId.isBlank()) return;
        SceneProject.SceneObject object = project.objects.stream()
                .filter(candidate -> objectId.equals(candidate.id)).findFirst().orElse(null);
        if (object == null || object.transform == null) return;
        if (project.keyframes == null) project.keyframes = new ArrayList<>();
        removeKeyframe(project, objectId, frame);
        SceneProject.Keyframe key = new SceneProject.Keyframe();
        key.objectId = objectId;
        key.frame = Math.max(0, Math.min(2400, frame));
        key.transform = copy(object.transform);
        project.keyframes.add(key);
        normalize(project);
    }

    public static boolean removeKeyframe(SceneProject project, String objectId, int frame) {
        if (project == null || project.keyframes == null) return false;
        return project.keyframes.removeIf(key -> key != null && objectId.equals(key.objectId) && key.frame == frame);
    }

    public static boolean hasKeyframe(SceneProject project, String objectId, int frame) {
        return project != null && project.keyframes != null && project.keyframes.stream()
                .anyMatch(key -> key != null && objectId.equals(key.objectId) && key.frame == frame);
    }

    public static void applyFrame(SceneProject project, int frame) {
        if (project == null || project.objects == null) return;
        normalize(project);
        int target = Math.max(0, Math.min(2400, frame));
        project.timelineFrame = target;
        for (SceneProject.SceneObject object : project.objects) {
            List<SceneProject.Keyframe> keys = project.keyframes.stream()
                    .filter(key -> object.id.equals(key.objectId))
                    .sorted(Comparator.comparingInt(key -> key.frame)).toList();
            if (keys.isEmpty()) continue;
            SceneProject.Keyframe before = null;
            SceneProject.Keyframe after = null;
            for (SceneProject.Keyframe key : keys) {
                if (key.frame <= target) before = key;
                if (key.frame >= target) { after = key; break; }
            }
            if (before == null) object.transform = copy(keys.get(0).transform);
            else if (after == null) object.transform = copy(keys.get(keys.size() - 1).transform);
            else if (before.frame == after.frame) object.transform = copy(before.transform);
            else {
                float amount = (target - before.frame) / (float) (after.frame - before.frame);
                object.transform = interpolate(before.transform, after.transform, amount);
            }
        }
    }

    private static SceneProject.Transform interpolate(SceneProject.Transform a, SceneProject.Transform b, float t) {
        SceneProject.Transform out = new SceneProject.Transform();
        out.x = lerp(a.x, b.x, t); out.y = lerp(a.y, b.y, t); out.z = lerp(a.z, b.z, t);
        out.rotationX = angle(a.rotationX, b.rotationX, t);
        out.rotationY = angle(a.rotationY, b.rotationY, t);
        out.rotationZ = angle(a.rotationZ, b.rotationZ, t);
        out.scaleX = lerp(a.scaleX, b.scaleX, t); out.scaleY = lerp(a.scaleY, b.scaleY, t);
        out.scaleZ = lerp(a.scaleZ, b.scaleZ, t);
        return out;
    }

    private static float angle(float a, float b, float t) {
        float delta = ((b - a + 540.0F) % 360.0F) - 180.0F;
        return a + delta * t;
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static SceneProject.Transform copy(SceneProject.Transform t) {
        SceneProject.Transform out = new SceneProject.Transform();
        out.x=t.x; out.y=t.y; out.z=t.z; out.rotationX=t.rotationX; out.rotationY=t.rotationY;
        out.rotationZ=t.rotationZ; out.scaleX=t.scaleX; out.scaleY=t.scaleY; out.scaleZ=t.scaleZ;
        return out;
    }
    private static void normalizeTransform(SceneProject.Transform t) {
        t.x=finite(t.x,0,-100000,100000); t.y=finite(t.y,0,-100000,100000); t.z=finite(t.z,0,-100000,100000);
        t.rotationX=finite(t.rotationX,0,-360000,360000); t.rotationY=finite(t.rotationY,0,-360000,360000);
        t.rotationZ=finite(t.rotationZ,0,-360000,360000);
        t.scaleX=finite(t.scaleX,1,0.05F,20); t.scaleY=finite(t.scaleY,1,0.05F,20);
        t.scaleZ=finite(t.scaleZ,1,0.05F,20);
    }
    private static float finite(float v,float fallback,float min,float max) {
        return Float.isFinite(v) ? Math.max(min,Math.min(max,v)) : fallback;
    }
}
