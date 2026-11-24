#!/usr/bin/env python3
# Makes the GhostPlane resources of Halo Drop's client jar (HaloDropClient.jar 0.6.0+) from the C-17 model.
# Usage: python tools/client-java/ghost_mesh.py <halodrop_c17_body.fbx> <Vehicle_HaloDropC17.png> <out folder>
#   out folder = Contents/mods/[ZEDGROUND] HALO Drop/42/src/halodropclient (build.sh packs *.mesh / *.tex from there)
# Needs Pillow. Reads binary FBX 7.x (one mesh, triangles) with the small parser below.
import struct, zlib, sys, math
from PIL import Image
def parse(path):
    d = open(path,'rb').read()
    ver = struct.unpack('<I', d[23:27])[0]
    big = ver >= 7500
    pos = 27
    def node(pos):
        if big:
            end, n, plen = struct.unpack('<QQQ', d[pos:pos+24]); pos += 24
        else:
            end, n, plen = struct.unpack('<III', d[pos:pos+12]); pos += 12
        nl = d[pos]; pos += 1
        if end == 0: return None, pos
        name = d[pos:pos+nl].decode('latin1'); pos += nl
        props = []
        for _ in range(n):
            t = chr(d[pos]); pos += 1
            if t in 'YCIFDL':
                fmt = {'Y':'<h','C':'<?','I':'<i','F':'<f','D':'<d','L':'<q'}[t]
                sz = struct.calcsize(fmt); props.append(struct.unpack(fmt, d[pos:pos+sz])[0]); pos += sz
            elif t in 'fdlib':
                L, enc, cl = struct.unpack('<III', d[pos:pos+12]); pos += 12
                raw = d[pos:pos+cl]; pos += cl
                if enc: raw = zlib.decompress(raw)
                fmt = {'f':'f','d':'d','l':'q','i':'i','b':'?'}[t]
                props.append(list(struct.unpack('<%d%s' % (L, fmt), raw)))
            elif t in 'SR':
                L = struct.unpack('<I', d[pos:pos+4])[0]; pos += 4
                props.append(d[pos:pos+L] if t=='R' else d[pos:pos+L].decode('latin1')); pos += L
        kids = []
        while pos < end:
            c, pos = node(pos)
            if c is None: break
            kids.append(c)
        return (name, props, kids), end
    top = []
    while pos < len(d):
        c, pos = node(pos)
        if c is None: break
        top.append(c)
    return top
def find(nodes, name):
    for n in nodes:
        if n[0] == name: yield n
def child(n, name):
    for c in n[2]:
        if c[0] == name: return c

#   c17.mesh : 'HDC1', int count, then count * (px,py,pz, nx,ny,nz, u,v) floats. Units = tiles.
#              x = right wing, y = up, z = nose. Origin = model origin (same as the vehicle position).
#   c17.tex  : 'HDT1', int w, int h, then w*h RGBA bytes, top row first.
fbxp, pngp, out = sys.argv[1], sys.argv[2], sys.argv[3]
SCALE = 0.1131 * 0.1          # model scale x vehicle model scale (halodrop_c17_vehicle.txt): length 53 tiles
top = parse(fbxp)
g = child(next(find(top, 'Objects')), 'Geometry')
V = child(g, 'Vertices')[1][0]; I = child(g, 'PolygonVertexIndex')[1][0]
ln = child(g, 'LayerElementNormal'); N = child(ln, 'Normals')[1][0]; NI = child(ln, 'NormalsIndex')[1][0]
lu = child(g, 'LayerElementUV'); UV = child(lu, 'UV')[1][0]; UI = child(lu, 'UVIndex')[1][0]
# tail fin (highest vertices) sits at -z, so the nose is +z already; mesh x is kept, the basis x = y cross z
tailz = [V[3*i+2] for i in range(len(V)//3) if V[3*i+1] > 1200]
assert max(tailz) < 0, 'nose direction changed'
verts = []; flipped = 0
for t in range(len(I) // 3):
    corners = []
    for k in range(3):
        p = I[3*t+k]; p = ~p if p < 0 else p
        n = NI[3*t+k]; u = UI[3*t+k]
        corners.append((V[3*p]*SCALE, V[3*p+1]*SCALE, V[3*p+2]*SCALE, N[3*n], N[3*n+1], N[3*n+2], UV[2*u], UV[2*u+1]))
    a, b, c = corners
    e1 = [b[q]-a[q] for q in range(3)]; e2 = [c[q]-a[q] for q in range(3)]
    fn = [e1[1]*e2[2]-e1[2]*e2[1], e1[2]*e2[0]-e1[0]*e2[2], e1[0]*e2[1]-e1[1]*e2[0]]
    sn = [a[3+q]+b[3+q]+c[3+q] for q in range(3)]
    if sum(fn[q]*sn[q] for q in range(3)) < 0:   # make every triangle counter-clockwise seen from its normal side
        corners = [a, c, b]; flipped += 1
    verts.extend(corners)
with open(out + '/c17.mesh', 'wb') as f:
    f.write(b'HDC1'); f.write(struct.pack('<i', len(verts)))
    for v in verts: f.write(struct.pack('<8f', *v))
im = Image.open(pngp).convert('RGBA').resize((1024, 1024), Image.LANCZOS)
with open(out + '/c17.tex', 'wb') as f:
    f.write(b'HDT1'); f.write(struct.pack('<ii', *im.size)); f.write(im.tobytes())
xs = [v[0] for v in verts]; ys = [v[1] for v in verts]; zs = [v[2] for v in verts]
print('triangles', len(verts)//3, 'flipped', flipped, 'bbox x', round(min(xs),2), round(max(xs),2), 'y', round(min(ys),2), round(max(ys),2), 'z', round(min(zs),2), round(max(zs),2))
