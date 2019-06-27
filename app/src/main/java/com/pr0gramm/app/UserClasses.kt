package com.pr0gramm.app

import android.graphics.Color
import androidx.annotation.ColorInt
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.config.ConfigService
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class UserClassesService(configObservable: Observable<Config>) {
    data class UserClass(val name: String, val symbol: String, @get:ColorInt val color: Int)

    private var userClasses: List<UserClass> = listOf()

    val changes: PublishSubject<Unit> = PublishSubject.create()

    init {
        configObservable
                .map { config -> config.userClasses.map { parseClass(it) } }
                .distinctUntilChanged()
                .subscribe {
                    userClasses = it

                    // publish changes
                    changes.onNext(Unit)
                }
    }

    private fun parseClass(inputValue: Config.UserClass): UserClass {
        val color = try {
            Color.parseColor(inputValue.color)
        } catch (_: Exception) {
            Color.WHITE
        }

        return UserClass(inputValue.name.toUpperCase(Locale.GERMANY), inputValue.symbol, color)
    }

    fun get(mark: Int): UserClass {
        return userClasses.getOrNull(mark) ?: UserClass("User", "?", Color.WHITE)
    }

    companion object {
        operator fun invoke(configService: ConfigService) = UserClassesService(configService.observeConfig())
    }
}
