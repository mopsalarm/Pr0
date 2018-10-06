package com.pr0gramm.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class Runner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, TestApplicationClass::class.java.name, context)
    }
}