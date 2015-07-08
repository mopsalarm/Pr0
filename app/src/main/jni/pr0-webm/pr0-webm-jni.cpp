#include <jni.h>

#include <sstream>

#include <vp8dx.h>
#include <vpx_decoder.h>
#include <libwebm/mkvparser.hpp>

extern "C" {
  JNIEXPORT jstring JNICALL
  Java_com_pr0gramm_app_webm_WebmJNI_getVpxString(JNIEnv *env) {
    vpx_codec_ctx_t codec;
    const vpx_codec_iface_t *decoder = vpx_codec_vp8_dx();
    if(!decoder) {
      return env->NewStringUTF("Could not create decoder");
    }

    return env->NewStringUTF(vpx_codec_iface_name(decoder));
  }

  JNIEXPORT jstring JNICALL
  Java_com_pr0gramm_app_webm_WebmJNI_getWebmString(JNIEnv *env) {
    int major, minor, build, revision;
    mkvparser::GetVersion(major, minor, build, revision);

    std::ostringstream version;
    version << "major: " << major << ", minor: " << minor;
    return env->NewStringUTF(version.str().c_str());
  }
}
