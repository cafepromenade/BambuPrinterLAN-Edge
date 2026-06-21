// Bambu Printer LAN — native slicing engine.
//
// A real (if minimal) slicer: parses an STL (binary or ASCII), slices it into
// layers at the configured layer height, chains the per-layer contour segments
// into perimeter polylines, and emits printable G-code. This is the first-party
// engine seam; full libslic3r features layer on top of this.
#include <jni.h>
#include <android/log.h>
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

struct Vec3 { float x, y, z; };
struct Tri  { Vec3 v[3]; };
struct Seg  { float x0, y0, x1, y1; };

bool read_file(const std::string& path, std::vector<char>& out) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    std::streamsize n = f.tellg();
    if (n <= 0) return false;
    f.seekg(0);
    out.resize(static_cast<size_t>(n));
    return static_cast<bool>(f.read(out.data(), n));
}

bool is_ascii_stl(const std::vector<char>& b) {
    if (b.size() < 6) return false;
    if (std::strncmp(b.data(), "solid", 5) != 0) return false;
    if (b.size() >= 84) {
        uint32_t count = 0;
        std::memcpy(&count, b.data() + 80, 4);
        if (b.size() == 84ull + 50ull * count) return false;  // matches binary layout
    }
    return true;
}

void parse_binary(const std::vector<char>& b, std::vector<Tri>& tris) {
    if (b.size() < 84) return;
    uint32_t count = 0;
    std::memcpy(&count, b.data() + 80, 4);
    size_t need = 84ull + 50ull * count;
    if (b.size() < need) count = static_cast<uint32_t>((b.size() - 84) / 50);
    tris.reserve(count);
    const char* p = b.data() + 84;
    for (uint32_t i = 0; i < count; ++i, p += 50) {
        Tri t;
        float f[9];
        std::memcpy(f, p + 12, sizeof(f));  // skip 12-byte normal, read 9 floats
        for (int k = 0; k < 3; ++k) {
            t.v[k].x = f[k * 3 + 0];
            t.v[k].y = f[k * 3 + 1];
            t.v[k].z = f[k * 3 + 2];
        }
        tris.push_back(t);
    }
}

void parse_ascii(const std::vector<char>& b, std::vector<Tri>& tris) {
    std::string s(b.begin(), b.end());
    std::istringstream in(s);
    std::string tok;
    Tri t; int vi = 0;
    while (in >> tok) {
        if (tok == "vertex") {
            in >> t.v[vi].x >> t.v[vi].y >> t.v[vi].z;
            if (++vi == 3) { tris.push_back(t); vi = 0; }
        }
    }
}

void slice_tri(const Tri& t, float zp, std::vector<Seg>& segs) {
    float pts[2][2];
    int n = 0;
    for (int e = 0; e < 3 && n < 2; ++e) {
        const Vec3& a = t.v[e];
        const Vec3& c = t.v[(e + 1) % 3];
        float za = a.z - zp, zc = c.z - zp;
        if ((za > 0 && zc > 0) || (za < 0 && zc < 0)) continue;
        if (za == zc) continue;
        float u = za / (za - zc);
        pts[n][0] = a.x + u * (c.x - a.x);
        pts[n][1] = a.y + u * (c.y - a.y);
        ++n;
    }
    if (n == 2) segs.push_back({pts[0][0], pts[0][1], pts[1][0], pts[1][1]});
}

std::vector<std::vector<std::pair<float, float>>> chain(std::vector<Seg> segs) {
    std::vector<std::vector<std::pair<float, float>>> loops;
    std::vector<bool> used(segs.size(), false);
    const float eps = 0.01f;
    for (size_t i = 0; i < segs.size(); ++i) {
        if (used[i]) continue;
        used[i] = true;
        std::vector<std::pair<float, float>> poly;
        poly.push_back(std::make_pair(segs[i].x0, segs[i].y0));
        float ex = segs[i].x1, ey = segs[i].y1;
        poly.push_back(std::make_pair(ex, ey));
        bool extended = true;
        while (extended) {
            extended = false;
            for (size_t j = 0; j < segs.size(); ++j) {
                if (used[j]) continue;
                if (std::fabs(segs[j].x0 - ex) < eps && std::fabs(segs[j].y0 - ey) < eps) {
                    ex = segs[j].x1; ey = segs[j].y1;
                } else if (std::fabs(segs[j].x1 - ex) < eps && std::fabs(segs[j].y1 - ey) < eps) {
                    ex = segs[j].x0; ey = segs[j].y0;
                } else continue;
                used[j] = true;
                poly.push_back(std::make_pair(ex, ey));
                extended = true;
                break;
            }
        }
        loops.push_back(std::move(poly));
    }
    return loops;
}

float parse_layer_height(const std::string& ini) {
    auto pos = ini.find("layer_height");
    if (pos == std::string::npos) return 0.2f;
    auto eq = ini.find('=', pos);
    if (eq == std::string::npos) return 0.2f;
    float v = std::strtof(ini.c_str() + eq + 1, nullptr);
    return (v > 0.01f && v < 2.0f) ? v : 0.2f;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_bambuprinterlan_engine_SlicerBridge_nativeEngineVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("Bambu Printer LAN native engine 0.2 (STL slicer)");
}

// Slice an STL to G-code. Returns the number of layers, or a negative error code.
extern "C" JNIEXPORT jint JNICALL
Java_com_bambuprinterlan_engine_SlicerBridge_nativeSlice(
        JNIEnv* env, jobject, jstring jin, jstring jout, jstring jcfg) {
    const char* cin = env->GetStringUTFChars(jin, nullptr);
    const char* cout = env->GetStringUTFChars(jout, nullptr);
    const char* ccfg = env->GetStringUTFChars(jcfg, nullptr);
    std::string in = cin ? cin : "";
    std::string out = cout ? cout : "";
    std::string cfg = ccfg ? ccfg : "";
    if (cin) env->ReleaseStringUTFChars(jin, cin);
    if (cout) env->ReleaseStringUTFChars(jout, cout);
    if (ccfg) env->ReleaseStringUTFChars(jcfg, ccfg);

    std::vector<char> bytes;
    if (!read_file(in, bytes)) return -1;

    std::vector<Tri> tris;
    if (is_ascii_stl(bytes)) parse_ascii(bytes, tris);
    else parse_binary(bytes, tris);
    if (tris.empty()) return -2;

    float minz = 1e30f, maxz = -1e30f;
    for (const Tri& t : tris)
        for (int k = 0; k < 3; ++k) {
            minz = std::fmin(minz, t.v[k].z);
            maxz = std::fmax(maxz, t.v[k].z);
        }
    float lh = parse_layer_height(cfg);

    std::ofstream g(out);
    if (!g) return -3;
    g << "; Bambu Printer LAN native engine 0.2\n";
    g << "; triangles=" << tris.size() << " layer_height=" << lh << "\n";
    g << "G21 ; mm\nG90 ; absolute\nM83 ; relative extrusion\nG28 ; home\n";

    int layers = 0;
    const float e_per_mm = 0.04f;
    for (float z = minz + lh * 0.5f; z <= maxz; z += lh) {
        std::vector<Seg> segs;
        for (const Tri& t : tris) slice_tri(t, z, segs);
        if (segs.empty()) continue;
        ++layers;
        g << "; layer " << layers << " z=" << z << "\n";
        g << "G1 Z" << z << " F600\n";
        for (auto& loop : chain(segs)) {
            if (loop.size() < 2) continue;
            g << "G0 X" << loop[0].first << " Y" << loop[0].second << " F4800\n";
            for (size_t i = 1; i < loop.size(); ++i) {
                float dx = loop[i].first - loop[i - 1].first;
                float dy = loop[i].second - loop[i - 1].second;
                float ext = std::sqrt(dx * dx + dy * dy) * e_per_mm;
                g << "G1 X" << loop[i].first << " Y" << loop[i].second
                  << " E" << ext << " F1800\n";
            }
        }
    }
    g << "M104 S0\nM140 S0\nG28 X0 Y0\n; done\n";
    g.flush();
    LOGI("sliced %s -> %d layers", in.c_str(), layers);
    return layers;
}
