package com.pr0gramm.app.ui.views

import android.content.Context
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware

interface KodeinViewMixin : KodeinAware {
    fun getContext(): Context

    override val kodein: Kodein
        get() = (getContext().applicationContext as KodeinAware).kodein
}