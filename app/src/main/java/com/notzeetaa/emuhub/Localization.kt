package com.notzeetaa.emuhub

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    PORTUGUESE_PORTUGAL("pt-PT"),
    PORTUGUESE_BRAZIL("pt-BR"),
    SPANISH("es"),
    FRENCH("fr"),
    GERMAN("de")
}

private val LocalAppResources = staticCompositionLocalOf<Resources> {
    error("App resources were not provided")
}

@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resources = androidx.compose.runtime.remember(context, language) {
        resourcesForLanguage(context, language)
    }

    CompositionLocalProvider(LocalAppResources provides resources) {
        content()
    }
}

private fun resourcesForLanguage(context: Context, language: AppLanguage): Resources {
    if (language == AppLanguage.SYSTEM || language.languageTag == null) {
        return context.resources
    }

    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(language.languageTag))
    return context.createConfigurationContext(configuration).resources
}

@Composable
fun appString(@StringRes id: Int, vararg args: Any): String =
    LocalAppResources.current.getString(id, *args)

fun appStringFor(
    context: Context,
    language: AppLanguage,
    @StringRes id: Int,
    vararg args: Any
): String = resourcesForLanguage(context, language).getString(id, *args)
