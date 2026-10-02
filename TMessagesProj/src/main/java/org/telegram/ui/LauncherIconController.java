package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    /** Классика Telegram и набор UwU. Стандартная exteraless-иконка убрана:
     *  её место занимает тёмный кот, он же пункт по умолчанию. */
    public static boolean isAvailableInSelector(LauncherIcon icon) {
        return icon == LauncherIcon.EXTERALESS
                || icon == LauncherIcon.TELEGRAM
                || (icon.key != null && icon.key.startsWith("Uwu") && icon != LauncherIcon.UWU12KITTY);
    }

    /** Готовая картинка целиком, без зума adaptive-foreground. */
    public static boolean isFullArt(LauncherIcon icon) {
        return icon == LauncherIcon.EXTERALESS
                || (icon.key != null && icon.key.startsWith("Uwu"));
    }

    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                if (isAvailableInSelector(icon)) {
                    return;
                }
                // Иконки больше нет в селекторе — возвращаем фирменную.
                break;
            }
        }

        setIcon(LauncherIcon.EXTERALESS);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        // Пока пользователь ничего не выбирал, включённой считается наша иконка:
        // именно она стоит у <application> в манифесте, и переключатель должен
        // показывать выбранным то, что человек видит на рабочем столе.
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.EXTERALESS;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        EXTERALESS("ExteralessIcon", R.drawable.uwu_icon_12_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconExteraless),
        UWU01BOY("Uwu01Boy", R.drawable.uwu_icon_01_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu01Boy),
        UWU01KITTY("Uwu01Kitty", R.drawable.uwu_icon_01_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu01Kitty),
        UWU02BOY("Uwu02Boy", R.drawable.uwu_icon_02_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu02Boy),
        UWU02KITTY("Uwu02Kitty", R.drawable.uwu_icon_02_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu02Kitty),
        UWU03BOY("Uwu03Boy", R.drawable.uwu_icon_03_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu03Boy),
        UWU03KITTY("Uwu03Kitty", R.drawable.uwu_icon_03_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu03Kitty),
        UWU04BOY("Uwu04Boy", R.drawable.uwu_icon_04_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu04Boy),
        UWU04KITTY("Uwu04Kitty", R.drawable.uwu_icon_04_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu04Kitty),
        UWU05BOY("Uwu05Boy", R.drawable.uwu_icon_05_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu05Boy),
        UWU05KITTY("Uwu05Kitty", R.drawable.uwu_icon_05_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu05Kitty),
        UWU06BOY("Uwu06Boy", R.drawable.uwu_icon_06_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu06Boy),
        UWU06KITTY("Uwu06Kitty", R.drawable.uwu_icon_06_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu06Kitty),
        UWU07BOY("Uwu07Boy", R.drawable.uwu_icon_07_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu07Boy),
        UWU07KITTY("Uwu07Kitty", R.drawable.uwu_icon_07_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu07Kitty),
        UWU08BOY("Uwu08Boy", R.drawable.uwu_icon_08_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu08Boy),
        UWU08KITTY("Uwu08Kitty", R.drawable.uwu_icon_08_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu08Kitty),
        UWU09BOY("Uwu09Boy", R.drawable.uwu_icon_09_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu09Boy),
        UWU09KITTY("Uwu09Kitty", R.drawable.uwu_icon_09_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu09Kitty),
        UWU10BOY("Uwu10Boy", R.drawable.uwu_icon_10_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu10Boy),
        UWU10KITTY("Uwu10Kitty", R.drawable.uwu_icon_10_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu10Kitty),
        UWU11BOY("Uwu11Boy", R.drawable.uwu_icon_11_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu11Boy),
        UWU11KITTY("Uwu11Kitty", R.drawable.uwu_icon_11_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu11Kitty),
        UWU12BOY("Uwu12Boy", R.drawable.uwu_icon_12_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu12Boy),
        UWU12KITTY("Uwu12Kitty", R.drawable.uwu_icon_12_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu12Kitty),
        UWU13BOY("Uwu13Boy", R.drawable.uwu_icon_13_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu13Boy),
        UWU13KITTY("Uwu13Kitty", R.drawable.uwu_icon_13_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu13Kitty),
        UWU14BOY("Uwu14Boy", R.drawable.uwu_icon_14_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu14Boy),
        UWU14KITTY("Uwu14Kitty", R.drawable.uwu_icon_14_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu14Kitty),
        UWU15BOY("Uwu15Boy", R.drawable.uwu_icon_15_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu15Boy),
        UWU15KITTY("Uwu15Kitty", R.drawable.uwu_icon_15_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu15Kitty),
        UWU16BOY("Uwu16Boy", R.drawable.uwu_icon_16_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu16Boy),
        UWU16KITTY("Uwu16Kitty", R.drawable.uwu_icon_16_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu16Kitty),
        UWU17BOY("Uwu17Boy", R.drawable.uwu_icon_17_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu17Boy),
        UWU17KITTY("Uwu17Kitty", R.drawable.uwu_icon_17_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu17Kitty),
        UWU18BOY("Uwu18Boy", R.drawable.uwu_icon_18_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu18Boy),
        UWU18KITTY("Uwu18Kitty", R.drawable.uwu_icon_18_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu18Kitty),
        UWU19BOY("Uwu19Boy", R.drawable.uwu_icon_19_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu19Boy),
        UWU19KITTY("Uwu19Kitty", R.drawable.uwu_icon_19_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu19Kitty),
        UWU20BOY("Uwu20Boy", R.drawable.uwu_icon_20_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu20Boy),
        UWU20KITTY("Uwu20Kitty", R.drawable.uwu_icon_20_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu20Kitty),
        UWU21BOY("Uwu21Boy", R.drawable.uwu_icon_21_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu21Boy),
        UWU21KITTY("Uwu21Kitty", R.drawable.uwu_icon_21_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu21Kitty),
        UWU22BOY("Uwu22Boy", R.drawable.uwu_icon_22_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu22Boy),
        UWU22KITTY("Uwu22Kitty", R.drawable.uwu_icon_22_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu22Kitty),
        UWU23BOY("Uwu23Boy", R.drawable.uwu_icon_23_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu23Boy),
        UWU23KITTY("Uwu23Kitty", R.drawable.uwu_icon_23_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu23Kitty),
        UWU24BOY("Uwu24Boy", R.drawable.uwu_icon_24_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu24Boy),
        UWU24KITTY("Uwu24Kitty", R.drawable.uwu_icon_24_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu24Kitty),
        UWU25BOY("Uwu25Boy", R.drawable.uwu_icon_25_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu25Boy),
        UWU25KITTY("Uwu25Kitty", R.drawable.uwu_icon_25_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu25Kitty),
        UWU26BOY("Uwu26Boy", R.drawable.uwu_icon_26_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu26Boy),
        UWU26KITTY("Uwu26Kitty", R.drawable.uwu_icon_26_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu26Kitty),
        UWU27BOY("Uwu27Boy", R.drawable.uwu_icon_27_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu27Boy),
        UWU27KITTY("Uwu27Kitty", R.drawable.uwu_icon_27_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu27Kitty),
        UWU28BOY("Uwu28Boy", R.drawable.uwu_icon_28_boy,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu28Boy),
        UWU28KITTY("Uwu28Kitty", R.drawable.uwu_icon_28_kitty,
                R.drawable.app_icon_empty_foreground, R.string.AppIconUwu28Kitty),
        BLUEPRINT("BlueprintIcon", R.drawable.blueprint_icon_background,
                R.drawable.blueprint_icon_foreground, R.string.AppIconBlueprint),
        RED("RedIcon", R.drawable.red_icon_background,
                R.drawable.red_icon_foreground, R.string.AppIconRed),
        NYA("NyaIcon", R.drawable.nya_icon_background,
                R.drawable.nya_icon_foreground, R.string.AppIconNya),
        AYU("AyuIcon", R.drawable.ayu_icon_background,
                R.drawable.ayu_icon_foreground, R.string.AppIconAyu),
        QUACK("QuackIcon", R.drawable.quack_icon_background,
                R.drawable.quack_icon_foreground, R.string.AppIconQuack),
        GO("GoIcon", R.drawable.go_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconGo),
        HAND("HandIcon", R.drawable.hand_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconHand),
        MONO("MonoIcon", R.drawable.mono_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconMono),
        NOTHING("NothingIcon", R.drawable.nothing_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconNothing),
        PLUS("PlusIcon", R.drawable.plus_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconPlus),
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }

    }
}
