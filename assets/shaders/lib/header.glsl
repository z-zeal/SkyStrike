#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#else
#define LOWP
#define MED
#define HIGH
#endif
