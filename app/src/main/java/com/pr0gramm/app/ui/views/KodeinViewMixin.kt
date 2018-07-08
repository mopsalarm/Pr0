package com.pr0gramm.app.ui.views

import android.content.Context
import com.pr0gramm.app.util.kodein
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware

interface KodeinViewMixin : KodeinAware {
    fun getContext(): Context

    override val kodein: Kodein
        get() = getContext().kodein
}