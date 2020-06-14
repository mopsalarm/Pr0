package com.pr0gramm.app.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


fun <T> MutableLiveData<T>.readOnly(): LiveData<T> {
    return this
}
