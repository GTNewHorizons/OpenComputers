package li.cil.oc.client.renderer.glsl;

import static li.cil.oc.client.renderer.glsl.GlslDialect.LOG;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public final class ProgramRecipe {

    private final String domain;
    private final String name;
    private final List<String> uniforms = new ArrayList<>();
    private final Object2IntOpenHashMap<String> samplers = new Object2IntOpenHashMap<>();
    private final Object2FloatOpenHashMap<String> constants = new Object2FloatOpenHashMap<>();
    private final StringBuilder defines = new StringBuilder();

    private boolean tinted;
    private boolean lit;

    private ProgramRecipe(String domain, String name) {
        this.domain = domain;
        this.name = name;
    }

    public static ProgramRecipe of(String domain, String name) {
        return new ProgramRecipe(domain, name);
    }

    public ProgramRecipe required(String... names) {
        for (String uniform : names) {
            if (uniforms.contains(uniform)) {
                throw new IllegalArgumentException(id() + ": " + uniform + " is declared twice");
            }
        }
        Collections.addAll(uniforms, names);
        return this;
    }

    public ProgramRecipe sampler(String uniform, int unit) {
        samplers.put(uniform, unit);
        return required(uniform);
    }

    public ProgramRecipe constant(String uniform, float value) {
        constants.put(uniform, value);
        return required(uniform);
    }

    public ProgramRecipe define(String macro, float value) {
        defines.append("#define ")
            .append(macro)
            .append(' ')
            .append(value)
            .append('\n');
        return this;
    }

    public ProgramRecipe tinted() {
        this.tinted = true;
        return this;
    }

    public ProgramRecipe lights() {
        this.tinted = true;
        this.lit = true;
        return this;
    }

    public Uniform uniform(String name) {
        final int slot = uniforms.indexOf(name);
        if (slot < 0) {
            throw new IllegalArgumentException(
                id() + ": " + name + " was not declared; declared: " + String.join(", ", uniforms));
        }
        return new Uniform(this, name, slot);
    }

    public String name() {
        return name;
    }

    public List<String> expectedUniforms(GlslDialect dialect) {
        final boolean core = dialect == GlslDialect.GLSL330;
        final List<String> expected = new ArrayList<>(uniforms);
        expected.add(core ? "u_MVP" : "u_ModelMatrix");
        if (lit && core) {
            expected.add("u_NormalMatrix");
            expected.add("u_LightDir");
            expected.add("u_LightDiffuse");
        }
        return expected;
    }

    public String assemble(GlslDialect dialect, String prelude, String body) {
        return dialect.source((tinted ? "#define OC_TINTED\n" : "") + (lit ? "#define OC_LIT\n" : "") + defines, prelude, body);
    }

    public Program bake() {
        final GlslDialect dialect = GlslDialect.preferred();
        final int program = link(dialect);
        final Program handle = program == 0 ? null : resolve(dialect, program);
        if (handle == null) {
            if (program != 0) GL20.glDeleteProgram(program);
            LOG.error("Shader {} [{}]: unavailable, falling back to fixed-function rendering", id(), dialect);
            return Program.invalid(this);
        }
        uploadConstants(handle);
        LOG.info("Shader {} [{}]: linked", id(), dialect);
        return handle;
    }

    private int link(GlslDialect dialect) {
        final String vertexBody = read("shaders/" + name + ".vert");
        final String fragmentBody = read("shaders/" + name + ".frag");
        final String vertexPrelude = read("shaders/standalone.vert");
        final String fragmentPrelude = read("shaders/standalone.frag");
        if (vertexBody == null || fragmentBody == null || vertexPrelude == null || fragmentPrelude == null) return 0;

        final int vertex = compile(dialect, GL20.GL_VERTEX_SHADER, assemble(dialect, vertexPrelude, vertexBody));
        final int fragment = compile(
            dialect,
            GL20.GL_FRAGMENT_SHADER,
            assemble(dialect, fragmentPrelude, fragmentBody));
        int program = 0;
        if (vertex != 0 && fragment != 0) {
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                LOG.error(
                    "Shader {} [{}]: link failed: {}",
                    id(),
                    dialect,
                    GL20.glGetProgramInfoLog(program, GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH)));
                GL20.glDeleteProgram(program);
                program = 0;
            }
        }
        if (vertex != 0) GL20.glDeleteShader(vertex);
        if (fragment != 0) GL20.glDeleteShader(fragment);
        return program;
    }

    private int compile(GlslDialect dialect, int type, String source) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error(
                "Shader {} [{}]: {} stage did not compile: {}",
                id(),
                dialect,
                type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment",
                GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH)));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private String read(String path) {
        final ResourceLocation location = new ResourceLocation(domain, path);
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(location)
            .getInputStream()) {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Shader {}: could not read {}", id(), location, e);
            return null;
        }
    }

    private Program resolve(GlslDialect dialect, int program) {
        final List<String> expected = expectedUniforms(dialect);
        final int[] locations = new int[expected.size()];
        boolean ok = true;
        for (int i = 0; i < locations.length; i++) {
            locations[i] = locate(dialect, program, expected.get(i));
            ok &= locations[i] >= 0;
        }
        if (!ok) return null;

        final int declared = uniforms.size();
        final int[] slots = new int[declared];
        System.arraycopy(locations, 0, slots, 0, declared);
        final boolean lighting = locations.length > declared + 1;
        return new Program(
            this,
            dialect,
            program,
            slots,
            locations[declared],
            lighting ? locations[declared + 1] : -1,
            lighting ? locations[declared + 2] : -1,
            lighting ? locations[declared + 3] : -1);
    }

    private int locate(GlslDialect dialect, int program, String uniform) {
        final int location = GL20.glGetUniformLocation(program, uniform);
        if (location < 0) {
            LOG.error("Shader {} [{}]: uniform {} is missing or unused", id(), dialect, uniform);
        }
        return location;
    }

    private void uploadConstants(Program handle) {
        if (samplers.isEmpty() && constants.isEmpty()) return;
        final int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        handle.use();
        for (Object2IntMap.Entry<String> sampler : samplers.object2IntEntrySet()) {
            GL20.glUniform1i(handle.loc(uniform(sampler.getKey())), sampler.getIntValue());
        }
        for (Object2FloatMap.Entry<String> constant : constants.object2FloatEntrySet()) {
            GL20.glUniform1f(handle.loc(uniform(constant.getKey())), constant.getFloatValue());
        }
        GL20.glUseProgram(previous);
    }

    String id() {
        return domain + ":" + name;
    }

    int uniformCount() {
        return uniforms.size();
    }
}
