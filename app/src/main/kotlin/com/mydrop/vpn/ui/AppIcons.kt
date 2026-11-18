package com.mydrop.vpn.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mydrop.vpn.MainActivity
import com.mydrop.vpn.shared.R

/** The launcher icons the app ships; each is an `<activity-alias>` in the manifest. */
enum class AppIcon(val alias: String, @StringRes val title: Int, @DrawableRes val preview: Int) {
    Charcoal("AppIconCharcoal", R.string.app_icon_1, R.drawable.avaca_icon_1),
    Emerald("AppIconEmerald", R.string.app_icon_2, R.drawable.avaca_icon_2),
    Midnight("AppIconMidnight", R.string.app_icon_3, R.drawable.avaca_icon_3),
    Bordeaux("AppIconBordeaux", R.string.app_icon_4, R.drawable.avaca_icon_4),
    Bronze("AppIconBronze", R.string.app_icon_5, R.drawable.avaca_icon_5),
    Plum("AppIconPlum", R.string.app_icon_6, R.drawable.avaca_icon_6),
    Pine("AppIconPine", R.string.app_icon_7, R.drawable.avaca_icon_7),
    ;

    companion object {
        /** Enabled in the manifest; the others start disabled. */
        val Default = Charcoal
    }
}

/**
 * Which launcher icon is showing, and switching it.
 *
 * Read from the package manager rather than stored: the enabled alias *is* the setting, and a copy
 * of it elsewhere could only disagree with what the launcher shows.
 */
object AppIcons {

    fun current(context: Context): AppIcon {
        val manager = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (manager.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.Default
                else -> false
            }
        } ?: AppIcon.Default
    }

    /** The new entry is enabled before the others are disabled, so the launcher is never left with none. */
    fun apply(context: Context, icon: AppIcon) {
        val manager = context.packageManager
        manager.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AppIcon.entries.filter { it != icon }.forEach { other ->
            manager.setComponentEnabledSetting(
                component(context, other),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** Aliases live in MainActivity's package (the manifest namespace), not under the application id. */
    private fun component(context: Context, icon: AppIcon) =
        ComponentName(context.packageName, MainActivity::class.java.name.substringBeforeLast('.') + "." + icon.alias)
}
