#include <jni.h>
#include <stdlib.h>
#include <stdbool.h>

#include <vpx/vp8dx.h>
#include <vpx/vpx_decoder.h>

#include <libyuv/convert_argb.h>
#include <libyuv/scale.h>

#include <android/bitmap.h>

#include <coffeecatch/coffeecatch.h>
#include <coffeecatch/coffeejni.h>

#define LOG if(false) __android_log_print(ANDROID_LOG_INFO, "VPX",

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

static jint throw_VpxException(JNIEnv *env, const char *message) {
    jclass exClass = (*env)->FindClass(env, "com/pr0gramm/app/vpx/VpxException");
    return (*env)->ThrowNew(env, exClass, message);
}

JNIEXPORT jstring JNICALL
Java_com_pr0gramm_app_vpx_VpxWrapper_getVpxString(JNIEnv *env) {
  vpx_codec_ctx_t codec;
  const vpx_codec_iface_t *decoder = vpx_codec_vp8_dx();
  if(!decoder) {
    return (*env)->NewStringUTF(env, "Could not create decoder");
  }

  return (*env)->NewStringUTF(env, vpx_codec_iface_name(decoder));
}


JNIEXPORT jlong JNICALL
Java_com_pr0gramm_app_vpx_VpxWrapper_vpxNewWrapper(JNIEnv *env) {
  struct vpx_wrapper *wrapper = vpx_wrapper_init();
  if(wrapper->error) {
    throw_VpxException(env, wrapper->error);
    vpx_wrapper_destroy(wrapper);
    return 0;
  }

  return (long) wrapper;
}

JNIEXPORT void JNICALL
Java_com_pr0gramm_app_vpx_VpxWrapper_vpxFreeWrapper(JNIEnv *env, jlong wrapper_addr) {
  struct vpx_wrapper *wrapper = (struct vpx_wrapper*) wrapper_addr;
  vpx_wrapper_destroy(wrapper);
}

JNIEXPORT void JNICALL
Java_com_pr0gramm_app_vpx_VpxWrapper_vpxPutData(JNIEnv *env,
    jlong wrapper_addr, jbyteArray array, jint offset, jint length) {

  struct vpx_wrapper *wrapper = (struct vpx_wrapper*) wrapper_addr;

  jbyte* bytes = (*env)->GetByteArrayElements(env, array, NULL);

  if(!vpx_wrapper_put_data(wrapper, bytes + offset, length)) {
    throw_VpxException(env, wrapper->error);
  }

  (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}

static int multiple_of(int what, int value) {
  if(value % what) {
    value += what - (value % what);
  }

  return value;
}

jboolean real_vpxGetFrame(JNIEnv *env, struct vpx_wrapper *wrapper, jobject bitmap, jint pixel_skip) {
  // get the next image from codec
  vpx_image_t *image = vpx_wrapper_next_frame(wrapper);
  if(!image)
    return false;

  if(!bitmap)
    return true;

  // get info about the target bitmap
  AndroidBitmapInfo bitmap_info;
  if(AndroidBitmap_getInfo(env, bitmap, &bitmap_info)) {
    throw_VpxException(env, "Could not get bitmap info");
    return false;
  }

  // get the planes and strides
  bool planes_need_free = false;
  unsigned char *plane_y = NULL, *plane_u = NULL, *plane_v = NULL;
  int stride_y = 0, stride_u = 0, stride_v = 0;
  int image_width = 0, image_height = 0;

  #define min(a, b) (((a) < (b)) ? (a) : (b))

  jboolean result = false;
  if(pixel_skip && image->fmt == VPX_IMG_FMT_I420) {
    planes_need_free = true;

    int factor = pixel_skip + 1;

    stride_y = multiple_of(8, image->stride[VPX_PLANE_Y] / factor);
    stride_u = multiple_of(8, image->stride[VPX_PLANE_U] / factor);
    stride_v = multiple_of(8, image->stride[VPX_PLANE_V] / factor);
    if(stride_y <= 0 && stride_u <= 0 && stride_v <= 0) {
      throw_VpxException(env, "Stride values are not positive");
      goto exit;
    }

    image_width = min(image->d_w / factor, stride_y);
    image_height = image->d_h / factor;
    if(image_width <= 0 || image_height <= 0) {
      throw_VpxException(env, "Image size not positive");
      goto exit;
    }

    if(image_width > stride_y) {
      throw_VpxException(env, "Image width larger than image stride");
      goto exit;
    }

    plane_y = malloc(stride_y * image_height);
    plane_u = malloc(stride_u * image_height);
    plane_v = malloc(stride_v * image_height);
    if(!plane_y || !plane_u || !plane_v) {
      throw_VpxException(env, "Could not allocate memory for smaller frame");
      goto exit;
    }

    I420Scale(
      image->planes[VPX_PLANE_Y], image->stride[VPX_PLANE_Y],
      image->planes[VPX_PLANE_U], image->stride[VPX_PLANE_U],
      image->planes[VPX_PLANE_V], image->stride[VPX_PLANE_V],
      image->d_w, image->d_h,
      plane_y, stride_y,
      plane_u, stride_u,
      plane_v, stride_v,
      image_width, image_height,
      kFilterNone);

  } else {
    planes_need_free = false;

    image_width = image->d_w;
    image_height = image->d_h;

    stride_y = image->stride[VPX_PLANE_Y];
    stride_u = image->stride[VPX_PLANE_U];
    stride_v = image->stride[VPX_PLANE_V];

    plane_y = image->planes[VPX_PLANE_Y];
    plane_u = image->planes[VPX_PLANE_U];
    plane_v = image->planes[VPX_PLANE_V];
  }

  unsigned char *target;
  AndroidBitmap_lockPixels(env, bitmap, (void **) &target);

  int width = min(bitmap_info.width, image_width);
  int height = min(bitmap_info.height, image_height);

  int y, x;
  switch (image->fmt) {
    case VPX_IMG_FMT_I420:
      I420ToABGR(plane_y, stride_y, plane_u, stride_u, plane_v, stride_v,
        target, bitmap_info.stride, width, height);
      break;

    default:
      throw_VpxException(env, "Can not decode image, unknown pixel format");
      break;
  };

exit:
  AndroidBitmap_unlockPixels(env, bitmap);

  if(planes_need_free) {
    free(plane_y);
    free(plane_u);
    free(plane_v);
  }

  return true;
}

#define COFFEE_TRY_JNI_NO_ALARM(ENV, CODE)       \
  do {                                  \
    COFFEE_TRY() {                      \
      CODE;                             \
    } COFFEE_CATCH() {                  \
      coffeecatch_throw_exception(ENV); \
      coffeecatch_cancel_pending_alarm(); \
    } COFFEE_END();                     \
  } while(0)

 __attribute__ ((noinline))
void protected_vpxGetFrame(JNIEnv *env, struct vpx_wrapper *wrapper, jobject bitmap, jint pixel_skip, jboolean *result) {
  COFFEE_TRY_JNI_NO_ALARM(env, *result=real_vpxGetFrame(env, wrapper, bitmap, pixel_skip));
}

JNIEXPORT jboolean JNICALL
Java_com_pr0gramm_app_vpx_VpxWrapper_vpxGetFrame(JNIEnv *env, jlong wrapper_addr, jobject bitmap, jint pixel_skip) {
  struct vpx_wrapper *wrapper = (struct vpx_wrapper*) wrapper_addr;

  jboolean result = false;
  protected_vpxGetFrame(env, wrapper, bitmap, pixel_skip, &result);
  return result;
}
