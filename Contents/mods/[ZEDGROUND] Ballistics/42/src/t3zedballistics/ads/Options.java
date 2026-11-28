package t3zedballistics.ads;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;

public final class Options {
    static final String TAB = "Recoil & ADS";
    static final String PREFIX = "pvads.";
    // ZEDGROUND: options that change where shots land are server sandbox options (Gunplay.enforce), not listed here.
    private static final int NUM = 0;
    private static final int TOGGLE = 1;
    private static final int CHOICE = 2;
    private static final int KEY = 3;
    private static final List<Options.Entry> ALL = new ArrayList<>();
    static volatile boolean ready;
    private static boolean tried;
    private static Method mNumber;
    private static Method mToggle;
    private static Method mChoice;
    private static Method mDescribe;
    private static Method mShownAs;
    private static Method mNumGet;
    private static Method mTogGet;
    private static Method mChoGet;
    private static Method mDeclare;
    private static Method mChords;
    private static Method mChordKey;
    private static Method mKeyName;
    private static Object kindPress;
    private static Object kindHold;

    static synchronized boolean init() {
        if (tried) {
            return ready;
        }

        tried = true;

        try {
            Class var0 = VP.cls("viewpoint.platform.LiveSettings");
            Class var1 = VP.cls("viewpoint.platform.LiveSettings$Setting");
            Class var2 = VP.cls("viewpoint.platform.LiveSettings$Number");
            Class var3 = VP.cls("viewpoint.platform.LiveSettings$Toggle");
            Class var4 = VP.cls("viewpoint.platform.LiveSettings$Choice");
            mNumber = var0.getMethod("number", String.class, String.class, String.class, float.class, float.class, float.class, float.class);
            mToggle = var0.getMethod("toggle", String.class, String.class, String.class, boolean.class);
            mChoice = var0.getMethod("choice", String.class, String.class, String.class, String[].class, int.class);
            mDescribe = var1.getMethod("describe", String.class);
            mShownAs = var4.getMethod("shownAs", String[].class);
            mNumGet = var2.getMethod("get");
            mTogGet = var3.getMethod("get");
            mChoGet = var4.getMethod("get");

            try {
                Class var5 = VP.cls("viewpoint.platform.Keys");
                Class var6 = VP.cls("viewpoint.platform.KeyBind");
                Class var7 = VP.cls("viewpoint.platform.KeyBind$Kind");
                Class var8 = VP.cls("viewpoint.platform.KeyChord");
                mDeclare = var5.getDeclaredMethod("declare", String.class, String.class, String.class, var7, boolean.class, String.class, String.class);
                mDeclare.setAccessible(true);
                mChords = var6.getMethod("chords");
                mChordKey = var8.getMethod("key");
                mKeyName = var8.getDeclaredMethod("name", int.class);
                mKeyName.setAccessible(true);
                kindPress = enumValue(var7, "PRESS");
                kindHold = enumValue(var7, "HOLD");
            } catch (Throwable var9) {
                mDeclare = null;
            }

            declareAll();
            ready = true;
            carryOver();
            Log.info("options: " + ALL.size() + " settings on the 'Recoil & ADS' page of Viewpoint's settings window");
        } catch (Throwable var10) {
            ready = false;
            Log.warn("options: Viewpoint's settings window not available (" + var10 + "); using Settings > Mods instead");
        }

        return ready;
    }

    private static Object enumValue(Class<?> var0, String var1) {
        return Enum.valueOf((Class) var0, var1);
    }

    private static String sec(String var0) {
        return "Recoil & ADS/" + var0;
    }

    private static void num(String var0, String var1, String var2, float var3, float var4, float var5, float var6, String var7) throws Exception {
        Object var8 = mNumber.invoke(null, "pvads." + var0, var1, sec(var2), var3, var4, var5, var6);
        if (var7 != null) {
            mDescribe.invoke(var8, var7);
        }

        ALL.add(new Options.Entry(var0, 0, var8));
    }

    private static void tog(String var0, String var1, String var2, boolean var3, String var4) throws Exception {
        Object var5 = mToggle.invoke(null, "pvads." + var0, var1, sec(var2), var3);
        if (var4 != null) {
            mDescribe.invoke(var5, var4);
        }

        ALL.add(new Options.Entry(var0, 1, var5));
    }

    private static void cho(String var0, String var1, String var2, String[] var3, String[] var4, int var5, String var6) throws Exception {
        Object var7 = mChoice.invoke(null, "pvads." + var0, var1, sec(var2), var3, var5 - 1);
        if (var4 != null) {
            mShownAs.invoke(var7, var4);
        }

        if (var6 != null) {
            mDescribe.invoke(var7, var6);
        }

        ALL.add(new Options.Entry(var0, 2, var7));
    }

    private static void key(String var0, String var1, int var2, boolean var3, String var4) throws Exception {
        String var5 = var2 <= 0 ? "NONE" : (String)mKeyName.invoke(null, var2);
        Object var6 = mDeclare.invoke(null, "pvads." + var0, var1, "Keys/Recoil & ADS", var3 ? kindHold : kindPress, false, var5, var4 != null ? var4 : "");
        ALL.add(new Options.Entry(var0, 3, var6));
    }

    private static void declareAll() throws Exception {
        String var0 = "Aiming";
        String var1 = "Handling";
        String var2 = "Weapon presence";
        String var3 = "Optics";
        String var4 = "Scope zoom and zeroing";
        String var5 = "Developer";
        cho(
            "adsControl",
            "ADS control",
            var0,
            new String[]{"aim", "holdKey", "toggleKey", "dayz"},
            new String[]{
                "Aim (right mouse) raises the sights",
                "Hold the ADS key while aiming",
                "Hip fire; ADS key toggles sights",
                "DayZ: click right mouse = sights, hold = hip fire"
            },
            4,
            "How sights are raised with a firearm. DayZ style: click the aim button (right mouse) to bring the sights up and keep them there, click again to lower them; hold it for ordinary hip aiming. Leave the game's own 'Toggle aim' option off with it."
        );
        tog(
            "aimPriority",
            "Aiming takes priority over menus",
            var0,
            true,
            "While you hold a firearm, the ADS key does not open the vehicle / animal radial menu that shares it (V), and Viewpoint Interface's interaction prompt and action menu step aside while you aim. Lower the gun or holster it to use them."
        );
        num("adsSens", "ADS mouse sensitivity", var0, 0.3F, 1.5F, 0.05F, 0.85F, null);
        num("scopeSens", "Magnified scope sensitivity", var0, 0.3F, 2.0F, 0.05F, 1.0F, "1.0 keeps the same feel per degree of view at every magnification.");
        tog(
            "closeClip",
            "Stop the gun clipping open near the camera while aiming",
            var0,
            true,
            "Moves the camera's near clip plane from 5 cm to about 1 cm while sights are up, so the receiver and stock right under your eye are drawn instead of being cut open."
        );
        tog(
            "adsSharp",
            "No depth-of-field blur while aiming down sights",
            var0,
            true,
            "Viewpoint shader packs with depth of field focus on the screen centre, which is your sights when ADS, so the world blurs. This keeps the view sharp while sights are up."
        );
        num(
            "ironDist",
            "Iron sight / red dot distance (long guns)",
            var0,
            0.8F,
            1.6F,
            0.05F,
            1.0F,
            "How far in front of your eye the gun sits when aiming down sights. Higher = smaller front sight, more of the target visible and less of your hands near the camera, but past about 1.3 the arms stretch to reach. Pistols are not affected. Does not change where you hit."
        );
        tog(
            "bipodAuto",
            "Deploy bipods automatically",
            var1,
            true,
            "Guns with a bipod (Guns of Marz, Gael's Gun Store, VFE M60) put it down by the gun pack's own action when you go prone or rest the gun on something, and fold it when you walk off. The Bipod key in Options > Keybinds toggles it by hand."
        );
        tog(
            "presenceOthers",
            "Other players' guns collide too",
            var2,
            true,
            "Other players' guns also give way at walls, doors and furniture on your screen. Worked out on your own game from what it already knows about them (no network traffic, nothing needed on the server); players who run this mod see yours the same way. Up to 12 nearest aiming players within 40 tiles."
        );
        num("presenceStart", "Collision start distance (cm)", var2, 0.0F, 20.0F, 1.0F, 8.0F, "How far in front of an obstacle the muzzle starts giving way.");
        num("presenceStrength", "Compressed pose strength", var2, 0.5F, 1.5F, 0.05F, 1.0F, "How far the gun pulls back and rises when it meets something.");
        num(
            "presenceSmoothing",
            "Collision smoothing",
            var2,
            0.5F,
            2.0F,
            0.05F,
            1.0F,
            "Lower = snappier, higher = softer movement into and out of the compressed pose."
        );
        tog(
            "lowReady",
            "Low ready when not aiming (first person)",
            var2,
            false,
            "Lowers the muzzle a little while you hold a firearm without aiming. Off by default."
        );
        num("lowReadyAmount", "Low ready amount", var2, 0.1F, 1.0F, 0.05F, 0.5F, null);
        num("lensSize", "Scope lens size on screen", var3, 0.5F, 1.1F, 0.02F, 0.84F, null);
        num("brightness", "Reticle brightness", var3, 0.2F, 1.5F, 0.05F, 1.0F, null);
        cho(
            "color",
            "Illuminated reticle colour",
            var3,
            new String[]{"red", "green", "amber", "black"},
            new String[]{"Red", "Green", "Amber", "Black"},
            1,
            null
        );
        tog(
            "pipScopes",
            "Picture-in-picture scopes and sights",
            var3,
            true,
            "Scopes show their magnified view inside the scope's glass, and red dots / holo sights show the world clearly through their window with the lit reticle, while the rest of the screen, the gun and your hands stay normal. Renders the world a second time while aiming through an optic, so it costs some frame rate. Off: magnified scopes magnify the whole first-person view behind a lens mask. If it ever fails it switches itself off and the classic view is used."
        );
        cho(
            "pipQuality",
            "Picture-in-picture quality",
            var3,
            new String[]{"high", "mid", "low"},
            new String[]{"High", "Mid (default)", "Low"},
            2,
            "How much work the view inside scopes and sights costs. All levels only draw the part of the world the lens shows. High: everything, every frame (rain and object outlines in the lens too). Mid: no rain particles or outlines inside the lens. Low: like Mid, but the lens picture is redrawn every other frame (can lag a little when turning fast)."
        );
        tog(
            "variableZoom",
            "Variable zoom on high-power scopes",
            var4,
            true,
            "Scopes of 4x and up zoom in steps: an 8x goes 2x / 4x / 8x, a 6x 1.5x / 3x / 6x, a 4x 2x / 4x. Optics registered with their own zoom levels always use them."
        );
        tog("wheelZoom", "Mouse wheel changes scope zoom", var4, true, "While looking through a variable scope: wheel up zooms in, wheel down zooms out.");
        tog(
            "scopeHud",
            "Show zoom and zero when changed",
            var4,
            true,
            "A small readout in the lower right corner for a moment after you change the zoom or the zero."
        );
        tog("developer", "Developer / calibration mode", var5, false, "Enables the calibration window, extra logging and the validator.");
        tog(
            "presenceDebug",
            "Debug weapon collision (needs developer mode)",
            var5,
            false,
            "Shows the weapon's measured length, its rear, pivot and muzzle, the probe hit, the obstruction amount and the pose on screen, and logs state changes."
        );
    }

    private static void carryOver() {
        try {
            File var0 = new File(String.valueOf(LuaManager.getLuaCacheDir()), "ModOptions.ini");
            if (!var0.isFile()) {
                return;
            }

            String var1 = new String(Files.readAllBytes(var0.toPath()), StandardCharsets.UTF_8);
            Matcher var2 = Pattern.compile("(tickbox|slider|combobox|keybind)\\|ProjectViewpointADS\\|([A-Za-z0-9_]+)\\|(true|false|-?[0-9]+(?:\\.[0-9]+)?)")
                .matcher(var1);
            LinkedHashMap var3 = new LinkedHashMap();

            while (var2.find()) {
                var3.put(var2.group(2), var2.group(3));
            }

            if (var3.isEmpty()) {
                return;
            }

            Method var4 = VP.cls("viewpoint.platform.LiveSettings").getDeclaredMethod("carryOver", String.class, String.class);
            var4.setAccessible(true);
            Class var5 = VP.cls("viewpoint.platform.LiveSettings$Choice");
            Field var6 = var5.getDeclaredField("options");
            var6.setAccessible(true);
            int var7 = 0;

            for (Options.Entry var9 : ALL) {
                String var10 = (String)var3.get(var9.id);
                if (var10 != null) {
                    String var11;
                    if (var9.type == 2) {
                        String[] var12 = (String[])var6.get(var9.setting);
                        int var13 = (int)Math.round(Double.parseDouble(var10)) - 1;
                        if (var13 < 0 || var13 >= var12.length) {
                            continue;
                        }

                        var11 = var12[var13];
                    } else if (var9.type == 3) {
                        int var15 = (int)Math.round(Double.parseDouble(var10));
                        var11 = var15 <= 0 ? "" : (String)mKeyName.invoke(null, var15);
                    } else {
                        var11 = var10;
                    }

                    var4.invoke(null, "pvads." + var9.id, var11);
                    var7++;
                }
            }

            Log.info("options: " + var7 + " values carried over from the old Settings > Mods page (" + var0 + ")");
        } catch (Throwable var14) {
            Log.warn("options: old Settings > Mods values not carried over (" + var14 + ")");
        }
    }

    static void fill(KahluaTable var0) {
        if (ready && var0 != null) {
            for (Options.Entry var2 : ALL) {
                try {
                    var0.rawset(var2.id, value(var2));
                } catch (Throwable var4) {
                }
            }
        }
    }

    private static Object value(Options.Entry var0) throws Exception {
        switch (var0.type) {
            case 0:
                return ((Number)mNumGet.invoke(var0.setting)).doubleValue();
            case 1:
                return mTogGet.invoke(var0.setting);
            case 2:
                return (double)(((Number)mChoGet.invoke(var0.setting)).intValue() + 1);
            default:
                List var1 = (List)mChords.invoke(var0.setting);
                return var1 != null && !var1.isEmpty() ? (double)((Number)mChordKey.invoke(var1.get(0))).intValue() : 0.0;
        }
    }

    static double signature() {
        if (!ready) {
            return 0.0;
        }

        long var0 = 1125899906842597L;

        for (Options.Entry var3 : ALL) {
            try {
                var0 = 31L * var0 + String.valueOf(value(var3)).hashCode();
            } catch (Throwable var5) {
            }
        }

        return var0 & 281474976710655L;
    }

    private Options() {
    }

    private static final class Entry {
        final String id;
        final int type;
        final Object setting;

        Entry(String var1, int var2, Object var3) {
            this.id = var1;
            this.type = var2;
            this.setting = var3;
        }
    }
}
