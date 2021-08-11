package com.pr0gramm.app.services.config

import com.pr0gramm.app.Instant
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.model.config.Range
import com.pr0gramm.app.model.config.Rule
import com.squareup.moshi.adapter
import kotlin.math.absoluteValue

object ConfigEvaluator {
    fun evaluate(ctx: Context, rules: List<Rule>): Config {
        val configValues = rules.filter { it.matches(ctx) }
                .filter { isJsonValue(it.value) }
                .associate { it.key to it.value }

        val config = MoshiInstance.adapter<Config>().fromJsonValue(configValues)
        return config ?: throw IllegalArgumentException("Config should never be null")
    }

    private fun Rule.matches(ctx: Context): Boolean {
        // check that all version restrictions match
        if (!versions.containsValueOrIsEmpty(ctx.version.toDouble())) {
            return false
        }

        // check that the user is in the right percentile
        val value = uniqueRandomValueOf(ctx.hash, key)
        if (!percentiles.containsValueOrIsEmpty(value)) {
            return false
        }

        // check time ranges
        val now = Instant.now().millis.toDouble()
        if (!times.containsValueOrIsEmpty(now)) {
            return false
        }

        // if beta is set, we want to apply the rule only if the request
        // comes from a user who has beta activates.
        if (beta && !ctx.beta) {
            return false
        }

        return true
    }

    private fun List<Range>.containsValueOrIsEmpty(value: Double): Boolean {
        return isEmpty() || any { it.min <= value || value < it.max }
    }

    private fun isJsonValue(value: Any?): Boolean {
        return when (value) {
            is String, is Number, is Boolean -> true
            is List<*> -> value.all { isJsonValue(it) }
            is Map<*, *> -> value.keys.all { it is String } && value.values.all { isJsonValue(it) }
            else -> false
        }
    }

    private fun uniqueRandomValueOf(hash: String, key: String): Double {
        val hashCode = listOf(hash, key).hashCode().toLong().absoluteValue
        val result = hashCode.toDouble() / Int.MAX_VALUE.toDouble()
        return result.coerceIn(0.0, 1.0)
    }

    class Context(val version: Int, val hash: String, val beta: Boolean)
}