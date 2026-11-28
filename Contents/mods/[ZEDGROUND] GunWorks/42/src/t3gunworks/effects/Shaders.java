package t3gunworks.effects;

/** GLSL for bullet holes and impact debris (GLSL 3.30 core, like Viewpoint's own renderer). */
final class Shaders {

    static final String VERTEX = """
        #version 330 core
        uniform mat4 vp;
        in vec3 pos;
        in vec2 uv;
        in vec4 col;
        in vec4 extra;
        out vec2 vUv;
        out vec4 vCol;
        out vec4 vExtra;
        void main(){
          vUv = uv;
          vCol = col;
          vExtra = extra;
          gl_Position = vp * vec4(pos, 1.0);
        }
        """;

    /**
     * A bullet hole, multiplied into the lit scene (result = 2 * colour * scene): 0.5 leaves the surface
     * unchanged, below darkens (the hole, soot, damp earth), above brightens (freshly exposed plaster, concrete,
     * wood fibres, a polished metal rim). vCol = (family, fade, core radius, 1), vExtra.x = seed.
     */
    static final String HOLE = """
        #version 330 core
        in vec2 vUv;
        in vec4 vCol;
        in vec4 vExtra;
        out vec4 color;
        float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        float noise(vec2 p){
          vec2 i = floor(p), f = fract(p);
          f = f * f * (3.0 - 2.0 * f);
          return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
        }
        void main(){
          int family = int(vCol.x + 0.5);
          float fade = vCol.y;
          float core = vCol.z;
          float seed = vExtra.x;
          vec2 q = vUv;
          float r = length(q);
          if (r > 1.0) discard;
          float ang = atan(q.y, q.x);
          float ring = noise(vec2(ang * 2.5 + seed, seed * 0.37));
          float fine = noise(q * 9.0 + seed);
          float speck = noise(q * 23.0 + seed * 1.7);
          float c = core * (0.8 + 0.4 * fine);
          float m = 0.5;
          if (family == 0) {             // masonry: dark hole, shadowed crater, chipped lighter lip, soot speckles
            float jag = noise(vec2(ang * 7.0 + seed, seed * 1.3));
            float edge = 0.55 + 0.3 * ring + 0.15 * jag;
            if (r < c) m = 0.05;
            else if (r < edge) {
              float t = (r - c) / max(0.001, edge - c);
              m = 0.34 + 0.24 * smoothstep(0.0, 0.6, t) + 0.12 * (fine - 0.5) - 0.1 * step(0.74, speck);
              m = mix(m, 0.5, smoothstep(0.75, 1.0, t));
            }
          } else if (family == 1) {      // wood: hole, singed rim, splinter fibres along the grain
            vec2 g = vec2(cos(seed), sin(seed));
            float along = abs(dot(q, g)), across = abs(dot(q, vec2(-g.y, g.x)));
            if (r < c) m = 0.05;
            else if (r < c * 1.6) m = 0.34;
            else {
              float fibre = step(0.55, noise(vec2(along * 3.0, across * 40.0) + seed));
              float reach = 0.35 + 0.65 * noise(vec2(across * 18.0, seed));
              if (along < reach && across < 0.45) m = mix(0.5, 0.64, fibre * (1.0 - along / max(0.01, reach)));
            }
          } else if (family == 2) {      // metal: clean punched hole, bright rim, darker scorch
            if (r < c) m = 0.03;
            else if (r < c * 1.25) m = 0.68;
            else if (r < c * 2.2 + 0.25 * ring) m = mix(0.42, 0.5, (r - c * 1.25) / max(0.001, c * 0.95 + 0.25 * ring));
          } else if (family == 3) {      // earth: a soft dark divot
            float edge = 0.6 + 0.4 * ring;
            m = mix(0.28, 0.5, smoothstep(0.1, edge, r)) + 0.04 * (fine - 0.5);
          } else if (family == 6) {      // ceramic: hole, chips and radial cracks
            if (r < c) m = 0.08;
            else if (r < c * 1.8) m = 0.62;
            else {
              float spokes = abs(fract(ang * 0.954 + ring * 0.5 + seed) - 0.5);
              m = spokes < 0.02 * (1.0 - r) ? 0.3 : 0.5;
            }
          } else {                       // fabric, carpet, plastic, rubber
            if (r < c) m = 0.1;
            else if (r < c * 1.7) m = 0.42;
          }
          m = mix(0.5, m, fade);
          color = vec4(vec3(m), 1.0);
        }
        """;

    /**
     * Debris, premultiplied alpha. vCol = colour + alpha, vExtra.x = type
     * (0 dust, 1 chip, 2 spark, 3 splinter, 4 glass glint). Sparks and glints are additive (alpha 0).
     */
    static final String PARTICLE = """
        #version 330 core
        in vec2 vUv;
        in vec4 vCol;
        in vec4 vExtra;
        out vec4 color;
        void main(){
          int type = int(vExtra.x + 0.5);
          float r2 = dot(vUv, vUv);
          if (type == 0) {               // dust: soft puff
            if (r2 > 1.0) discard;
            float a = vCol.a * pow(1.0 - r2, 2.0);
            color = vec4(vCol.rgb * a, a);
          } else if (type == 2) {        // spark: hot additive streak
            float across = 1.0 - abs(vUv.y);
            float along = 1.0 - abs(vUv.x);
            float glow = across * across * along;
            color = vec4(vCol.rgb * glow * vCol.a, 0.0);
          } else if (type == 4) {        // glass glint: small additive star
            float s = max(0.0, 1.0 - abs(vUv.x) * abs(vUv.y) * 8.0 - r2 * 0.6);
            color = vec4(vCol.rgb * s * vCol.a * 0.8, 0.0);
          } else if (type == 3) {        // splinter: tapered sliver
            float w = 1.0 - abs(vUv.x);
            if (abs(vUv.y) > w) discard;
            float a = vCol.a;
            color = vec4(vCol.rgb * a, a);
          } else {                       // chip: small rough fragment
            if (r2 > 0.9) discard;
            float a = vCol.a;
            color = vec4(vCol.rgb * a, a);
          }
        }
        """;

    private Shaders() {}
}
