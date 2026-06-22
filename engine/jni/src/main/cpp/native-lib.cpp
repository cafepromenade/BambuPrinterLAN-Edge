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
    int pattern = (int)cfg(ini, "infill_pattern", 0);  // 0 line,1 grid,2 tri,3 star,4 concentric
    int seam = (int)cfg(ini, "seam_position", 0);      // 0 nearest, 1 back, 2 front
    int wallOrder = (int)cfg(ini, "wall_order", 0);    // 0 outer-first, 1 inner-first
    int iron = (int)cfg(ini, "ironing", 0);            // smooth the top surface
    float ironFlow = cfg(ini, "iron_flow", 0.15f);
    int support = (int)cfg(ini, "support", 0);         // generate overhang supports
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

    float flowMul = 1.f;  // scaled down during the ironing pass
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
            g << "G1 X" << b.x << " Y" << b.y << " E" << (d * e_mm * flowMul) << " F1500\n";
        }
    };

    // ---- support pre-pass: coarse occupancy grid -> overhang columns --------
    std::vector<std::vector<char>> supportG;
    float gMinX = 0, gMinY = 0; int gnx = 0, gny = 0; const float cs = 3.0f;
    if (support) {
        float ax0 = 1e30f, ax1 = -1e30f, ay0 = 1e30f, ay1 = -1e30f;
        for (const Tri& t : tris) for (int k = 0; k < 3; ++k) {
            ax0 = std::fmin(ax0, t.v[k].x); ax1 = std::fmax(ax1, t.v[k].x);
            ay0 = std::fmin(ay0, t.v[k].y); ay1 = std::fmax(ay1, t.v[k].y);
        }
        gMinX = ax0; gMinY = ay0;
        gnx = std::max(1, (int)std::ceil((ax1 - ax0) / cs));
        gny = std::max(1, (int)std::ceil((ay1 - ay0) / cs));
        if ((long)gnx * gny > 200000) support = 0;  // too large, skip supports
    }
    if (support) {
        std::vector<std::vector<char>> insideG;
        for (float z = lh * 0.5f; z <= height; z += lh) {
            std::vector<Seg> segs; Seg s;
            for (const Tri& t : tris) if (tri_seg(t, z, s)) segs.push_back(s);
            std::vector<Loop> loops = segs.empty() ? std::vector<Loop>() : chain(segs);
            std::vector<char> grid((size_t)gnx * gny, 0);
            for (int iy = 0; iy < gny; ++iy) for (int ix = 0; ix < gnx; ++ix) {
                float px = gMinX + (ix + 0.5f) * cs, py = gMinY + (iy + 0.5f) * cs;
                bool in = false;
                for (const Loop& lp : loops) {
                    size_t m = lp.size();
                    for (size_t i = 0, j = m - 1; i < m; j = i++) {
                        if (((lp[i].y > py) != (lp[j].y > py)) &&
                            (px < (lp[j].x - lp[i].x) * (py - lp[i].y) / (lp[j].y - lp[i].y) + lp[i].x))
                            in = !in;
                    }
                }
                grid[(size_t)iy * gnx + ix] = in ? 1 : 0;
            }
            insideG.push_back(std::move(grid));
        }
        int NL = (int)insideG.size();
        supportG.assign(NL, std::vector<char>((size_t)gnx * gny, 0));
        for (int k = NL - 2; k >= 0; --k) {
            for (size_t c = 0; c < (size_t)gnx * gny; ++c) {
                bool aboveSolid = insideG[k + 1][c] || supportG[k + 1][c];
                supportG[k][c] = (!insideG[k][c] && aboveSolid) ? 1 : 0;
            }
        }
    }

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

        // perimeters: N walls, with seam placement and wall ordering.
        for (const Loop& lp : loops) {
            if (lp.size() < 3) continue;
            float gx = 0, gy = 0; for (const Pt& p : lp) { gx += p.x; gy += p.y; }
            gx /= lp.size(); gy /= lp.size();
            std::vector<Loop> rings;
            for (int w = 0; w < walls; ++w) {
                float off = w * lw;
                Loop ring; ring.reserve(lp.size());
                for (const Pt& p : lp) {
                    float dx = p.x - gx, dy = p.y - gy; float dl = std::sqrt(dx * dx + dy * dy);
                    float k = dl > off ? (dl - off) / dl : 0.f;
                    ring.push_back({gx + dx * k, gy + dy * k});
                }
                // seam: rotate ring to start at back (max y) or front (min y)
                if (seam == 1 || seam == 2) {
                    size_t best = 0;
                    for (size_t i = 1; i < ring.size(); ++i)
                        if (seam == 1 ? ring[i].y > ring[best].y : ring[i].y < ring[best].y) best = i;
                    std::rotate(ring.begin(), ring.begin() + best, ring.end());
                }
                rings.push_back(std::move(ring));
            }
            if (wallOrder == 1) std::reverse(rings.begin(), rings.end());  // inner-first
            for (const Loop& ring : rings) emit_path(ring, true);
        }

        // infill: even-odd scanline at an arbitrary angle; solid on top/bottom.
        bool solid = (li < solidN) || (z > height - solidN * lh);
        float dens = solid ? 100.f : density;
        if (dens > 0.5f) {
            float base = lw / (dens / 100.f);
            // emit straight infill lines at angleDeg with given spacing (clipped to loops)
            auto infill_angle = [&](float angleDeg, float spacing) {
                float ar = angleDeg * 3.14159265f / 180.f;
                float c = std::cos(-ar), s = std::sin(-ar);   // rotate into scan frame
                float cb = std::cos(ar), sb = std::sin(ar);   // rotate back to world
                std::vector<Loop> rl(loops.size());
                float y0 = 1e30f, y1 = -1e30f;
                for (size_t k = 0; k < loops.size(); ++k) {
                    rl[k].reserve(loops[k].size());
                    for (const Pt& p : loops[k]) {
                        float rx = p.x * c + p.y * s, ry = -p.x * s + p.y * c;
                        rl[k].push_back({rx, ry});
                        y0 = std::fmin(y0, ry); y1 = std::fmax(y1, ry);
                    }
                }
                for (float yv = y0 + spacing * 0.5f; yv < y1; yv += spacing) {
                    std::vector<float> xs;
                    for (const Loop& lp : rl) {
                        size_t m = lp.size();
                        for (size_t i = 0; i < m; ++i) {
                            const Pt& p1 = lp[i]; const Pt& p2 = lp[(i + 1) % m];
                            if ((p1.y <= yv && p2.y > yv) || (p2.y <= yv && p1.y > yv)) {
                                float u = (yv - p1.y) / (p2.y - p1.y);
                                xs.push_back(p1.x + u * (p2.x - p1.x));
                            }
                        }
                    }
                    std::sort(xs.begin(), xs.end());
                    for (size_t i = 0; i + 1 < xs.size(); i += 2) {
                        Pt A{xs[i] * cb - yv * sb, xs[i] * sb + yv * cb};
                        Pt B{xs[i + 1] * cb - yv * sb, xs[i + 1] * sb + yv * cb};
                        std::vector<Pt> line = {A, B}; emit_path(line, false);
                    }
                }
            };
            if (solid) {
                infill_angle((li % 2) ? 135.f : 45.f, base);   // solid top/bottom
            } else switch (pattern) {
                case 1:  // grid
                    infill_angle(0.f, base * 2); infill_angle(90.f, base * 2); break;
                case 2:  // triangles
                    infill_angle(0.f, base * 3); infill_angle(60.f, base * 3); infill_angle(120.f, base * 3); break;
                case 3:  // star / cubic-ish
                    infill_angle(0.f, base * 4); infill_angle(45.f, base * 4);
                    infill_angle(90.f, base * 4); infill_angle(135.f, base * 4); break;
                case 4: {  // concentric: inward contour offsets
                    for (const Loop& lp : loops) {
                        if (lp.size() < 3) continue;
                        float gx = 0, gy = 0; for (const Pt& p : lp) { gx += p.x; gy += p.y; }
                        gx /= lp.size(); gy /= lp.size();
                        for (int r = walls; r < 40; ++r) {
                            float off = r * base; Loop ring; ring.reserve(lp.size()); bool ok = false;
                            for (const Pt& p : lp) {
                                float dx = p.x - gx, dy = p.y - gy; float dl = std::sqrt(dx*dx+dy*dy);
                                float kk = dl > off ? (dl - off) / dl : 0.f; if (kk > 0) ok = true;
                                ring.push_back({gx + dx * kk, gy + dy * kk});
                            }
                            if (!ok) break; emit_path(ring, true);
                        }
                    }
                    break;
                }
                default:  // 0 = line, alternating per layer
                    infill_angle((li % 2) ? 90.f : 0.f, base); break;
            }
            // ironing: fine low-flow pass over the very top surface
            if (iron && z > height - lh * 1.0f) {
                g << "; ironing\n";
                flowMul = ironFlow;
                infill_angle(45.f, lw * 0.3f);
                flowMul = 1.f;
            }
        }

        // supports: sparse lines under detected overhangs
        if (support && li < (int)supportG.size()) {
            const std::vector<char>& sg = supportG[li];
            bool wrote = false;
            for (int iy = 0; iy < gny; ++iy) for (int ix = 0; ix < gnx; ++ix) {
                if (!sg[(size_t)iy * gnx + ix]) continue;
                if (!wrote) { g << "; support\n"; wrote = true; }
                float cx2 = gMinX + (ix + 0.5f) * cs, cy2 = gMinY + (iy + 0.5f) * cs;
                std::vector<Pt> line = {{cx2 - cs * 0.4f, cy2}, {cx2 + cs * 0.4f, cy2}};
                emit_path(line, false);
            }
        }
    }

    g << "M104 S0\nM140 S0\nM107\nG1 Z" << (height + 5) << " F600\nG28 X0 Y0\n; done\n";
    g.flush();
    LOGI("sliced %s -> %d/%d layers", in.c_str(), layers, approxLayers);
    return layers;
}
