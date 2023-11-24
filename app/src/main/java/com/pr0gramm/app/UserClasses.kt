package com.pr0gramm.app

import android.graphics.Color
import androidx.annotation.ColorInt
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.model.config.DefaultUserClasses
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

class UserClassesService(configObservable: Flow<Config>) {
    constructor(configService: ConfigService) : this(configService.observeConfig())

    data class UserClass(val name: String, val symbol: String, @get:ColorInt val color: Int)

    private var userClasses: List<UserClass> = DefaultUserClasses.map(this::parseClass)

    private val mutableChanges = MutableStateFlow(0)

    val onChange: Flow<Unit> = mutableChanges.map { }

    init {
        doInBackground {
            configObservable
                    .map { config -> config.userClasses.map { parseClass(it) } }
                    .distinctUntilChanged()
                    .collect {
                        userClasses = it

                        // publish changes
                        mutableChanges.value++
                    }
        }
    }

    private fun parseClass(inputValue: Config.UserClass): UserClass {
        val color = try {
            Color.parseColor(inputValue.color)
        } catch (_: Exception) {
            Color.WHITE
        }

        return UserClass(inputValue.name.uppercase(Locale.GERMANY), inputValue.symbol, color)
    }

    fun get(mark: Int): UserClass {
        return userClasses.getOrNull(mark) ?: UserClass("User", "?", Color.WHITE)
    }
}
