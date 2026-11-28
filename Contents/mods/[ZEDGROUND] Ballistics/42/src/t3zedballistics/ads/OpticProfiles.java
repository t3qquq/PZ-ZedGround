package t3zedballistics.ads;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import se.krka.kahlua.vm.KahluaTable;
import zombie.ZomboidFileSystem;
import zombie.core.skinnedmodel.model.ModelInstance;

public final class OpticProfiles {
    static final String[] FIELDS = new String[]{
        "adjX",
        "adjY",
        "adjZ",
        "adjPitch",
        "adjYaw",
        "adjRoll",
        "relief",
        "fovMul",
        "opticHeight",
        "lensScale",
        "windowScale",
        "pipX",
        "pipY",
        "pipZ",
        "pipScale",
        "pipZoom",
        "pipRetX",
        "pipRetY",
        "pipDepth",
        "pipCorner",
        "pipAspect",
        "reticleRoll",
        "rearX",
        "rearY",
        "rearZ",
        "frontX",
        "frontY",
        "frontZ"
    };
    static final int N = FIELDS.length;
    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static final Map<String, float[]> SHIPPED;
    private static final Map<String, float[]> USER;
    private static final ArrayDeque<Object[]> UNDO;
    static final int UNDO_MAX = 30;
    private static boolean loaded;
    private static volatile boolean dirty;
    private static volatile int version;
    static volatile String curModel;
    static volatile String curMount;
    static volatile String curWeaponModel;
    static volatile String curKey;
    static volatile String curUserKey;
    static volatile String curSource;
    private static String appliedKey;
    private static int appliedVersion;
    private static int appliedSerial;
    private static final Set<String> logged;
    private static final float[] EFF;
    static File fileOverride;

    static int index(String var0) {
        Integer var1 = INDEX.get(var0);
        return var1 == null ? -1 : var1;
    }

    static boolean isField(String var0) {
        return INDEX.containsKey(var0);
    }

    static float[] blank() {
        float[] var0 = new float[N];
        Arrays.fill(var0, Float.NaN);
        return var0;
    }

    static String norm(String var0) {
        return var0 == null ? "" : var0.trim().toLowerCase(Locale.ROOT);
    }

    static boolean identify(ModelInstance var0, ModelInstance var1) {
        if (var1 != null && var1.modelScript != null) {
            curModel = ADS.shortName(var1.modelScript.getName());
            curMount = norm(var1.attachmentNameParent);
            curWeaponModel = var0 != null && var0.modelScript != null ? ADS.shortName(var0.modelScript.getName()) : "";
            curKey = curModel + "@" + curMount;
            curUserKey = USER.containsKey(curKey + "@" + curWeaponModel) ? curKey + "@" + curWeaponModel : curKey;
            return true;
        } else {
            curUserKey = "";
            curKey = "";
            curMount = "";
            curModel = "";
            return false;
        }
    }

    private static float[] find(Map<String, float[]> var0, boolean var1) {
        float[] var2 = (float[])var0.get(curKey + "@" + curWeaponModel);
        if (var2 == null) {
            var2 = (float[])var0.get(curKey);
        }

        if (var2 == null && var1) {
            var2 = (float[])var0.get(curModel + "@*");
        }

        return var2;
    }

    static synchronized void apply(ModelInstance var0, ModelInstance var1) {
        ADS.Profile var2 = ADS.P;
        if (var2.base != null) {
            ensureLoaded();
            applyCurrent(identify(var0, var1));
        }
    }

    static void identifyNames(String var0, String var1, String var2) {
        curModel = norm(var0);
        curMount = norm(var1);
        curWeaponModel = norm(var2);
        curKey = curModel.isEmpty() ? "" : curModel + "@" + curMount;
        curUserKey = curKey.isEmpty() ? "" : (USER.containsKey(curKey + "@" + curWeaponModel) ? curKey + "@" + curWeaponModel : curKey);
    }

    static synchronized void applyCurrent(boolean var0) {
        ADS.Profile var1 = ADS.P;
        if (var1.base != null) {
            String var2 = var0 ? curKey + "@" + curWeaponModel : "";
            if (!var2.equals(appliedKey) || version != appliedVersion || var1.serial != appliedSerial) {
                appliedKey = var2;
                appliedVersion = version;
                appliedSerial = var1.serial;
                float[] var3 = (float[])var1.base.clone();
                StringBuilder var4 = new StringBuilder();
                boolean var5 = var1.basePipLocked;
                boolean var6 = false;
                if (var0) {
                    boolean var7 = false;

                    for (String var11 : new String[]{curModel + "@*", curKey, curKey + "@" + curWeaponModel}) {
                        float[] var12 = SHIPPED.get(var11);
                        if (var12 != null) {
                            var5 |= overlay(var3, var12);
                            var7 = true;
                            var6 |= !Float.isNaN(var12[8]);
                        }
                    }

                    if (var7) {
                        var4.append("shipped optic");
                    }

                    migrate(var1);
                    var7 = false;

                    for (String var22 : new String[]{curKey, curKey + "@" + curWeaponModel}) {
                        float[] var24 = USER.get(var22);
                        if (var24 != null) {
                            var5 |= overlay(var3, var24);
                            var7 = true;
                            var6 |= !Float.isNaN(var24[8]);
                        }
                    }

                    if (var7) {
                        var4.append(var4.length() > 0 ? " + " : "").append("your optic calibration");
                    }
                }

                if (var1.userWeapon != null) {
                    boolean var14 = false;

                    for (int var16 = 0; var16 < N; var16++) {
                        if (!Float.isNaN(var1.userWeapon[var16])) {
                            var3[var16] = var1.userWeapon[var16];
                            var14 = true;
                            if (var16 >= 11 && var16 <= 20) {
                                var5 = true;
                            }

                            if (var16 == 8) {
                                var6 = true;
                            }
                        }
                    }

                    if (var14) {
                        var4.append(var4.length() > 0 ? " + " : "").append("this gun's own calibration");
                        String var17 = var1.weaponId + "|" + curKey;
                        float[] var19 = var0 ? find(USER, false) : null;
                        boolean var21 = false;
                        if (var19 != null) {
                            for (int var23 = 0; var23 < N; var23++) {
                                if (!Float.isNaN(var1.userWeapon[var23])
                                    && Math.abs(var1.userWeapon[var23] - (Float.isNaN(var19[var23]) ? Float.MAX_VALUE : var19[var23])) > 1.0E-5F) {
                                    var21 = true;
                                }
                            }
                        }

                        if (var21 && logged.add("keep|" + var17)) {
                            Log.info(
                                "optic profile: "
                                    + var1.weaponId
                                    + " keeps its own calibration on "
                                    + label()
                                    + " ("
                                    + fieldsSet(var1.userWeapon)
                                    + "); the other guns use the optic's. Change a value in the calibrator to move it to the optic."
                            );
                        }
                    }
                }

                write(var1, var3);
                var1.pipLocked = var5;
                var1.opticHeightSet = var6;
                curSource = var4.length() == 0 ? (var0 ? "measured / weapon values" : "") : var4.toString();
                if (var0 && logged.add("use|" + var2 + "|" + curSource)) {
                    Log.info("optic profile: " + label() + (curWeaponModel.isEmpty() ? "" : " (gun model " + curWeaponModel + ")") + " -> " + curSource);
                }
            }
        }
    }

    private static boolean overlay(float[] var0, float[] var1) {
        boolean var2 = false;

        for (int var3 = 0; var3 < N; var3++) {
            if (!Float.isNaN(var1[var3])) {
                var0[var3] = var1[var3];
                if (var3 >= 11 && var3 <= 20) {
                    var2 = true;
                }
            }
        }

        return var2;
    }

    static String label() {
        return curModel.isEmpty() ? "" : curModel + " on mount " + (curMount.isEmpty() ? "?" : curMount);
    }

    private static String fieldsSet(float[] var0) {
        StringBuilder var1 = new StringBuilder();

        for (int var2 = 0; var2 < N; var2++) {
            if (!Float.isNaN(var0[var2])) {
                var1.append(var1.length() > 0 ? ", " : "").append(FIELDS[var2]);
            }
        }

        return var1.toString();
    }

    private static void migrate(ADS.Profile var0) {
        if (var0.userWeapon != null && !USER.containsKey(curKey) && !USER.containsKey(curKey + "@" + curWeaponModel)) {
            boolean var1 = false;

            for (float var5 : var0.userWeapon) {
                if (!Float.isNaN(var5)) {
                    var1 = true;
                    break;
                }
            }

            if (var1) {
                USER.put(curKey, (float[])var0.userWeapon.clone());
                curUserKey = curKey;
                version++;
                Log.info(
                    "optic profile: your calibration of "
                        + var0.weaponId
                        + " with this optic was copied to "
                        + label()
                        + " ("
                        + fieldsSet(var0.userWeapon)
                        + ") and applies to every other gun that mounts it; "
                        + var0.weaponId
                        + " keeps its own values until you change one of them in the calibrator"
                );
                save();
            }
        }
    }

    static float[] read(ADS.Profile var0) {
        return readInto(var0, blank());
    }

    static float[] readInto(ADS.Profile var0, float[] var1) {
        Arrays.fill(var1, Float.NaN);
        var1[0] = var0.adjX;
        var1[1] = var0.adjY;
        var1[2] = var0.adjZ;
        var1[3] = var0.adjPitch;
        var1[4] = var0.adjYaw;
        var1[5] = var0.adjRoll;
        var1[6] = var0.relief;
        var1[7] = var0.fovMul;
        var1[8] = var0.opticHeight;
        var1[9] = var0.lensScale;
        var1[10] = var0.windowScale;
        var1[11] = var0.pipX;
        var1[12] = var0.pipY;
        var1[13] = var0.pipZ;
        var1[14] = var0.pipScale;
        var1[15] = var0.pipZoom;
        var1[16] = var0.pipRetX;
        var1[17] = var0.pipRetY;
        var1[18] = var0.pipDepth;
        var1[19] = var0.pipCorner;
        var1[20] = var0.pipAspect;
        var1[21] = var0.reticleRoll;
        if (var0.opticRear != null) {
            var1[22] = var0.opticRear[0];
            var1[23] = var0.opticRear[1];
            var1[24] = var0.opticRear[2];
        }

        if (var0.opticFront != null) {
            var1[25] = var0.opticFront[0];
            var1[26] = var0.opticFront[1];
            var1[27] = var0.opticFront[2];
        }

        return var1;
    }

    private static void write(ADS.Profile var0, float[] var1) {
        var0.adjX = var1[0];
        var0.adjY = var1[1];
        var0.adjZ = var1[2];
        var0.adjPitch = var1[3];
        var0.adjYaw = var1[4];
        var0.adjRoll = var1[5];
        var0.relief = Api.clamp(var1[6], 0.02F, 1.5F);
        var0.fovMul = Api.clamp(var1[7], 0.3F, 1.2F);
        var0.opticHeight = var1[8];
        var0.lensScale = var1[9];
        var0.windowScale = var1[10];
        var0.pipX = Api.clamp(var1[11], -1.0F, 1.0F);
        var0.pipY = Api.clamp(var1[12], -1.0F, 1.0F);
        var0.pipZ = Api.clamp(var1[13], -0.3F, 0.3F);
        var0.pipScale = Api.clamp(var1[14], 0.3F, 2.0F);
        var0.pipZoom = Api.clamp(var1[15], 0.3F, 3.0F);
        var0.pipRetX = Api.clamp(var1[16], -1.0F, 1.0F);
        var0.pipRetY = Api.clamp(var1[17], -1.0F, 1.0F);
        var0.pipDepth = Api.clamp(var1[18], -0.3F, 0.5F);
        var0.pipCorner = Api.clamp(var1[19], -1.0F, 1.0F);
        var0.pipAspect = Api.clamp(var1[20], 0.4F, 2.5F);
        var0.reticleRoll = Float.isNaN(var1[21]) ? 0.0F : Api.clamp(var1[21], -180.0F, 180.0F);
        boolean var2 = !Float.isNaN(var1[22])
            && !Float.isNaN(var1[23])
            && !Float.isNaN(var1[24])
            && !Float.isNaN(var1[25])
            && !Float.isNaN(var1[26])
            && !Float.isNaN(var1[27]);
        var0.opticRear = var2 ? new float[]{var1[22], var1[23], var1[24]} : null;
        var0.opticFront = var2 ? new float[]{var1[25], var1[26], var1[27]} : null;
    }

    static float effective(String var0) {
        int var1 = index(var0);
        if (var1 < 0) {
            return Float.NaN;
        }

        synchronized (EFF) {
            readInto(ADS.P, EFF);
            return EFF[var1];
        }
    }

    static synchronized void setShipped(String var0, KahluaTable var1) {
        if (var0 != null && var1 != null) {
            float[] var2 = blank();

            for (int var3 = 0; var3 < N; var3++) {
                Object var4 = var1.rawget(FIELDS[var3]);
                if (var4 instanceof Number) {
                    var2[var3] = ((Number)var4).floatValue();
                }
            }

            Object var8 = var1.rawget("rear");
            Object var9 = var1.rawget("front");
            if (var8 instanceof KahluaTable && var9 instanceof KahluaTable) {
                for (int var5 = 0; var5 < 3; var5++) {
                    Object var6 = ((KahluaTable)var8).rawget(var5 + 1);
                    Object var7 = ((KahluaTable)var9).rawget(var5 + 1);
                    if (var6 instanceof Number) {
                        var2[22 + var5] = ((Number)var6).floatValue();
                    }

                    if (var7 instanceof Number) {
                        var2[25 + var5] = ((Number)var7).floatValue();
                    }
                }
            }

            SHIPPED.put(norm(var0), var2);
            version++;
        }
    }

    static synchronized void setShippedArray(String var0, float[] var1) {
        SHIPPED.put(norm(var0), (float[])var1.clone());
        version++;
    }

    static synchronized float userValue(String var0) {
        int var1 = index(var0);
        float[] var2 = curUserKey.isEmpty() ? null : USER.get(curUserKey);
        return var1 >= 0 && var2 != null ? var2[var1] : Float.NaN;
    }

    static synchronized boolean userSet(String var0, float var1) {
        ensureLoaded();
        int var2 = index(var0);
        if (var2 >= 0 && !curKey.isEmpty() && Float.isFinite(var1)) {
            String var3 = curUserKey.isEmpty() ? curKey : curUserKey;
            float[] var4 = USER.get(var3);
            pushUndo(var3, var4);
            if (var4 == null) {
                var4 = blank();
                USER.put(var3, var4);
            }

            var4[var2] = var1;
            curUserKey = var3;
            ADS.Profile var5 = ADS.P;
            if (var5.userWeapon != null) {
                var5.userWeapon[var2] = Float.NaN;
            }

            dirty = true;
            version++;
            return true;
        } else {
            return false;
        }
    }

    static synchronized boolean userClear() {
        ensureLoaded();
        if (curKey.isEmpty()) {
            return false;
        }

        String var0 = curUserKey.isEmpty() ? curKey : curUserKey;
        float[] var1 = USER.get(var0);
        if (var1 == null) {
            return false;
        }

        pushUndo(var0, var1);
        USER.remove(var0);
        dirty = true;
        version++;
        return true;
    }

    static synchronized String undo() {
        Object[] var0 = UNDO.pollFirst();
        if (var0 == null) {
            return "";
        }

        String var1 = (String)var0[0];
        float[] var2 = (float[])var0[1];
        if (var2 == null) {
            USER.remove(var1);
        } else {
            USER.put(var1, var2);
        }

        dirty = true;
        version++;
        return var1;
    }

    private static void pushUndo(String var0, float[] var1) {
        UNDO.addFirst(new Object[]{var0, var1 == null ? null : var1.clone()});

        while (UNDO.size() > 30) {
            UNDO.pollLast();
        }
    }

    static synchronized void fillUser(KahluaTable var0) {
        float[] var1 = curUserKey.isEmpty() ? null : USER.get(curUserKey);
        if (var1 != null && var0 != null) {
            for (int var2 = 0; var2 < N; var2++) {
                if (!Float.isNaN(var1[var2])) {
                    var0.rawset(FIELDS[var2], (double)var1[var2]);
                }
            }
        }
    }

    static File file() {
        return fileOverride != null
            ? fileOverride
            : new File(ZomboidFileSystem.instance.getCacheDir() + File.separator + "Lua" + File.separator + "PVADS_OpticProfiles.txt");
    }

    static synchronized void forget() {
        curUserKey = "";
        curKey = "";
        curWeaponModel = "";
        curMount = "";
        curModel = "";
        curSource = "";
        appliedKey = "\u0000";
    }

    static synchronized void resetForTest() {
        loaded = false;
        USER.clear();
        SHIPPED.clear();
        UNDO.clear();
        appliedKey = "\u0000";
        version++;
    }

    static synchronized void ensureLoaded() {
        if (!loaded) {
            loaded = true;

            try {
                File var0 = file();
                if (!var0.isFile()) {
                    return;
                }

                int var1 = 0;

                String var3;
                try (BufferedReader var2 = Files.newBufferedReader(var0.toPath(), StandardCharsets.UTF_8)) {
                    while ((var3 = var2.readLine()) != null) {
                        var3 = var3.trim();
                        if (!var3.isEmpty() && !var3.startsWith("#")) {
                            int var4 = var3.indexOf("=>");
                            if (var4 > 0) {
                                float[] var5 = blank();

                                for (String var9 : var3.substring(var4 + 2).split(";")) {
                                    int var10 = var9.indexOf(61);
                                    if (var10 > 0) {
                                        int var11 = index(var9.substring(0, var10).trim());
                                        if (var11 >= 0) {
                                            try {
                                                var5[var11] = Float.parseFloat(var9.substring(var10 + 1).trim());
                                            } catch (NumberFormatException var14) {
                                            }
                                        }
                                    }
                                }

                                USER.put(norm(var3.substring(0, var4)), var5);
                                var1++;
                            }
                        }
                    }
                }

                version++;
                Log.info("optic profiles: " + var1 + " of your optic calibrations loaded from " + var0);
            } catch (Throwable var16) {
                Log.error("optic profiles: reading your optic calibrations", var16);
            }
        }
    }

    static boolean dirty() {
        return dirty;
    }

    static synchronized boolean save() {
        try {
            File var0 = file();
            File var1 = var0.getParentFile();
            if (var1 != null && !var1.isDirectory() && !var1.mkdirs()) {
                throw new IOException("cannot create " + var1);
            }

            Path var2 = var0.toPath();
            Path var3 = new File(var0.getPath() + ".tmp").toPath();

            try (BufferedWriter var4 = Files.newBufferedWriter(var3, StandardCharsets.UTF_8)) {
                var4.write("# Project Viewpoint ADS - your optic calibrations (one optic model + mount per line; applies to every gun that mounts it)\r\n");
                var4.write("# Format: opticmodel@mount[@gunmodel]=>field=value;...\r\n");

                for (Entry var6 : USER.entrySet()) {
                    StringBuilder var7 = new StringBuilder((String)var6.getKey()).append("=>");
                    float[] var8 = (float[])var6.getValue();

                    for (int var9 = 0; var9 < N; var9++) {
                        if (!Float.isNaN(var8[var9])) {
                            var7.append(FIELDS[var9]).append('=').append(String.format(Locale.ROOT, "%.5f", var8[var9])).append(';');
                        }
                    }

                    var4.write(var7.append("\r\n").toString());
                }
            }

            if (var0.isFile()) {
                for (int var14 = 3; var14 > 1; var14--) {
                    File var15 = new File(var0.getPath() + ".bak" + (var14 - 1));
                    if (var15.isFile()) {
                        Files.move(var15.toPath(), new File(var0.getPath() + ".bak" + var14).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                Files.copy(var2, new File(var0.getPath() + ".bak1").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(var3, var2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var11) {
                Files.move(var3, var2, StandardCopyOption.REPLACE_EXISTING);
            }

            dirty = false;
            return true;
        } catch (Throwable var13) {
            Log.error("optic profiles: saving your optic calibrations", var13);
            return false;
        }
    }

    private OpticProfiles() {
    }

    static {
        for (int var0 = 0; var0 < N; var0++) {
            INDEX.put(FIELDS[var0], var0);
        }

        SHIPPED = new HashMap<>();
        USER = new LinkedHashMap<>();
        UNDO = new ArrayDeque<>();
        curModel = "";
        curMount = "";
        curWeaponModel = "";
        curKey = "";
        curUserKey = "";
        curSource = "";
        appliedKey = "\u0000";
        appliedVersion = -1;
        appliedSerial = -1;
        logged = new HashSet<>();
        EFF = new float[N];
    }
}
