package com.pr0gramm.app

import android.graphics.Color
import androidx.annotation.ColorInt
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.config.ConfigService

class UserClassesService(configService: ConfigService) {
    class UserClass(val name: String, val symbol: String, @get:ColorInt val color: Int)

    private var userClasses: List<UserClass> = listOf()

    init {
        configService.observeConfig()
                .map { config -> config.userClasses.map { parseClass(it) } }
                .subscribe { userClasses = it }
    }

    private fun parseClass(inputValue: Config.UserClass): UserClass {
        val color = try {
            Color.parseColor(inputValue.color)
        } catch (_: Exception) {
            Color.WHITE
        }

        return UserClass(inputValue.name.toUpperCase(), inputValue.symbol, color)
    }

    fun get(mark: Int): UserClass {
        return userClasses.getOrNull(mark) ?: UserClass("User", "?", Color.WHITE)
    }
}
