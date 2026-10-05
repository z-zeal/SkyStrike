// Camera transformations and coordinates helper functions

vec2 camRelToWorld(vec2 camRelPos, vec2 camCenter) {
    return camRelPos + camCenter;
}

vec2 worldToCamRel(vec2 worldPos, vec2 camCenter) {
    return worldPos - camCenter;
}

vec2 worldToSdfUv(vec2 worldPos, vec2 worldSize) {
    return worldPos / worldSize;
}

vec2 screenUvToCamRel(vec2 screenUv, vec2 viewportSize) {
    return (screenUv - 0.5) * viewportSize;
}
