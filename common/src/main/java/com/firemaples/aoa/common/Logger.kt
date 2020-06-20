package com.firemaples.aoa.common

import android.util.Log

inline fun <reified T> T.getLogger(): Logger {
    return Logger(T::class.java.simpleName)
}

class Logger(private val className: String) {
    companion object {
        var logInterceptor: MutableList<(Level, String, Throwable?) -> Unit> = mutableListOf()
    }

    fun verbose(msg: String, t: Throwable? = null) = log(Level.VERBOSE, msg, t)
    fun debug(msg: String, t: Throwable? = null) = log(Level.DEBUG, msg, t)
    fun info(msg: String, t: Throwable? = null) = log(Level.INFO, msg, t)
    fun warn(msg: String, t: Throwable? = null) = log(Level.WARN, msg, t)
    fun error(msg: String, t: Throwable? = null) = log(Level.ERROR, msg, t)

    fun log(level: Level, msg: String, t: Throwable? = null) {
        when (level) {
            Level.VERBOSE -> Log.v(className, msg, t)
            Level.DEBUG -> Log.d(className, msg, t)
            Level.INFO -> Log.i(className, msg, t)
            Level.WARN -> Log.w(className, msg, t)
            Level.ERROR -> Log.e(className, msg, t)
        }

        logInterceptor.forEach { it.invoke(level, msg, t) }
    }

    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}