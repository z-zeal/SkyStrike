// Signed Distance Field (SDF) sampling, gradient and sphere-tracing helpers

float sampleSdf(sampler2D sdfTex, vec2 worldPos, vec2 worldSize) {
    vec2 uv = worldPos / worldSize;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return -127.0;
    }
    float raw = texture2D(sdfTex, uv).r;
    return (raw * 255.0) - 128.0;
}

vec2 sampleSdfGradient(sampler2D sdfTex, vec2 worldPos, vec2 worldSize, float eps) {
    float dx = sampleSdf(sdfTex, worldPos + vec2(eps, 0.0), worldSize) - sampleSdf(sdfTex, worldPos - vec2(eps, 0.0), worldSize);
    float dy = sampleSdf(sdfTex, worldPos + vec2(0.0, eps), worldSize) - sampleSdf(sdfTex, worldPos - vec2(0.0, eps), worldSize);
    float len = length(vec2(dx, dy));
    if (len > 0.0001) {
        return vec2(dx, dy) / len;
    }
    return vec2(0.0, 1.0);
}

float sdfSoftShadow(sampler2D sdfTex, vec2 origin, vec2 target, vec2 worldSize, float k, int maxSteps) {
    vec2 dir = target - origin;
    float totalDist = length(dir);
    if (totalDist < 1.0) {
        return 1.0;
    }
    vec2 rayDir = dir / totalDist;
    float res = 1.0;
    float t = 4.0;

    for (int i = 0; i < 48; i++) {
        if (i >= maxSteps || t >= totalDist) {
            break;
        }
        vec2 samplePos = origin + rayDir * t;
        float d = sampleSdf(sdfTex, samplePos, worldSize);
        if (d < 0.5) {
            return 0.0;
        }
        res = min(res, (k * d) / t);
        t += max(d * 0.85, 2.0);
    }
    return clamp(res, 0.0, 1.0);
}
