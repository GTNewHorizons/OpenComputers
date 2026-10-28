package li.cil.oc.client.renderer.glsl;

import java.util.Arrays;

import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;

public final class Program {

    private final ProgramRecipe recipe;
    private final GlslDialect dialect;

    private final int[] slots;

    private final int transformLocation;
    private final int normalLocation;
    private final int lightDirLocation;
    private final int lightDiffuseLocation;

    private int program;

    Program(ProgramRecipe recipe, GlslDialect dialect, int program, int[] slots, int transformLocation,
        int normalLocation, int lightDirLocation, int lightDiffuseLocation) {
        this.recipe = recipe;
        this.dialect = dialect;
        this.program = program;
        this.slots = slots;
        this.transformLocation = transformLocation;
        this.normalLocation = normalLocation;
        this.lightDirLocation = lightDirLocation;
        this.lightDiffuseLocation = lightDiffuseLocation;
    }

    static Program invalid(ProgramRecipe recipe) {
        final int[] slots = new int[recipe.uniformCount()];
        Arrays.fill(slots, -1);
        return new Program(recipe, GlslDialect.preferred(), 0, slots, -1, -1, -1, -1);
    }

    public boolean isValid() {
        return program != 0;
    }

    public void use() {
        if (program != 0) GL20.glUseProgram(program);
    }

    public int loc(Uniform uniform) {
        if (uniform.owner != recipe) {
            throw new IllegalArgumentException(recipe.id() + ": " + uniform + " belongs to another shader");
        }
        return slots[uniform.slot];
    }

    public void uploadModel(Matrix4fc model) {
        if (program == 0) return;

        if (dialect != GlslDialect.GLSL330) {
            DrawUniforms.upload(transformLocation, model);
        } else if (normalLocation < 0) {
            DrawUniforms.uploadMVP(transformLocation, model);
        } else {
            DrawUniforms.uploadLit(transformLocation, normalLocation, lightDirLocation, lightDiffuseLocation, model);
        }
    }

    public void release() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }
}
