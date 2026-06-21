// Bambu Printer LAN — native slicing engine (v0.3).
//
// A real FDM slicer: parses STL (binary/ASCII), applies a model transform
// (scale / rotate-Z / move / drop-to-plate / center), slices into layers, and
// for each layer emits N perimeter loops plus rectilinear infill clipped to the
// contours (even-odd scanline), with solid top/bottom layers, temperatures,
// retraction and part-cooling fan. Config comes as `key = value` lines.
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#define LOG_TAG "BPLengine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct V3 { float x, y, z; };
struct Tri { V3 v[3]; };
struct Pt { float x, y; };
struct Seg { float x0, y0, x1, y1; };
using Loop = std::vector<Pt>;

bool read_file(const std::string& p, std::vector<char>& out) {
    std::ifstream f(p, std::ios::binary | std::ios::ate);
    if (!f) return false;
    std::streamsize n = f.tellg();
    if (n <= 0) return false;
    f.seekg(0); out.resize((size_t)n);
    return (bool)f.read(out.data(), n);
}

bool is_ascii(const std::vector<char>& b) {
    if (b.size() < 6 || std::strncmp(b.data(), "solid", 5) != 0) return false;
    if (b.size() >= 84) { uint32_t c; std::memcpy(&c, b.data() + 80, 4);
        if (b.size() == 84ull + 50ull * c) return false; }
    return true;
}

void parse_binary(const std::vector<char>& b, std::vector<Tri>& t) {
    if (b.size() < 84) return;
    uint32_t c; std::memcpy(&c, b.data() + 80, 4);
    if (b.size() < 84ull + 50ull * c) c = (uint32_t)((b.size() - 84) / 50);
    t.reserve(c); const char* p = b.data() + 84;
    for (uint32_t i = 0; i < c; ++i, p += 50) {
        Tri tr; float f[9]; std::memcpy(f, p + 12, sizeof(f));
        for (int k = 0; k < 3; ++k) { tr.v[k] = {f[k*3], f[k*3+1], f[k*3+2]}; }
        t.push_back(tr);
    }
}

void parse_ascii(const std::vector<char>& b, std::vector<Tri>& t) {
    std::string s(b.begin(), b.end()); std::istringstream in(s); std::string tok;
    Tri tr; int vi = 0;
    while (in >> tok) if (tok == "vertex") {
        in >> tr.v[vi].x >> tr.v[vi].y >> tr.v[vi].z;
        if (++vi == 3) { t.push_back(tr); vi = 0; }
    }
}

bool looks_binary(const std::vector<char>& b) {
    if (b.size() < 84) return false;
    uint32_t c; std::memcpy(&c, b.data() + 80, 4);
    return b.size() == 84ull + 50ull * c;
}

// Wavefront OBJ: vertices + faces (handles f a/b/c and polygon fans).
void parse_obj(const std::vector<char>& b, std::vector<Tri>& t) {
    std::string s(b.begin(), b.end()); std::istringstream in(s); std::string line;
    std::vector<V3> verts;
    while (std::getline(in, line)) {
        if (line.size() < 2) continue;
        if (line[0] == 'v' && line[1] == ' ') {
            std::istringstream ls(line.substr(2)); V3 v{}; ls >> v.x >> v.y >> v.z; verts.push_back(v);
        } else if (line[0] == 'f' && line[1] == ' ') {
            std::istringstream ls(line.substr(2)); std::string w; std::vector<int> idx;
            while (ls >> w) {
                int vi = std::atoi(w.c_str());  // up to first '/' — atoi stops there
                if (vi < 0) vi = (int)verts.size() + vi + 1;
                if (vi >= 1 && vi <= (int)verts.size()) idx.push_back(vi - 1);
            }
            for (size_t i = 2; i < idx.size(); ++i) {
                Tri tr; tr.v[0] = verts[idx[0]]; tr.v[1] = verts[idx[i - 1]]; tr.v[2] = verts[idx[i]];
                t.push_back(tr);
            }
        }
    }
}

float cfg(const std::string& ini, const char* key, float def) {
    auto p = ini.find(key); if (p == std::string::npos) return def;
    auto e = ini.find('=', p); if (e == std::string::npos) return def;
    char* end = nullptr; float v = std::strtof(ini.c_str() + e + 1, &end);
    return end == ini.c_str() + e + 1 ? def : v;
}

bool tri_seg(const Tri& t, float z, Seg& s) {
    float p[2][2]; int n = 0;
    for (int e = 0; e < 3 && n < 2; ++e) {
        const V3& a = t.v[e]; const V3& c = t.v[(e + 1) % 3];
        float za = a.z - z, zc = c.z - z;
        if ((za > 0 && zc > 0) || (za < 0 && zc < 0) || za == zc) continue;
        float u = za / (za - zc);
        p[n][0] = a.x + u * (c.x - a.x); p[n][1] = a.y + u * (c.y - a.y); ++n;
    }
    if (n != 2) return false;
    s = {p[0][0], p[0][1], p[1][0], p[1][1]}; return true;
}

std::vector<Loop> chain(std::vector<Seg> segs) {
    std::vector<Loop> loops; std::vector<bool> used(segs.size(), false);
    const float eps = 0.02f;
    for (size_t i = 0; i < segs.size(); ++i) {
        if (used[i]) continue; used[i] = true;
        Loop lp; lp.push_back({segs[i].x0, segs[i].y0});
        float ex = segs[i].x1, ey = segs[i].y1; lp.push_back({ex, ey});
        bool ext = true;
        while (ext) { ext = false;
            for (size_t j = 0; j < segs.size(); ++j) {
                if (used[j]) continue;
                if (std::fabs(segs[j].x0 - ex) < eps && std::fabs(segs[j].y0 - ey) < eps) { ex = segs[j].x1; ey = segs[j].y1; }
                else if (std::fabs(segs[j].x1 - ex) < eps && std::fabs(segs[j].y1 - ey) < eps) { ex = segs[j].x0; ey = segs[j].y0; }
                else continue;
                used[j] = true; lp.push_back({ex, ey}); ext = true; break;
            }
        }
        loops.push_back(std::move(lp));
    }
    return loops;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_bambuprinterlan_engine_SlicerBridge_nativeEngineVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("Bambu Printer LAN engine 0.3 (perimeters + infill + solid)");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_bambuprinterlan_engine_SlicerBridge_nativeSlice(
        JNIEnv* env, jobject, jstring jin, jstring jout, jstring jcfg) {
    const char* ci = env->GetStringUTFChars(jin, nullptr);
    const char* co = env->GetStringUTFChars(jout, nullptr);
    const char* cc = env->GetStringUTFChars(jcfg, nullptr);
    std::string in = ci ? ci : "", out = co ? co : "", ini = cc ? cc : "";
    if (ci) env->ReleaseStringUTFChars(jin, ci);
    if (co) env->ReleaseStringUTFChars(jout, co);
    if (cc) env->ReleaseStringUTFChars(jcfg, cc);

    std::vector<char> bytes;
    if (!read_file(in, bytes)) return -1;
    std::vector<Tri> tris;
    if (is_ascii(bytes)) parse_ascii(bytes, tris);
    else if (looks_binary(bytes)) parse_binary(bytes, tris);
    else parse_obj(bytes, tris);  // OBJ (or other text mesh)
    if (tris.empty()) return -2;

    // ---- config ----
    float lh = cfg(ini, "layer_height", 0.2f); if (lh < 0.04f || lh > 1.0f) lh = 0.2f;
    float lw = cfg(ini, "line_width", 0.42f);
    int walls = std::max(1, (int)cfg(ini, "wall_loops", 2));
    float density = std::min(100.f, std::max(0.f, cfg(ini, "infill_density", 15)));
    int solidN = std::max(0, (int)cfg(ini, "top_bottom_layers", 3));
    int brim = std::max(0, (int)cfg(ini, "brim_loops", 0));
    int skirt = std::max(0, (int)cfg(ini, "skirt_loops", 0));
    float skirtGap = cfg(ini, "skirt_gap", 3.f);
    int nozzleT = (int)cfg(ini, "nozzle_temp", 220);
    int bedT = (int)cfg(ini, "bed_temp", 60);
    float scale = cfg(ini, "scale", 1.0f); if (scale <= 0) scale = 1.f;
    float rot = cfg(ini, "rotate_z", 0.f) * 3.14159265f / 180.f;
    float moveX = cfg(ini, "move_x", 0.f), moveY = cfg(ini, "move_y", 0.f);
    float cx = cfg(ini, "plate_x", 128.f), cy = cfg(ini, "plate_y", 128.f);
    bool center = cfg(ini, "center", 1.f) != 0.f;

    // ---- transform: scale, rotate-Z ----
    float cosr = std::cos(rot), sinr = std::sin(rot);
    float minx = 1e30f, maxx = -1e30f, miny = 1e30f, maxy = -1e30f, minz = 1e30f, maxz = -1e30f;
    for (Tri& t : tris) for (int k = 0; k < 3; ++k) {
        float x = t.v[k].x * scale, y = t.v[k].y * scale, z = t.v[k].z * scale;
        t.v[k].x = x * cosr - y * sinr; t.v[k].y = x * sinr + y * cosr; t.v[k].z = z;
        minx = std::fmin(minx, t.v[k].x); maxx = std::fmax(maxx, t.v[k].x);
        miny = std::fmin(miny, t.v[k].y); maxy = std::fmax(maxy, t.v[k].y);
        minz = std::fmin(minz, t.v[k].z); maxz = std::fmax(maxz, t.v[k].z);
    }
    float tx = moveX - (center ? (minx + maxx) * 0.5f - cx : 0.f);
    float ty = moveY - (center ? (miny + maxy) * 0.5f - cy : 0.f);
    for (Tri& t : tris) for (int k = 0; k < 3; ++k) {
        t.v[k].x += tx; t.v[k].y += ty; t.v[k].z -= minz;
    }
    float height = maxz - minz;

    float flow = cfg(ini, "flow_ratio", 1.0f); if (flow <= 0.f) flow = 1.f;

    std::ofstream g(out);
    if (!g) return -3;
    const float e_mm = (lw * lh) / 2.405f * flow;
    g << "; Bambu Printer LAN engine 0.3\n; tris=" << tris.size()
      << " lh=" << lh << " walls=" << walls << " infill=" << density << "%\n";
    g << "M140 S" << bedT << "\nM104 S" << nozzleT << "\n";
    g << "M190 S" << bedT << "\nM109 S" << nozzleT << "\n";
    g << "G21\nG90\nM82\nG28\nG92 E0\n";
    g << "G1 Z0.2 F600\nG1 X5 Y5 F3000\nG1 X150 Y5 E15 F1000\nG92 E0\nM83\n";

    auto emit_path = [&](const std::vector<Pt>& pts, bool close) {
        if (pts.size() < 2) return;
        g << "G1 E-0.8 F1800\n";
        g << "G0 X" << pts[0].x << " Y" << pts[0].y << " F6000\n";
        g << "G1 E0.8 F1800\n";
        size_t cnt = pts.size() + (close ? 1 : 0);
        for (size_t i = 1; i < cnt; ++i) {
            const Pt& a = pts[(i - 1) % pts.size()]; const Pt& b = pts[i % pts.size()];
            float dx = b.x - a.x, dy = b.y - a.y; float d = std::sqrt(dx * dx + dy * dy);
            if (d < 1e-4f) continue;
            g << "G1 X" << b.x << " Y" << b.y << " E" << (d * e_mm) << " F1500\n";
        }
    };

    int approxLayers = (int)(height / lh);
    int layers = 0;
    int li = 0;
    for (float z = lh * 0.5f; z <= height; z += lh, ++li) {
        std::vector<Seg> segs; Seg s;
        for (const Tri& t : tris) if (tri_seg(t, z, s)) segs.push_back(s);
        if (segs.empty()) continue;
        std::vector<Loop> loops = chain(segs);
        ++layers;
        g << "; layer " << layers << " z=" << z << "\n";
        if (layers == 2) g << "M106 S255\n";
        g << "G1 Z" << z << " F600\n";

        // skirt: priming loops around the whole model on the first layer
        if (layers == 1 && skirt > 0) {
            float bx0 = 1e30f, bx1 = -1e30f, by0 = 1e30f, by1 = -1e30f;
            for (const Loop& lp : loops) for (const Pt& p : lp) {
                bx0 = std::fmin(bx0, p.x); bx1 = std::fmax(bx1, p.x);
                by0 = std::fmin(by0, p.y); by1 = std::fmax(by1, p.y);
            }
            for (int sk = skirt - 1; sk >= 0; --sk) {
                float o = skirtGap + sk * lw;
                Loop rect = {{bx0 - o, by0 - o}, {bx1 + o, by0 - o},
                             {bx1 + o, by1 + o}, {bx0 - o, by1 + o}};
                emit_path(rect, true);
            }
        }

        // brim: outward adhesion loops on the first printed layer
        if (layers == 1 && brim > 0) {
            for (const Loop& lp : loops) {
                if (lp.size() < 3) continue;
                float gx = 0, gy = 0; for (const Pt& p : lp) { gx += p.x; gy += p.y; }
                gx /= lp.size(); gy /= lp.size();
                for (int br = brim; br >= 1; --br) {
                    float off = br * lw;
                    Loop ring; ring.reserve(lp.size());
                    for (const Pt& p : lp) {
                        float dx = p.x - gx, dy = p.y - gy; float dl = std::sqrt(dx * dx + dy * dy);
                        float k = dl > 0 ? (dl + off) / dl : 1.f;
                        ring.push_back({gx + dx * k, gy + dy * k});
                    }
                    emit_path(ring, true);
                }
            }
        }

        // perimeters: outer loop + inward offsets toward centroid
        for (const Loop& lp : loops) {
            if (lp.size() < 3) continue;
            float gx = 0, gy = 0; for (const Pt& p : lp) { gx += p.x; gy += p.y; }
            gx /= lp.size(); gy /= lp.size();
            for (int w = 0; w < walls; ++w) {
                float off = w * lw;
                Loop ring; ring.reserve(lp.size());
                for (const Pt& p : lp) {
                    float dx = p.x - gx, dy = p.y - gy; float dl = std::sqrt(dx * dx + dy * dy);
                    float k = dl > off ? (dl - off) / dl : 0.f;
                    ring.push_back({gx + dx * k, gy + dy * k});
                }
                emit_path(ring, true);
            }
        }

        // infill: even-odd scanline; solid on top/bottom layers
        bool solid = (li < solidN) || (z > height - solidN * lh);
        float dens = solid ? 100.f : density;
        if (dens > 0.5f) {
            float spacing = lw / (dens / 100.f);
            bool vertical = (li % 2) == 1;
            float a0 = 1e30f, a1 = -1e30f;
            for (const Loop& lp : loops) for (const Pt& p : lp) {
                float c = vertical ? p.x : p.y; a0 = std::fmin(a0, c); a1 = std::fmax(a1, c);
            }
            for (float a = a0 + spacing * 0.5f; a < a1; a += spacing) {
                std::vector<float> xs;
                for (const Loop& lp : loops) {
                    size_t m = lp.size();
                    for (size_t i = 0; i < m; ++i) {
                        const Pt& p1 = lp[i]; const Pt& p2 = lp[(i + 1) % m];
                        float c1 = vertical ? p1.x : p1.y, c2 = vertical ? p2.x : p2.y;
                        if ((c1 <= a && c2 > a) || (c2 <= a && c1 > a)) {
                            float u = (a - c1) / (c2 - c1);
                            float b1 = vertical ? p1.y : p1.x, b2 = vertical ? p2.y : p2.x;
                            xs.push_back(b1 + u * (b2 - b1));
                        }
                    }
                }
                std::sort(xs.begin(), xs.end());
                for (size_t i = 0; i + 1 < xs.size(); i += 2) {
                    Pt A = vertical ? Pt{a, xs[i]} : Pt{xs[i], a};
                    Pt B = vertical ? Pt{a, xs[i + 1]} : Pt{xs[i + 1], a};
                    std::vector<Pt> line = {A, B};
                    emit_path(line, false);
                }
            }
        }
    }

    g << "M104 S0\nM140 S0\nM107\nG1 Z" << (height + 5) << " F600\nG28 X0 Y0\n; done\n";
    g.flush();
    LOGI("sliced %s -> %d/%d layers", in.c_str(), layers, approxLayers);
    return layers;
}
