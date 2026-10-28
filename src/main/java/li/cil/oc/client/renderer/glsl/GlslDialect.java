package li.cil.oc.client.renderer.glsl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

// GLSL330 sources must stay free of compat builtins and of angelica_*/iris_* uniform names
public enum GlslDialect {

    GLSL120("#version 120\n"),
    GLSL330("#version 330 core\n" + "#define OC_CORE\n");

    static final Logger LOG = LogManager.getLogger("OpenComputers");

    private static GlslDialect preferred;

    private final String preamble;

    GlslDialect(String preamble) {
        this.preamble = preamble;
    }

    public String source(String defines, String prelude, String body) {
        return preamble + defines + prelude + body;
    }

    public static void init() {
        if (preferred != null) return;
        final ContextCapabilities capabilities = GLContext.getCapabilities();
        final boolean coreContext = capabilities.OpenGL32
            && (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_CORE_PROFILE_BIT) != 0;
        preferred = coreContext ? GLSL330 : GLSL120;
        LOG.info("Preferred GLSL dialect: {}", preferred);
    }

    public static GlslDialect preferred() {
        if (preferred == null) throw new IllegalStateException("GlslDialect.init() has not run");
        return preferred;
    }
}
