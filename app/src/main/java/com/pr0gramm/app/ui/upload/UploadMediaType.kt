package com.pr0gramm.app.ui.upload

import com.pr0gramm.app.ui.views.viewer.MediaUri

enum class UploadMediaType(val mimeType: String, val mediaUriType: MediaUri.MediaType) {
    IMAGE("image/*", MediaUri.MediaType.IMAGE),
    VIDEO("video/*", MediaUri.MediaType.VIDEO),
}