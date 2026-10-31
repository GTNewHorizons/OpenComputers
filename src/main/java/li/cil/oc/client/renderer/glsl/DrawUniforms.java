package li.cil.oc.client.renderer.glsl;

import java.nio.FloatBuffer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

final class DrawUniforms {

    private static final int LIGHTS = 2;

    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f modelView = new Matrix4f();
    private static final Matrix4f mvp = new Matrix4f();
    private static final Matrix3f normal = new Matrix3f();

    private static final FloatBuffer scratch = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer lights = BufferUtils.createFloatBuffer(LIGHTS * 3);

    private DrawUniforms() {}

    static void uploadMVP(int location, Matrix4fc model) {
        scratch.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, scratch);
        projection.set(scratch);
        scratch.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, scratch);
        modelView.set(scratch)
            .mul(model);
        upload(location, projection.mul(modelView, mvp));
    }

    static void uploadLit(int mvpLocation, int normalLocation, int lightDirLocation, int lightDiffuseLocation,
        Matrix4fc model) {
        uploadMVP(mvpLocation, model);

        scratch.clear();
        modelView.normal(normal)
            .get(scratch);
        scratch.limit(9);
        GL20.glUniformMatrix3(normalLocation, false, scratch);

        uploadLight(lightDirLocation, GL11.GL_POSITION);
        uploadLight(lightDiffuseLocation, GL11.GL_DIFFUSE);
    }

    private static void uploadLight(int location, int parameter) {
        lights.clear();
        for (int i = 0; i < LIGHTS; i++) {
            scratch.clear();
            GL11.glGetLight(GL11.GL_LIGHT0 + i, parameter, scratch);
            lights.put(scratch.get(0))
                .put(scratch.get(1))
                .put(scratch.get(2));
        }
        lights.flip();
        GL20.glUniform3(location, lights);
    }

    static void upload(int location, Matrix4fc matrix) {
        scratch.clear();
        matrix.get(scratch);
        GL20.glUniformMatrix4(location, false, scratch);
    }
}
