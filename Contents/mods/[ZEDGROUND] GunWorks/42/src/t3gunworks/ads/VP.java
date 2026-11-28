package t3gunworks.ads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VP {
    static final float SQRT6 = 2.4494896F;
    static boolean ok;
    static Field lookYaw;
    static Field lookPitch;
    static Field viewEnabled;
    static Field thirdPersonActive;
    static Field freeCamActive;
    static Field frameCamX;
    static Field frameCamY;
    static Field frameCamZ;
    static Object eyeMotion;
    static Field emOffX;
    static Field emOffZ;
    static Field emHeight;
    static Field emLean;
    static Field amLeanX;
    static Field amLeanY;
    static Field amLeanDrop;
    static Field amDip;
    static Field amRoll;
    static boolean amTried;
    static boolean amWritesEye;
    private static boolean reliefTried;
    private static Method reliefCurrent;
    private static Method liftScene;
    static Field lookCaptured;
    static Method tpFlip;
    static Field tpFlipping;
    private static volatile int flipTo = -1;
    private static volatile long flipNs;
    static final long FLIP_WAIT_NS = 500000000L;
    private static Object gunplayToggle;
    private static Method gunplayGet;
    private static boolean gunplayTried;

    public static void init() {
        try {
            Class var0 = cls("viewpoint.input.Look");
            lookYaw = var0.getField("yaw");
            lookPitch = var0.getField("pitch");
            lookCaptured = optional(var0, "captured");
            viewEnabled = optional(cls("viewpoint.core.View"), "enabled");
            thirdPersonActive = optional(cls("viewpoint.core.View"), "thirdPerson");
            if (thirdPersonActive != null) {
                try {
                    Class var1 = cls("viewpoint.camera.ThirdPerson");
                    tpFlip = var1.getMethod("flip");
                    tpFlipping = acc(var1.getDeclaredField("flipping"));
                } catch (Throwable var9) {
                    tpFlip = null;
                    tpFlipping = null;
                    Log.warn("Viewpoint third-person switch not found (" + var9 + "); ADS from third person stays in third person");
                }
            } else {
                try {
                    thirdPersonActive = optional(cls("viewpoint.input.ThirdPerson"), "active");
                } catch (Throwable var8) {
                    thirdPersonActive = null;
                }
            }

            try {
                freeCamActive = optional(cls("viewpoint.input.FreeCam"), "active");
            } catch (Throwable var7) {
                freeCamActive = null;
            }

            Class var11 = cls("viewpoint.core.Frame");
            frameCamX = var11.getField("camX");
            frameCamY = var11.getField("camY");
            frameCamZ = var11.getField("camZ");
            ok = true;

            try {
                Field var2;
                try {
                    var2 = cls("viewpoint.input.Eye").getDeclaredField("eyeMotion");
                } catch (Throwable var5) {
                    var2 = cls("viewpoint.input.Controls").getDeclaredField("eyeMotion");
                }

                var2.setAccessible(true);
                Object var3 = var2.get(null);
                Class var4 = var3.getClass();
                emOffX = acc(var4.getDeclaredField("offsetX"));
                emOffZ = acc(var4.getDeclaredField("offsetZ"));
                emHeight = acc(var4.getDeclaredField("height"));
                emLean = acc(var4.getDeclaredField("lean"));
                eyeMotion = var3;
            } catch (Throwable var6) {
                eyeMotion = null;
                Log.warn("Viewpoint eye state not readable (" + var6 + "); using the head-bone eye");
            }

            Log.info("Viewpoint camera bridge ready" + (eyeMotion != null ? " (eye from Viewpoint)" : ""));
        } catch (Throwable var10) {
            ok = false;
            Log.warn("Viewpoint not found or incompatible (" + var10 + "); ADS stays disabled");
        }
    }

    static Class<?> cls(String var0) throws ClassNotFoundException {
        try {
            return Class.forName(var0, false, VP.class.getClassLoader());
        } catch (ClassNotFoundException var2) {
            return Class.forName(var0, false, Thread.currentThread().getContextClassLoader());
        }
    }

    private static Field acc(Field var0) {
        var0.setAccessible(true);
        return var0;
    }

    static Object relief() {
        if (!reliefTried) {
            reliefTried = true;

            try {
                reliefCurrent = cls("viewpoint.far.Relief").getMethod("current");
                liftScene = cls("viewpoint.render.FarRelief$Lift").getMethod("scene", double.class, double.class);
                Log.info("Viewpoint terrain relief found: sights are kept on the eye on slopes");
            } catch (Throwable var2) {
                reliefCurrent = null;
                Log.info("Viewpoint terrain relief not present (" + var2 + ")");
            }
        }

        if (reliefCurrent == null) {
            return null;
        }

        try {
            return reliefCurrent.invoke(null);
        } catch (Throwable var1) {
            reliefCurrent = null;
            Log.error("relief", var1);
            return null;
        }
    }

    static float reliefScene(Object var0, double var1, double var3) {
        try {
            return (Float)liftScene.invoke(var0, var1, var3);
        } catch (Throwable var6) {
            reliefCurrent = null;
            Log.error("relief lift", var6);
            return Float.NaN;
        }
    }

    static boolean movementOffsets(float[] var0) {
        return movementOffsets(var0, !amWritesEye);
    }

    static boolean movementOffsets(float[] var0, boolean var1) {
        var0[0] = var0[1] = var0[2] = var0[3] = 0.0F;
        if (!amTried) {
            amTried = true;

            try {
                Class var2 = cls("com.viewpointmovement.core.CameraBridge");
                amLeanX = acc(var2.getDeclaredField("leanX"));
                amLeanY = acc(var2.getDeclaredField("leanY"));
                amLeanDrop = acc(var2.getDeclaredField("leanDrop"));
                amDip = acc(var2.getDeclaredField("dip"));
                amRoll = acc(var2.getDeclaredField("roll"));

                try {
                    var2.getDeclaredField("eyeMotion");
                    amWritesEye = true;
                } catch (NoSuchFieldException var5) {
                    amWritesEye = false;
                }

                Log.info(
                    "Viewpoint Advanced Movement found: ADS follows its lean"
                        + (amWritesEye ? " (lean already in Viewpoint's eye; roll taken from it)" : " (lean added to the eye here)")
                );
            } catch (ClassNotFoundException var6) {
                amLeanX = null;
            } catch (Throwable var7) {
                amLeanX = null;
                Log.warn("Viewpoint Advanced Movement found but its lean is not readable (" + var7 + ")");
            }
        }

        if (amLeanX == null) {
            return false;
        }

        try {
            if (var1) {
                var0[0] = amLeanX.getFloat(null);
                var0[1] = amLeanY.getFloat(null);
                var0[2] = amLeanDrop.getFloat(null) + amDip.getFloat(null);
            }

            var0[3] = amRoll.getFloat(null);
            return true;
        } catch (Throwable var4) {
            amLeanX = null;
            Log.error("movement offsets", var4);
            return false;
        }
    }

    static boolean eyeState(float[] var0) {
        if (eyeMotion == null) {
            return false;
        }

        try {
            var0[0] = emOffX.getFloat(eyeMotion);
            var0[1] = emOffZ.getFloat(eyeMotion);
            var0[2] = emHeight.getFloat(eyeMotion);
            var0[3] = emLean.getFloat(eyeMotion);
            return Float.isFinite(var0[0]) && Float.isFinite(var0[2]) && var0[2] > 0.2F;
        } catch (Throwable var2) {
            eyeMotion = null;
            Log.error("eye state", var2);
            return false;
        }
    }

    private static Field optional(Class<?> var0, String var1) {
        try {
            return var0.getField(var1);
        } catch (Throwable var3) {
            return null;
        }
    }

    static boolean mouseCaptured() {
        try {
            return lookCaptured == null || lookCaptured.getBoolean(null);
        } catch (Throwable var1) {
            return true;
        }
    }

    static float yaw() {
        try {
            return lookYaw.getFloat(null);
        } catch (Throwable var1) {
            return 0.0F;
        }
    }

    static float pitch() {
        try {
            return lookPitch.getFloat(null);
        } catch (Throwable var1) {
            return 0.0F;
        }
    }

    static void setYaw(float var0) {
        try {
            lookYaw.setFloat(null, var0);
        } catch (Throwable var2) {
            Log.error("setYaw", var2);
        }
    }

    static void setPitch(float var0) {
        try {
            lookPitch.setFloat(null, var0);
        } catch (Throwable var2) {
            Log.error("setPitch", var2);
        }
    }

    static boolean firstPerson() {
        if (!ok) {
            return false;
        }

        try {
            return viewEnabled != null && !viewEnabled.getBoolean(null) ? false : thirdPersonActive == null || !thirdPersonActive.getBoolean(null);
        } catch (Throwable var1) {
            return false;
        }
    }

    static boolean viewOn() {
        if (!ok) {
            return false;
        }

        try {
            return viewEnabled == null || viewEnabled.getBoolean(null);
        } catch (Throwable var1) {
            return false;
        }
    }

    static boolean freeCam() {
        try {
            return freeCamActive != null && freeCamActive.getBoolean(null);
        } catch (Throwable var1) {
            return false;
        }
    }

    static boolean thirdPerson() {
        try {
            if (thirdPersonActive == null) {
                return false;
            }

            boolean var0 = thirdPersonActive.getBoolean(null);
            int var1 = flipTo;
            if (var1 >= 0) {
                if (var0 != (var1 == 1) && System.nanoTime() - flipNs <= 500000000L) {
                    return var1 == 1;
                }

                flipTo = -1;
            }

            return var0;
        } catch (Throwable var2) {
            return false;
        }
    }

    static boolean setThirdPerson(boolean var0) {
        if (thirdPersonActive == null) {
            return false;
        }

        try {
            if (tpFlip != null) {
                if (thirdPerson() == var0) {
                    return true;
                }

                if (tpFlipping != null && tpFlipping.getBoolean(null)) {
                    return true;
                }

                flipTo = var0 ? 1 : 0;
                flipNs = System.nanoTime();
                tpFlip.invoke(null);
                return true;
            } else {
                thirdPersonActive.setBoolean(null, var0);
                return true;
            }
        } catch (Throwable var2) {
            Log.error("setThirdPerson", var2);
            return false;
        }
    }

    static boolean gunplayOn() {
        if (!gunplayTried) {
            gunplayTried = true;

            try {
                Field var0 = acc(cls("viewpoint.gunplay.Gunplay").getDeclaredField("ON"));
                Object var1 = var0.get(null);
                Method var2 = var1.getClass().getMethod("get");
                var2.setAccessible(true);
                gunplayGet = var2;
                gunplayToggle = var1;
            } catch (ClassNotFoundException var4) {
                gunplayToggle = null;
            } catch (Throwable var5) {
                gunplayToggle = null;
                Log.warn("Viewpoint gunplay setting not readable (" + var5 + ")");
            }
        }

        if (gunplayToggle == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(gunplayGet.invoke(gunplayToggle));
        } catch (Throwable var3) {
            gunplayToggle = null;
            return false;
        }
    }

    static float camX(Object var0) {
        try {
            return frameCamX.getFloat(var0);
        } catch (Throwable var2) {
            return Float.NaN;
        }
    }

    static float camY(Object var0) {
        try {
            return frameCamY.getFloat(var0);
        } catch (Throwable var2) {
            return Float.NaN;
        }
    }

    static float camZ(Object var0) {
        try {
            return frameCamZ.getFloat(var0);
        } catch (Throwable var2) {
            return Float.NaN;
        }
    }

    private VP() {
    }
}
