package t3zedballistics.effects;

import static org.lwjgl.opengl.GL33.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import t3zedballistics.Log;

/**
 * Draws bullet holes and impact debris inside Viewpoint's 3D view, on the render thread at the end of
 * viewpoint.render.WorldRenderer.drawWeather (same hook and GL state discipline as the tracer drawing in
 * t3zedballistics.debug.DebugDraw: every piece of GL state touched is saved and restored).
 *
 *   holes   - one quad each on the hit surface, procedural (no texture files), multiplied into the lit scene
 *             (blend DST_COLOR, SRC_COLOR: 0.5 = unchanged, darker = hole/soot, lighter = freshly exposed
 *             material), so they are exactly as lit as the wall they are on. Depth tested, polygon offset.
 *   debris  - camera-facing quads; dust and chips alpha blended, sparks and glass glints additive
 *             (premultiplied alpha: one blend state for both). Depth tested, no depth writes.
 *
 * Skipped when a Minecraft (Iris) shader pack draws the frame - its targets are not Viewpoint's.
 */
public final class ImpactDraw {
    private static volatile boolean failed;
    private static int holeProgram, partProgram, vao, vbo;
    private static FloatBuffer buf = BufferUtils.createFloatBuffer(13 * 6 * 512);
    private static Field irisDrawn, frameField, fViewProjection, fView, fEyeX, fEyeY, fEyeZ;
    private static Method bindTarget;
    private static final float[] matrix = new float[16];
    static final int VERT = 13; // pos3 uv2 colour4 extra4

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
        float[] items = ImpactEffects.snapshots.get(scene);
        if (items == null || items.length < 2) return;
        int holes = (int) items[0], parts = (int) items[1];
        if (holes + parts == 0) return;
        try {
            init();
            if (irisDrawn.getBoolean(null)) return;
            Object ctx = frameField.get(null);
            Matrix4f vp = (Matrix4f) fViewProjection.get(ctx);
            Matrix4f view = (Matrix4f) fView.get(ctx);
            if (vp == null || view == null) return;
            float ex = fEyeX.getFloat(ctx), ey = fEyeY.getFloat(ctx), ez = fEyeZ.getFloat(ctx);

            int oldProgram = glGetInteger(GL_CURRENT_PROGRAM), oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int oldVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int fbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE),
                scissor = glIsEnabled(GL_SCISSOR_TEST), offset = glIsEnabled(GL_POLYGON_OFFSET_FILL),
                mask = glGetBoolean(GL_DEPTH_WRITEMASK);
            float offFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR), offUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS);
            int df = glGetInteger(GL_DEPTH_FUNC), sr = glGetInteger(GL_BLEND_SRC_RGB), dr = glGetInteger(GL_BLEND_DST_RGB),
                sa = glGetInteger(GL_BLEND_SRC_ALPHA), da = glGetInteger(GL_BLEND_DST_ALPHA),
                er = glGetInteger(GL_BLEND_EQUATION_RGB), ea = glGetInteger(GL_BLEND_EQUATION_ALPHA);
            try {
                initGL();
                bindTarget.invoke(null);
                glBindVertexArray(vao);
                glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LEQUAL);
                glDepthMask(false);
                glDisable(GL_CULL_FACE);
                glDisable(GL_SCISSOR_TEST);
                glEnable(GL_BLEND);
                glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
                vp.get(matrix);

                // ---- holes: multiplied into the scene
                if (holes > 0) {
                    int verts = buildHoles(items, holes);
                    upload(verts);
                    glUseProgram(holeProgram);
                    glUniformMatrix4fv(glGetUniformLocation(holeProgram, "vp"), false, matrix);
                    glBlendFuncSeparate(GL_DST_COLOR, GL_SRC_COLOR, GL_ZERO, GL_ONE);
                    glEnable(GL_POLYGON_OFFSET_FILL);
                    glPolygonOffset(-1f, -4f);
                    glDrawArrays(GL_TRIANGLES, 0, verts);
                    glDisable(GL_POLYGON_OFFSET_FILL);
                }
                // ---- debris: premultiplied alpha (alpha 0 = additive)
                if (parts > 0) {
                    int verts = buildParticles(items, holes, parts, view, ex, ey, ez);
                    upload(verts);
                    glUseProgram(partProgram);
                    glUniformMatrix4fv(glGetUniformLocation(partProgram, "vp"), false, matrix);
                    glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
                    glDrawArrays(GL_TRIANGLES, 0, verts);
                }
            } finally {
                glUseProgram(oldProgram);
                glBindVertexArray(oldVao);
                glBindBuffer(GL_ARRAY_BUFFER, oldVbo);
                glBlendEquationSeparate(er, ea);
                glBlendFuncSeparate(sr, dr, sa, da);
                toggle(GL_BLEND, blend);
                glDepthFunc(df);
                glDepthMask(mask);
                toggle(GL_DEPTH_TEST, depth);
                toggle(GL_CULL_FACE, cull);
                toggle(GL_SCISSOR_TEST, scissor);
                glPolygonOffset(offFactor, offUnits);
                toggle(GL_POLYGON_OFFSET_FILL, offset);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
                glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
        } catch (Throwable t) {
            failed = true;
            Log.error("bullet holes / impact debris drawing disabled for this session", t);
        }
    }

    private static void upload(int verts) {
        buf.flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STREAM_DRAW);
    }

    private static void ensure(int floats) {
        if (buf.capacity() < floats) buf = BufferUtils.createFloatBuffer(Math.max(floats, buf.capacity() * 2));
        buf.clear();
    }

    // ------------------------------------------------------------------ geometry (render thread)

    /** Holes: [cx,cy,cz, nx,ny,nz, size, seed, family, fade, core, -] per hole, render space. */
    static int buildHoles(float[] it, int holes) {
        ensure(holes * 6 * VERT);
        int o = 2;
        for (int h = 0; h < holes; h++, o += ImpactEffects.HOLE_FLOATS) {
            float cx = it[o], cy = it[o + 1], cz = it[o + 2];
            float nx = it[o + 3], ny = it[o + 4], nz = it[o + 5];
            float size = it[o + 6], seed = it[o + 7];
            // tangent basis on the surface (render space: y is up)
            float tx, ty, tz;
            if (Math.abs(ny) > 0.9f) { tx = 1; ty = 0; tz = 0; }
            else { tx = -nz; ty = 0; tz = nx; } // cross(n, up)
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl; ty /= tl; tz /= tl;
            float bx = ny * tz - nz * ty, by = nz * tx - nx * tz, bz = nx * ty - ny * tx; // cross(n, t)
            double a = seed * 2.399; // random in-plane rotation
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float ux = (tx * ca + bx * sa) * size, uy = (ty * ca + by * sa) * size, uz = (tz * ca + bz * sa) * size;
            float vx = (bx * ca - tx * sa) * size, vy = (by * ca - ty * sa) * size, vz = (bz * ca - tz * sa) * size;
            float r = it[o + 8], g = it[o + 9], b = it[o + 10]; // family, fade, core
            corner(cx, cy, cz, ux, uy, uz, vx, vy, vz, -1, -1, r, g, b, seed);
            corner(cx, cy, cz, ux, uy, uz, vx, vy, vz, 1, -1, r, g, b, seed);
            corner(cx, cy, cz, ux, uy, uz, vx, vy, vz, 1, 1, r, g, b, seed);
            corner(cx, cy, cz, ux, uy, uz, vx, vy, vz, -1, -1, r, g, b, seed);
            corner(cx, cy, cz, ux, uy, uz, vx, vy, vz, 1, 1, r, g, b, seed);
            corner(cx, cy, cz, ux, uy, uz, vx, vy, vz, -1, 1, r, g, b, seed);
        }
        return holes * 6;
    }

    private static void corner(float cx, float cy, float cz, float ux, float uy, float uz, float vx, float vy, float vz,
                               float s, float t, float family, float fade, float core, float seed) {
        buf.put(cx + ux * s + vx * t).put(cy + uy * s + vy * t).put(cz + uz * s + vz * t);
        buf.put(s).put(t);
        buf.put(family).put(fade).put(core).put(1f);
        buf.put(seed).put(0f).put(0f).put(0f);
    }

    /** Particles: [x,y,z, vx,vy,vz, size, r,g,b,a, type] per particle, render space. */
    static int buildParticles(float[] it, int holes, int parts, Matrix4f view, float ex, float ey, float ez) {
        ensure(parts * 6 * VERT);
        // camera right / up in world (rows of the view matrix)
        float rx = view.m00(), ry = view.m10(), rz = view.m20();
        float upx = view.m01(), upy = view.m11(), upz = view.m21();
        int o = 2 + holes * ImpactEffects.HOLE_FLOATS;
        for (int p = 0; p < parts; p++, o += ImpactEffects.PARTICLE_FLOATS) {
            float x = it[o], y = it[o + 1], z = it[o + 2];
            float size = it[o + 6], type = it[o + 11];
            float ax, ay, az, bx, by, bz; // half extents
            int ty = (int) type;
            if (ty == ImpactEffects.P_SPARK || ty == ImpactEffects.P_SPLINTER) {
                // stretched along the motion, facing the eye
                float vx = it[o + 3], vy = it[o + 4], vz = it[o + 5];
                float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (vl < 1e-4f) { vx = upx; vy = upy; vz = upz; vl = 1; }
                vx /= vl; vy /= vl; vz /= vl;
                float len = ty == ImpactEffects.P_SPARK ? Math.min(0.25f, 0.012f + vl * 0.018f) : size * 3.5f;
                float wx = ex - x, wy = ey - y, wz = ez - z;
                float sx = vy * wz - vz * wy, sy = vz * wx - vx * wz, sz = vx * wy - vy * wx;
                float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1e-5f) { sx = rx; sy = ry; sz = rz; sl = 1; }
                float w = ty == ImpactEffects.P_SPARK ? 0.006f : size * 0.6f;
                ax = vx * len; ay = vy * len; az = vz * len;
                bx = sx / sl * w; by = sy / sl * w; bz = sz / sl * w;
            } else {
                ax = rx * size; ay = ry * size; az = rz * size;
                bx = upx * size; by = upy * size; bz = upz * size;
            }
            float r = it[o + 7], g = it[o + 8], b = it[o + 9], a = it[o + 10];
            pcorner(x, y, z, ax, ay, az, bx, by, bz, -1, -1, r, g, b, a, type);
            pcorner(x, y, z, ax, ay, az, bx, by, bz, 1, -1, r, g, b, a, type);
            pcorner(x, y, z, ax, ay, az, bx, by, bz, 1, 1, r, g, b, a, type);
            pcorner(x, y, z, ax, ay, az, bx, by, bz, -1, -1, r, g, b, a, type);
            pcorner(x, y, z, ax, ay, az, bx, by, bz, 1, 1, r, g, b, a, type);
            pcorner(x, y, z, ax, ay, az, bx, by, bz, -1, 1, r, g, b, a, type);
        }
        return parts * 6;
    }

    private static void pcorner(float x, float y, float z, float ax, float ay, float az, float bx, float by, float bz,
                                float s, float t, float r, float g, float b, float a, float type) {
        buf.put(x + ax * s + bx * t).put(y + ay * s + by * t).put(z + az * s + bz * t);
        buf.put(s).put(t);
        buf.put(r).put(g).put(b).put(a);
        buf.put(type).put(0f).put(0f).put(0f);
    }

    // ------------------------------------------------------------------ GL setup

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

    private static int program(String vsSrc, String fsSrc) {
        int vs = shader(GL_VERTEX_SHADER, vsSrc), fs = shader(GL_FRAGMENT_SHADER, fsSrc);
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glBindAttribLocation(p, 0, "pos");
        glBindAttribLocation(p, 1, "uv");
        glBindAttribLocation(p, 2, "col");
        glBindAttribLocation(p, 3, "extra");
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(p);
            glDeleteProgram(p);
            throw new IllegalStateException(log);
        }
        return p;
    }

    private static void initGL() {
        if (holeProgram != 0) return;
        holeProgram = program(Shaders.VERTEX, Shaders.HOLE);
        partProgram = program(Shaders.VERTEX, Shaders.PARTICLE);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = VERT * 4;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * 4);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 5L * 4);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 9L * 4);
    }

    private ImpactDraw() {}
}
