#include <jni.h>

#include <vp8dx.h>
#include <vpx_decoder.h>

JNIEXPORT jstring JNICALL
Java_com_pr0gramm_app_webm_WebmJNI_getVpxString(JNIEnv *env) {
  vpx_codec_ctx_t codec;
  const vpx_codec_iface_t *decoder = vpx_codec_vp8_dx();
  if(!decoder) {
    return (*env)->NewStringUTF(env, "Could not create decoder");
  }

  return (*env)->NewStringUTF(env, vpx_codec_iface_name(decoder));
}
