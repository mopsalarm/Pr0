#include <jni.h>
#include <stdlib.h>
#include <stdbool.h>

#include <vp8dx.h>
#include <vpx_decoder.h>

struct vpx_wrapper {
  const vpx_codec_iface_t *decoder;

  vpx_codec_ctx_t codec;
  vpx_codec_iter_t iter;

  void *input_data;

  const char *error;
};

static struct vpx_wrapper* vpx_wrapper_init() {
  struct vpx_wrapper *wrapper = calloc(1, sizeof(struct vpx_wrapper));
  wrapper->decoder = vpx_codec_vp8_dx();

  if(vpx_codec_dec_init(&wrapper->codec, wrapper->decoder, NULL, 0)) {
    wrapper->error = vpx_codec_error_detail(&wrapper->codec);
  }

  return wrapper;
}

static void vpx_wrapper_destroy(struct vpx_wrapper* wrapper) {
  vpx_codec_destroy(&wrapper->codec);
  free(wrapper->input_data);
  free(wrapper);
}

static vpx_image_t* vpx_wrapper_next_frame(struct vpx_wrapper* wrapper) {
  return vpx_codec_get_frame(&wrapper->codec, &wrapper->iter);
}

static bool vpx_wrapper_put_data(struct vpx_wrapper* wrapper, const char *data, unsigned size) {
  // copy new frame data
  free(wrapper->input_data);
  wrapper->input_data = malloc(size);
  memcpy(wrapper->input_data, data, size);

  // reset the iterator
  wrapper->iter = NULL;

  if (vpx_codec_decode(&wrapper->codec, wrapper->input_data, size, NULL, 0)) {
    wrapper->error = vpx_codec_error_detail(&wrapper->codec);
    return false;
  }

  return true;
}

static jint throw_RuntimeException(JNIEnv *env, const char *message) {
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    return (*env)->ThrowNew(env, exClass, message);
}

JNIEXPORT jstring JNICALL
Java_com_pr0gramm_app_webm_WebmJNI_getVpxString(JNIEnv *env) {
  vpx_codec_ctx_t codec;
  const vpx_codec_iface_t *decoder = vpx_codec_vp8_dx();
  if(!decoder) {
    return (*env)->NewStringUTF(env, "Could not create decoder");
  }

  return (*env)->NewStringUTF(env, vpx_codec_iface_name(decoder));
}


JNIEXPORT jlong JNICALL
Java_com_pr0gramm_app_webm_WebmJNI_newVpxWrapper(JNIEnv *env) {
  struct vpx_wrapper *wrapper = vpx_wrapper_init();
  if(wrapper->error) {
    throw_RuntimeException(env, wrapper->error);
    vpx_wrapper_destroy(wrapper);
    return 0;
  }

  return (long) wrapper;
}

JNIEXPORT void JNICALL
Java_com_pr0gramm_app_webm_WebmJNI_freeVpxWrapper(JNIEnv *env, jlong wrapper_addr) {
  struct vpx_wrapper *wrapper = (struct vpx_wrapper*) wrapper_addr;
  vpx_wrapper_destroy(wrapper);
}

JNIEXPORT void JNICALL
Java_com_pr0gramm_app_webm_WebmJNI_vpxPutData(JNIEnv *env,
    jlong wrapper_addr, jbyteArray array, jint offset, jint length) {

  struct vpx_wrapper *wrapper = (struct vpx_wrapper*) wrapper_addr;

  jbyte* bytes = (*env)->GetByteArrayElements(env, array, NULL);

  if(!vpx_wrapper_put_data(wrapper, bytes + offset, length)) {
    throw_RuntimeException(env, wrapper->error);
  }

  (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}


JNIEXPORT jboolean JNICALL
Java_com_pr0gramm_app_webm_WebmJNI_vpxGetFrame(JNIEnv *env, jlong wrapper_addr) {
  struct vpx_wrapper *wrapper = (struct vpx_wrapper*) wrapper_addr;
  return vpx_wrapper_next_frame(wrapper) != NULL;
}
