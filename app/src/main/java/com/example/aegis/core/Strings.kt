package com.example.aegis.core

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * Looks up strings in the language the user picked, for code that has no Activity to ask
 * (API error messages, ViewModels). Call init() once from Application.onCreate().
 */
object Strings {

    private lateinit var app: Application

    fun init(application: Application) {
        app = application
    }

    fun get(@StringRes id: Int, vararg args: Any): String = localizedContext().getString(id, *args)

    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return app
        val config = Configuration(app.resources.configuration)
        config.setLocale(locales[0])
        return app.createConfigurationContext(config)
    }
}
