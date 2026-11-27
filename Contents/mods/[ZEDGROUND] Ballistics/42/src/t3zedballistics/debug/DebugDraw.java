package t3zedballistics.debug;

import static org.lwjgl.opengl.GL33.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.joml.Matrix4f;

import t3zedballistics.Log;

/**
 * Draws BallisticsDebug lines (and tracer rounds) inside Viewpoint's 3D view. Runs on the render thread
 * at the end of viewpoint.render.WorldRenderer.drawWeather(SceneData, ...), the same hook and GL state
 * handling used by the published "Project Viewpoint - 3D Tracer Fix" add-on. Every piece of GL state it
 * touches is saved and restored. Skipped when an Iris shader pack draws the frame (different targets).
 *
 * Only loaded on clients with Viewpoint; any failure disables it for the session.
 */
public final class DebugDraw {
    private static volatile boolean failed;
    private static int program, vao;
    private static Field irisDrawn, frameField, fViewProjection, fView, fEyeX, fEyeY, fEyeZ;
    private static Method bindTarget;
    private static final float[] matrix = new float[16];

    private static void init() throws ReflectiveOperationException {
        if (bindTarget != null) return;
        Class<?> renderer = Class.forName("viewpoint.render.WorldRenderer");
        Class<?> context = Class.forName("viewpoint.render.FrameContext");
        irisDrawn = renderer.getDeclaredField("irisDrawn");
        irisDrawn.setAccessible(true);
        frameField = renderer.getDeclaredField("frame");
        frameField.setAccessible(true);
        fViewProjection = context.getField("viewProjection");
        fView = context.getField("view");
        fEyeX = context.getField("eyeX");
        fEyeY = context.getField("eyeY");
        fEyeZ = context.getField("eyeZ");
        bindTarget = renderer.getMethod("bindTarget");
    }

    public static void draw(Object scene) {
        if (failed || scene == null) return;
        float[] items = BallisticsDebug.snapshots.get(scene);
        if (items == null || items.length == 0) return;
        try {
            init();
            if (irisDrawn.getBoolean(null)) return;
            Object ctx = frameField.get(null);
            Matrix4f vp = (Matrix4f) fViewProjection.get(ctx);
            Matrix4f view = (Matrix4f) fView.get(ctx);
            if (vp == null || view == null) return;
            float ex = fEyeX.getFloat(ctx), ey = fEyeY.getFloat(ctx), ez = fEyeZ.getFloat(ctx);

            int oldProgram = glGetInteger(GL_CURRENT_PROGRAM), oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int fbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE),
                scissor = glIsEnabled(GL_SCISSOR_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
            int df = glGetInteger(GL_DEPTH_FUNC), sr = glGetInteger(GL_BLEND_SRC_RGB), dr = glGetInteger(GL_BLEND_DST_RGB),
                sa = glGetInteger(GL_BLEND_SRC_ALPHA), da = glGetInteger(GL_BLEND_DST_ALPHA),
                er = glGetInteger(GL_BLEND_EQUATION_RGB), ea = glGetInteger(GL_BLEND_EQUATION_ALPHA);
            try {
                initGL();
                bindTarget.invoke(null);
                glUseProgram(program);
                glBindVertexArray(vao);
                glEnable(GL_BLEND);
                glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LEQUAL);
                glDepthMask(false);
                glDisable(GL_CULL_FACE);
                glDisable(GL_SCISSOR_TEST);
                vp.get(matrix);
                glUniformMatrix4fv(glGetUniformLocation(program, "vp"), false, matrix);
                glUniform3f(glGetUniformLocation(program, "eye"), ex, ey, ez);
                int uA = glGetUniformLocation(program, "a"), uB = glGetUniformLocation(program, "b"),
                    uTint = glGetUniformLocation(program, "tint");
                for (int o = 0; o + 9 < items.length; o += 10) {
                    glUniform3f(uA, items[o], items[o + 1], items[o + 2]);
                    glUniform3f(uB, items[o + 3], items[o + 4], items[o + 5]);
                    glUniform4f(uTint, items[o + 6], items[o + 7], items[o + 8], items[o + 9]);
                    glDrawArrays(GL_TRIANGLES, 0, 6);
                }
            } finally {
                glUseProgram(oldProgram);
                glBindVertexArray(oldVao);
                glBlendEquationSeparate(er, ea);
                glBlendFuncSeparate(sr, dr, sa, da);
                toggle(GL_BLEND, blend);
                glDepthFunc(df);
                glDepthMask(mask);
                toggle(GL_DEPTH_TEST, depth);
                toggle(GL_CULL_FACE, cull);
                toggle(GL_SCISSOR_TEST, scissor);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
                glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
        } catch (Throwable t) {
            failed = true;
            Log.error("3D debug drawing disabled for this session", t);
        }
    }

    private static void toggle(int cap, boolean on) {
        if (on) glEnable(cap); else glDisable(cap);
    }

    private static int shader(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(s);
            glDeleteShader(s);
            throw new IllegalStateException(log);
        }
        return s;
    }

    private static void initGL() {
        if (program != 0) return;
        int vs = shader(GL_VERTEX_SHADER, """
            #version 330 core
            uniform mat4 vp;
            uniform vec3 a;
            uniform vec3 b;
            uniform vec3 eye;
            out vec2 uv;
            const vec2 corners[6]=vec2[6](vec2(0,0),vec2(1,0),vec2(1,1),vec2(0,0),vec2(1,1),vec2(0,1));
            void main(){
              vec2 q=corners[gl_VertexID];
              vec3 dir=b-a;
              vec3 mid=(a+b)*0.5;
              vec3 side=cross(dir,eye-mid);
              if(dot(side,side)<1e-10) side=vec3(0,1,0);
              side=normalize(side);
              float w=0.012*(1.0+length(eye-mid)*0.04);
              vec3 p=mix(a,b,q.x)+side*(q.y-0.5)*w;
              gl_Position=vp*vec4(p,1);
              uv=q;
            }
            """);
        int fs = shader(GL_FRAGMENT_SHADER, """
            #version 330 core
            uniform vec4 tint;
            in vec2 uv;
            out vec4 color;
            void main(){
              float across=1.0-abs(uv.y*2.0-1.0);
              color=vec4(tint.rgb, tint.a*clamp(across*2.0,0.0,1.0));
            }
            """);
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(p);
            glDeleteProgram(p);
            throw new IllegalStateException(log);
        }
        program = p;
        vao = glGenVertexArrays();
    }

    private DebugDraw() {}
}
