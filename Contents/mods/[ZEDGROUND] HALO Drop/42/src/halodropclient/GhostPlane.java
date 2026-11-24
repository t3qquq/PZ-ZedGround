package halodropclient;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import viewpoint.api.render.FrameView;
import viewpoint.api.view.Vec3;

import static org.lwjgl.opengl.GL33.*;

/**
 * Halo Drop: the jump plane keeps flying away in Viewpoint 1st/3rd person (0.6.0, 11-24, never in isometric).
 *
 * Why: the jump plane is a real vehicle. HaloDropPilot.lua removes it JUMP_PLANE_AHEAD (64) tiles past the jump point,
 *   and even without that the client drops a vehicle as soon as it leaves the client's loaded chunks
 *   (BaseVehicle.update: chunk.refs empty -> removeFromWorld; VehicleManager.clientUpdateVehiclePos: square null).
 *   Viewpoint draws vehicles from the vanilla ones (VehicleCapture), so the plane vanished in one frame.
 * What: when the pilot removes the vehicle, Lua publishes HaloDrop.Ghost (start point, heading, speeds, start time).
 *   From then on this class draws the same C-17 mesh on that line, in Viewpoint's own pass, with the frame's view and
 *   projection matrices - no vehicle, no chunk, so the loaded area does not matter. It ends after `life` ms or when it
 *   is `far` tiles from the camera, fading out over the last `fade` share of that distance.
 * Hook: PatchVpGhost -> viewpoint.render.AddonDraws.afterTranslucent, the same point and the same two reflected
 *   internals as Blue Zone's wall (AddonDraws.view(FrameContext) -> FrameView, WorldRenderer.bindTarget()).
 *   Plain OpenGL 3.3 (Viewpoint's addon GPU wrapper refuses jars that are not registered addons, see Blue Zone 0.4.0).
 *   Every GL state that is changed is saved and put back.
 * Mesh: halodropclient/c17.mesh, made from halodrop_c17_body.fbx (scale 0.1131 x 0.1 of halodrop_c17_vehicle.txt,
 *   53 x 51.5 x 16 tiles), x = right wing, y = up, z = nose, triangles counter-clockwise from their normal side.
 *   Texture halodropclient/c17.tex = Vehicle_HaloDropC17.png at 1024 px, raw RGBA, top row first.
 * Axes: render x = -(world x - origin.x), render z = -(world y - origin.y), render y = up (Blue Zone's calibrated
 *   mapping, run 11-19 long). Height: (floor - origin floor) x FLOOR_H. FLOOR_H is the vanilla 3D floor height
 *   (sqrt 6); not measured in Viewpoint yet - the first-draw log prints what is needed to check it.
 * Far plane: Viewpoint's scene far plane is a few hundred tiles. Past FAR_SHARE of it the plane is drawn with the same
 *   projection but a far plane behind the plane, without the depth test (back faces culled so it still reads solid).
 *   Up close the depth test sorts the faces and nothing is culled.
 *   Nothing of the scene is that far away in front of a plane in the sky.
 * Lua -> Java: no bridge. HaloDrop.Ghost is read from LuaManager.env by reflection, like Lens and Blue Zone.
 *   Lua replaces the table as a whole; a new `id` starts a new flight.
 * Public for the same reason as SkyLight: ZombieBuddy inlines the advice into Viewpoint's class.
 */
public final class GhostPlane {
    static final String TAG = "[HaloDrop][Ghost] ";
    static final float FLOOR_H = 2.44949f;      // render units per floor (vanilla 3D, sqrt 6)
    static final int DEFAULT_MAP = 6;           // render x = -(world dx), render z = -(world dy)  (Blue Zone)
    static final float FAR_SHARE = 0.85f;       // past this share of the scene far plane: own far plane, no depth test
    static final long STATE_NS = 30_000_000L;   // Lua state is re-read at most this often
    static final long STAT_MS = 5000L;
    static final float[] SUN = { 0.35f, 0.85f, 0.40f };   // render space, normalised in init
    static final float[] HAZE = { 0.62f, 0.68f, 0.76f };  // far haze colour (times the ambient)
    static final float HAZE_DIST = 1800f;       // tiles where the haze reaches HAZE_MAX
    static final float HAZE_MAX = 0.55f;

    static final String VS =
            "#version 330 core\n" +
            "layout(location = 0) in vec3 aPos;\n" +
            "layout(location = 1) in vec3 aNrm;\n" +
            "layout(location = 2) in vec2 aUV;\n" +
            "uniform mat4 uView;\n" +
            "uniform mat4 uProj;\n" +
            "uniform mat4 uModel;\n" +
            "out vec3 vN;\n" +
            "out vec2 vUV;\n" +
            "out float vD;\n" +
            "void main() {\n" +
            "    vec4 w = uModel * vec4(aPos, 1.0);\n" +
            "    vN = mat3(uModel) * aNrm;\n" +
            "    vUV = vec2(aUV.x, 1.0 - aUV.y);\n" +
            "    vec4 e = uView * w;\n" +
            "    vD = length(e.xyz);\n" +
            "    gl_Position = uProj * e;\n" +
            "}\n";

    static final String FS =
            "#version 330 core\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uLight;\n" +   // xyz = sun direction (render), w = ambient 0..1
            "uniform vec4 uLook;\n" +    // x = alpha, y = gamma, z = gain
            "uniform vec4 uHaze;\n" +    // rgb = haze colour, w = haze distance
            "uniform float uHazeMax;\n" +
            "in vec3 vN;\n" +
            "in vec2 vUV;\n" +
            "in float vD;\n" +
            "out vec4 oColor;\n" +
            "void main() {\n" +
            "    vec3 c = pow(texture(uTex, vUV).rgb, vec3(uLook.y));\n" +
            "    float lam = max(dot(normalize(vN), uLight.xyz), 0.0);\n" +
            "    vec3 lit = c * uLight.w * (0.45 + 0.55 * lam) * uLook.z;\n" +
            "    float h = uHazeMax * clamp(vD / uHaze.w, 0.0, 1.0);\n" +
            "    vec3 haze = pow(uHaze.rgb, vec3(uLook.y)) * uLight.w;\n" +
            "    oColor = vec4(mix(lit, haze, h), uLook.x);\n" +
            "}\n";

    private static final GhostPlane INSTANCE = new GhostPlane();

    // GL objects (render thread)
    private int program, vao, vbo, tex, vertCount;
    private int uView, uProj, uModel, uTex, uLight, uLook, uHaze, uHazeMax;
    private boolean broken;
    private int errors;

    // Viewpoint internals
    private boolean vpResolved;
    private Method mView, mBind;

    // Lua state
    private boolean luaResolved;
    private Field fEnv;
    private Method mRawget;
    private long nextState;
    private Object lastTable;
    private double flightId = Double.NaN;
    private boolean flying;
    private double x0, y0, dx, dy, z, v0, v1, acc, life, far, fade, gamma = 2.2, gain = 1.0;
    private long t0;

    // ambient (vanilla RenderSettings, the value Halo Drop's SkyLight also reads)
    private Method mRsInstance, mRsAmbient;
    private boolean ambientOff;
    private float ambient = 1f;
    private long nextAmbient;

    // diagnostics
    private boolean firstDrawLogged, projLogged;
    private long nextStat;
    private int frames, farFrames;
    private double lastS, lastDist, lastAlpha;

    private final float[] model = new float[16];
    private final float[] projFar = new float[16];

    public static void log(String s) { System.out.println(TAG + s); }

    /** called by PatchVpGhost at the end of AddonDraws.afterTranslucent (Viewpoint render thread) */
    public static void afterTranslucent(Object frame) {
        INSTANCE.draw(frame);
    }

    private void fail(String where, Throwable t) {
        errors++;
        log(where + " failed (" + errors + "): " + t + (t.getCause() != null ? " cause=" + t.getCause() : ""));
        if (errors >= 3) {
            broken = true;
            log("ghost plane off after " + errors + " errors");
        }
    }

    // ── Lua state ──────────────────────────────────────────────

    private Object get(Object table, String key) throws Exception {
        return table == null ? null : mRawget.invoke(table, key);
    }

    private double num(Object table, String key, double def) throws Exception {
        Object v = get(table, key);
        if (!(v instanceof Number)) return def;
        double d = ((Number) v).doubleValue();
        return Double.isNaN(d) || Double.isInfinite(d) ? def : d;
    }

    private void readState() throws Exception {
        long n = System.nanoTime();
        if (n < nextState) return;
        nextState = n + STATE_NS;
        if (!luaResolved) {
            ClassLoader cl = GhostPlane.class.getClassLoader();
            fEnv = Class.forName("zombie.Lua.LuaManager", false, cl).getField("env");
            mRawget = Class.forName("se.krka.kahlua.vm.KahluaTable", false, cl).getMethod("rawget", Object.class);
            luaResolved = true;
            log("state lookups resolved (LuaManager.env, KahluaTable.rawget)");
        }
        Object st = get(get(fEnv.get(null), "HaloDrop"), "Ghost");
        if (st == lastTable) return;
        lastTable = st;
        if (st == null) {
            if (flying) end("cleared by Lua");
            return;
        }
        double id = num(st, "id", Double.NaN);
        if (Double.isNaN(id) || id == flightId) return;
        double px = num(st, "x", Double.NaN), py = num(st, "y", Double.NaN), ddx = num(st, "dx", 0), ddy = num(st, "dy", 0);
        double len = Math.hypot(ddx, ddy);
        if (Double.isNaN(px) || Double.isNaN(py) || len < 1e-6) {
            flightId = id;
            log("ghost state ignored id=" + (long) id + ": bad start x=" + px + " y=" + py + " dir=(" + ddx + "," + ddy + ")");
            return;
        }
        flightId = id;
        x0 = px; y0 = py; dx = ddx / len; dy = ddy / len;
        z = num(st, "z", 31);
        v0 = num(st, "v0", 12);
        v1 = Math.max(num(st, "v1", v0), v0);
        acc = Math.max(num(st, "acc", 0), 0);
        life = num(st, "life", 90000);
        far = num(st, "far", 2500);
        fade = Math.min(Math.max(num(st, "fade", 0.3), 0.01), 1.0);
        gamma = num(st, "gamma", 2.2);
        gain = num(st, "gain", 1.0);
        t0 = (long) num(st, "t0", System.currentTimeMillis());
        flying = true;
        frames = 0; farFrames = 0; firstDrawLogged = false;
        nextStat = System.currentTimeMillis() + STAT_MS;
        log("ghost start id=" + (long) id + " at (" + f1(x0) + "," + f1(y0) + ") z=" + f1(z) + " dir=(" + f2(dx) + "," + f2(dy) + ")"
                + " v0=" + f1(v0) + " v1=" + f1(v1) + " acc=" + f1(acc) + " life=" + (long) life + "ms far=" + (long) far
                + " fade=" + f2(fade) + " gamma=" + f2(gamma) + " gain=" + f2(gain) + " lag=" + (System.currentTimeMillis() - t0) + "ms");
    }

    private void end(String why) {
        flying = false;
        log("ghost done (" + why + ") id=" + (long) flightId + " t=" + f1((System.currentTimeMillis() - t0) / 1000.0) + "s s=" + f1(lastS)
                + " tiles dist=" + f1(lastDist) + " frames=" + frames + " farFrames=" + farFrames);
    }

    /** tiles flown since the start: v0, accelerating by acc up to v1 */
    private double distanceAt(double t) {
        if (acc <= 0 || v1 <= v0) return v0 * t;
        double tr = (v1 - v0) / acc;
        if (t <= tr) return v0 * t + 0.5 * acc * t * t;
        return v0 * tr + 0.5 * acc * tr * tr + v1 * (t - tr);
    }

    private float ambientNow() {
        long now = System.currentTimeMillis();
        if (ambientOff || now < nextAmbient) return ambient;
        nextAmbient = now + 500L;
        try {
            if (mRsInstance == null) {
                Class<?> rs = Class.forName("zombie.core.opengl.RenderSettings", false, GhostPlane.class.getClassLoader());
                mRsInstance = rs.getMethod("getInstance");
                mRsAmbient = rs.getMethod("getAmbientForPlayer", int.class);
            }
            Object rs = mRsInstance.invoke(null);
            if (rs != null) ambient = ((Float) mRsAmbient.invoke(rs, 0)).floatValue();
        } catch (Throwable t) {
            ambientOff = true;
            log("ambient read off (plane stays at full light): " + t);
        }
        return ambient;
    }

    // ── GL ─────────────────────────────────────────────────────

    private boolean resolveVp(Object frame) {
        if (vpResolved) return true;
        try {
            ClassLoader cl = frame.getClass().getClassLoader();
            Class<?> ad = Class.forName("viewpoint.render.AddonDraws", false, cl);
            mView = ad.getDeclaredMethod("view", frame.getClass());
            mView.setAccessible(true);
            mBind = Class.forName("viewpoint.render.WorldRenderer", false, cl).getMethod("bindTarget");
            vpResolved = true;
            log("viewpoint hooks resolved (AddonDraws.view, WorldRenderer.bindTarget)");
            return true;
        } catch (Throwable t) {
            broken = true;
            log("viewpoint hooks FAILED, ghost plane off (Viewpoint changed?): " + t);
            return false;
        }
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = GhostPlane.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("resource missing in jar: halodropclient/" + name);
            return in.readAllBytes();
        }
    }

    private static int shader(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == 0) {
            String info = glGetShaderInfoLog(s);
            glDeleteShader(s);
            throw new IllegalStateException((type == GL_VERTEX_SHADER ? "vertex" : "fragment") + " shader: " + info);
        }
        return s;
    }

    /** program, vertex buffer and texture; called with the caller's GL state saved (draw restores it) */
    private void initGl() throws Exception {
        if (program != 0) return;
        byte[] mesh = resource("c17.mesh");
        ByteBuffer mb = ByteBuffer.wrap(mesh).order(ByteOrder.LITTLE_ENDIAN);
        if (mb.getInt(0) != 0x31434448) throw new IllegalStateException("c17.mesh: bad magic");   // 'HDC1'
        int count = mb.getInt(4);
        if (8 + count * 32 != mesh.length) throw new IllegalStateException("c17.mesh: size " + mesh.length + " for " + count + " vertices");
        ByteBuffer vb = ByteBuffer.allocateDirect(count * 32).order(ByteOrder.nativeOrder());
        mb.position(8);
        while (mb.hasRemaining()) vb.putFloat(mb.getFloat());
        vb.flip();

        byte[] tb = resource("c17.tex");
        ByteBuffer th = ByteBuffer.wrap(tb).order(ByteOrder.LITTLE_ENDIAN);
        if (th.getInt(0) != 0x31544448) throw new IllegalStateException("c17.tex: bad magic");    // 'HDT1'
        int tw = th.getInt(4), tht = th.getInt(8);
        if (12 + tw * tht * 4 != tb.length) throw new IllegalStateException("c17.tex: size " + tb.length + " for " + tw + "x" + tht);
        ByteBuffer px = ByteBuffer.allocateDirect(tw * tht * 4);
        px.put(tb, 12, tw * tht * 4).flip();

        int vs = shader(GL_VERTEX_SHADER, VS), fs = shader(GL_FRAGMENT_SHADER, FS);
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) {
            String info = glGetProgramInfoLog(p);
            glDeleteProgram(p);
            throw new IllegalStateException("program link: " + info);
        }
        uView = glGetUniformLocation(p, "uView");
        uProj = glGetUniformLocation(p, "uProj");
        uModel = glGetUniformLocation(p, "uModel");
        uTex = glGetUniformLocation(p, "uTex");
        uLight = glGetUniformLocation(p, "uLight");
        uLook = glGetUniformLocation(p, "uLook");
        uHaze = glGetUniformLocation(p, "uHaze");
        uHazeMax = glGetUniformLocation(p, "uHazeMax");

        int oldUnpackBuf = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING), oldAlign = glGetInteger(GL_UNPACK_ALIGNMENT),
                oldRow = glGetInteger(GL_UNPACK_ROW_LENGTH);
        try {
            vao = glGenVertexArrays();
            glBindVertexArray(vao);
            vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 32, 0L);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 32, 12L);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, 32, 24L);
            glEnableVertexAttribArray(0);
            glEnableVertexAttribArray(1);
            glEnableVertexAttribArray(2);

            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            tex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, tw, tht, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
            glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        } finally {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, oldUnpackBuf);
            glPixelStorei(GL_UNPACK_ALIGNMENT, oldAlign);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, oldRow);
        }
        vertCount = count;
        program = p;
        int err = glGetError();
        log("gl ready: program " + p + " vao " + vao + " vbo " + vbo + " tex " + tex + " (" + tw + "x" + tht + ") triangles " + (count / 3)
                + " uniforms view=" + uView + " proj=" + uProj + " model=" + uModel + " tex=" + uTex + " light=" + uLight
                + " look=" + uLook + " haze=" + uHaze + " hazeMax=" + uHazeMax + " glError=0x" + Integer.toHexString(err));
    }

    /** camera position in render space from a view matrix (column-major or row-major, picked by the zero row); [3] = 1 if column-major */
    static double[] cameraFromView(float[] m) {
        boolean colMajor = Math.abs(m[3]) + Math.abs(m[7]) + Math.abs(m[11]) < 1e-3f;
        double r00, r01, r02, r10, r11, r12, r20, r21, r22, tx, ty, tz;
        if (colMajor) {
            r00 = m[0]; r01 = m[4]; r02 = m[8];  tx = m[12];
            r10 = m[1]; r11 = m[5]; r12 = m[9];  ty = m[13];
            r20 = m[2]; r21 = m[6]; r22 = m[10]; tz = m[14];
        } else {
            r00 = m[0]; r01 = m[1]; r02 = m[2];  tx = m[3];
            r10 = m[4]; r11 = m[5]; r12 = m[6];  ty = m[7];
            r20 = m[8]; r21 = m[9]; r22 = m[10]; tz = m[11];
        }
        return new double[] {
                -(r00 * tx + r10 * ty + r20 * tz),
                -(r01 * tx + r11 * ty + r21 * tz),
                -(r02 * tx + r12 * ty + r22 * tz),
                colMajor ? 1 : 0 };
    }

    /** {near, far} of a standard OpenGL perspective projection, or null when it is something else (ortho, reversed, infinite) */
    static double[] nearFar(float[] p, boolean colMajor) {
        float a = p[10], b = colMajor ? p[14] : p[11], w = colMajor ? p[11] : p[14];
        if (Math.abs(w + 1f) > 1e-3f || Math.abs(p[15]) > 1e-3f) return null;
        double n = b / (a - 1.0), f = b / (a + 1.0);
        if (!(n > 0) || !(f > n) || Double.isInfinite(f)) return null;
        return new double[] { n, f };
    }

    private static void toggle(int cap, boolean on) { if (on) glEnable(cap); else glDisable(cap); }

    private void draw(Object frame) {
        if (broken) return;
        try { readState(); } catch (Throwable t) { fail("state", t); return; }
        if (!flying) return;
        if (!resolveVp(frame)) return;
        try {
            long now = System.currentTimeMillis();
            double t = Math.max(0, (now - t0) / 1000.0);
            if (t * 1000.0 > life) { end("life " + (long) life + "ms"); return; }
            FrameView v = (FrameView) mView.invoke(null, frame);
            Vec3 org = v.sceneOrigin();
            if (org == null) return;
            float[] view = v.view(), proj = v.projection();
            double[] cam = cameraFromView(view);
            boolean colMajor = cam[3] > 0;

            double s = distanceAt(t);
            double wx = x0 + dx * s, wy = y0 + dy * s;
            // render position of the model origin (mapping DEFAULT_MAP: x = -dx, z = -dy)
            double rx = -(wx - org.x()), rz = -(wy - org.y()), ry = (z - org.z()) * FLOOR_H;
            double dist = Math.sqrt((rx - cam[0]) * (rx - cam[0]) + (ry - cam[1]) * (ry - cam[1]) + (rz - cam[2]) * (rz - cam[2]));
            lastS = s; lastDist = dist;
            if (dist > far) { end("far " + (long) far + " tiles"); return; }
            double alpha = 1.0 - smooth(far * (1.0 - fade), far, dist);
            lastAlpha = alpha;

            // model: columns right, up, nose, position. nose = render (-dx, -dy); right = up x nose
            float fx = (float) -dx, fz = (float) -dy;
            model[0] = fz;  model[1] = 0f; model[2] = -fx; model[3] = 0f;
            model[4] = 0f;  model[5] = 1f; model[6] = 0f;  model[7] = 0f;
            model[8] = fx;  model[9] = 0f; model[10] = fz; model[11] = 0f;
            model[12] = (float) rx; model[13] = (float) ry; model[14] = (float) rz; model[15] = 1f;

            double[] nf = nearFar(proj, colMajor);
            if (!projLogged) {
                projLogged = true;
                log("scene projection: " + (nf == null ? "not a standard perspective (proj[10]=" + proj[10] + " proj[11]=" + proj[11] + " proj[14]=" + proj[14]
                        + " proj[15]=" + proj[15] + "), far-plane switch off" : "near=" + f2(nf[0]) + " far=" + f1(nf[1])) + " colMajor=" + colMajor);
            }
            boolean farMode = nf != null && dist + 40.0 > nf[1] * FAR_SHARE;
            float[] useProj = proj;
            if (farMode) {
                double n = nf[0], f = dist * 2.0 + 200.0;
                System.arraycopy(proj, 0, projFar, 0, 16);
                projFar[10] = (float) (-(f + n) / (f - n));
                projFar[colMajor ? 14 : 11] = (float) (-2.0 * f * n / (f - n));
                useProj = projFar;
                farFrames++;
            }

            int oldProgram = glGetInteger(GL_CURRENT_PROGRAM), oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING),
                    oldBuf = glGetInteger(GL_ARRAY_BUFFER_BINDING), oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
            int fbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE),
                    scissor = glIsEnabled(GL_SCISSOR_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
            int df = glGetInteger(GL_DEPTH_FUNC), sr = glGetInteger(GL_BLEND_SRC_RGB), dr = glGetInteger(GL_BLEND_DST_RGB),
                    sa = glGetInteger(GL_BLEND_SRC_ALPHA), da = glGetInteger(GL_BLEND_DST_ALPHA),
                    er = glGetInteger(GL_BLEND_EQUATION_RGB), ea = glGetInteger(GL_BLEND_EQUATION_ALPHA),
                    cullMode = glGetInteger(GL_CULL_FACE_MODE), front = glGetInteger(GL_FRONT_FACE);
            glActiveTexture(GL_TEXTURE0 + 7);
            int oldTex = glGetInteger(GL_TEXTURE_BINDING_2D), oldSampler = glGetInteger(GL_SAMPLER_BINDING);
            try {
                initGl();
                mBind.invoke(null);
                glUseProgram(program);
                glBindVertexArray(vao);
                glActiveTexture(GL_TEXTURE0 + 7);
                glBindTexture(GL_TEXTURE_2D, tex);
                glBindSampler(7, 0);
                glUniform1i(uTex, 7);

                glEnable(GL_BLEND);
                glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);   // the target's alpha is left alone
                glDisable(GL_SCISSOR_TEST);
                if (farMode) {
                    // nothing of the scene reaches this far: no depth test, back faces culled instead so the plane still
                    // hides its own far side. The front-face winding is Viewpoint's own (glFrontFace left as found): the
                    // mesh is counter-clockwise like vanilla models, a flipped target would have flipped it too.
                    glDisable(GL_DEPTH_TEST);
                    glDepthMask(false);
                    glEnable(GL_CULL_FACE);
                    glCullFace(GL_BACK);
                } else {
                    glEnable(GL_DEPTH_TEST);               // hills and buildings in front hide it, the plane hides itself
                    glDepthFunc(GL_LEQUAL);
                    glDepthMask(alpha > 0.99);             // write depth only while it is solid
                    glDisable(GL_CULL_FACE);               // depth sorts the faces; no winding assumption up close
                }

                glUniformMatrix4fv(uView, !colMajor, view);
                glUniformMatrix4fv(uProj, !colMajor, useProj);
                glUniformMatrix4fv(uModel, false, model);
                float amb = Math.max(0.15f, Math.min(1f, ambientNow()));
                float sl = (float) Math.sqrt(SUN[0] * SUN[0] + SUN[1] * SUN[1] + SUN[2] * SUN[2]);
                glUniform4f(uLight, SUN[0] / sl, SUN[1] / sl, SUN[2] / sl, amb);
                glUniform4f(uLook, (float) alpha, (float) gamma, (float) gain, 0f);
                glUniform4f(uHaze, HAZE[0], HAZE[1], HAZE[2], HAZE_DIST);
                glUniform1f(uHazeMax, HAZE_MAX);
                glDrawArrays(GL_TRIANGLES, 0, vertCount);
                frames++;

                if (!firstDrawLogged) {
                    firstDrawLogged = true;
                    Vec3 eye = v.eye();
                    log("first ghost draw: world=(" + f1(wx) + "," + f1(wy) + ") render=(" + f1(rx) + "," + f1(ry) + "," + f1(rz) + ")"
                            + " camRender=(" + f1(cam[0]) + "," + f1(cam[1]) + "," + f1(cam[2]) + ") dist=" + f1(dist)
                            + " sceneOrigin=" + vec(org) + " eye=" + vec(eye) + " farMode=" + farMode + " ambient=" + f2(amb)
                            + " viewport=" + viewport[2] + "x" + viewport[3]
                            + " gl before: blend=" + blend + " depthTest=" + depth + " depthFunc=0x" + Integer.toHexString(df)
                            + " cull=" + cull + " frontFace=0x" + Integer.toHexString(front) + " depthWrite=" + mask + " glError=0x" + Integer.toHexString(glGetError()));
                }
            } finally {
                glUseProgram(oldProgram);
                glBindVertexArray(oldVao);
                glBindBuffer(GL_ARRAY_BUFFER, oldBuf);
                glActiveTexture(GL_TEXTURE0 + 7);
                glBindTexture(GL_TEXTURE_2D, oldTex);
                glBindSampler(7, oldSampler);
                glActiveTexture(oldActive);
                glBlendEquationSeparate(er, ea);
                glBlendFuncSeparate(sr, dr, sa, da);
                toggle(GL_BLEND, blend);
                glDepthFunc(df);
                glDepthMask(mask);
                toggle(GL_DEPTH_TEST, depth);
                glCullFace(cullMode);
                toggle(GL_CULL_FACE, cull);
                toggle(GL_SCISSOR_TEST, scissor);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
                glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
            if (now >= nextStat) {
                nextStat = now + STAT_MS;
                log("ghost t=" + f1(t) + "s s=" + f1(s) + " tiles speed=" + f1(speedAt(t)) + " dist=" + f1(dist) + " alpha=" + f2(lastAlpha)
                        + " farMode=" + farMode + " frames=" + frames + " farFrames=" + farFrames);
            }
        } catch (Throwable t) {
            fail("draw", t);
        }
    }

    private double speedAt(double t) {
        if (acc <= 0 || v1 <= v0) return v0;
        return Math.min(v1, v0 + acc * t);
    }

    static double smooth(double e0, double e1, double x) {
        if (x <= e0) return 0;
        if (x >= e1) return 1;
        double k = (x - e0) / (e1 - e0);
        return k * k * (3 - 2 * k);
    }

    static String f2(double d) { return Double.isNaN(d) ? "nan" : String.valueOf(Math.round(d * 100) / 100.0); }
    static String f1(double d) { return String.valueOf(Math.round(d * 10) / 10.0); }
    static String vec(Vec3 p) { return p == null ? "null" : "(" + f1(p.x()) + "," + f1(p.y()) + "," + f1(p.z()) + ")"; }
}
