package t3zedballistics;

import me.zed_0xff.zombie_buddy.Exposer;
import t3zedballistics.ads.Api;

/**
 * Lua table PVADSJ for the absorbed Project Viewpoint ADS Lua (media/lua/.../PVADS). ZombieBuddy only discovers
 * @LuaClass in package t3zedballistics itself, so this forwards to t3zedballistics.ads.Api. Generated from Api's
 * public static methods; keep the two in step.
 */
@Exposer.LuaClass(name = "PVADSJ")
public final class AdsApi {
    private AdsApi() {}

    public static java.lang.String version() { return Api.version(); }
    public static java.lang.Boolean available() { return Api.available(); }
    public static java.lang.Boolean viewOn() { return Api.viewOn(); }
    public static java.lang.Boolean firstPerson() { return Api.firstPerson(); }
    public static void setWant(java.lang.Object a0) { Api.setWant(a0); }
    public static void setSuspended(java.lang.Object a0) { Api.setSuspended(a0); }
    public static void setClickAim(java.lang.Object a0) { Api.setClickAim(a0); }
    public static void setAimAllowed(java.lang.Object a0, java.lang.Object a1) { Api.setAimAllowed(a0, a1); }
    public static java.lang.Boolean optionsReady() { return Api.optionsReady(); }
    public static se.krka.kahlua.vm.KahluaTable getOptions(se.krka.kahlua.vm.KahluaTable a0) { return Api.getOptions(a0); }
    public static java.lang.Double optionsSignature() { return Api.optionsSignature(); }
    public static java.lang.Boolean aimLocked() { return Api.aimLocked(); }
    public static void clearAimLock() { Api.clearAimLock(); }
    public static java.lang.Boolean canAds() { return Api.canAds(); }
    public static void setDeveloper(java.lang.Object a0) { Api.setDeveloper(a0); }
    public static void setSwayFactor(java.lang.Object a0) { Api.setSwayFactor(a0); }
    public static void setOpticShape(java.lang.Object a0, java.lang.Object a1, java.lang.Object a2, java.lang.Object a3) { Api.setOpticShape(a0, a1, a2, a3); }
    public static void setScopeMag(java.lang.Object a0, java.lang.Object a1) { Api.setScopeMag(a0, a1); }
    public static void setZero(java.lang.Object a0) { Api.setZero(a0); }
    public static void clearProfile() { Api.clearProfile(); }
    public static void setProfile(se.krka.kahlua.vm.KahluaTable a0) { Api.setProfile(a0); }
    public static void setOpticModelProfile(java.lang.Object a0, se.krka.kahlua.vm.KahluaTable a1) { Api.setOpticModelProfile(a0, a1); }
    public static java.lang.String opticKey() { return Api.opticKey(); }
    public static java.lang.String opticLabel() { return Api.opticLabel(); }
    public static java.lang.String opticSource() { return Api.opticSource(); }
    public static java.lang.Boolean isOpticField(java.lang.Object a0) { return Api.isOpticField(a0); }
    public static java.lang.Double effective(java.lang.Object a0) { return Api.effective(a0); }
    public static java.lang.Boolean userOpticSet(java.lang.Object a0, java.lang.Object a1) { return Api.userOpticSet(a0, a1); }
    public static java.lang.Boolean userOpticClear() { return Api.userOpticClear(); }
    public static java.lang.String userOpticUndo() { return Api.userOpticUndo(); }
    public static java.lang.Boolean userOpticSave() { return Api.userOpticSave(); }
    public static java.lang.Boolean userOpticDirty() { return Api.userOpticDirty(); }
    public static se.krka.kahlua.vm.KahluaTable userOpticFields(se.krka.kahlua.vm.KahluaTable a0) { return Api.userOpticFields(a0); }
    public static java.lang.String opticProfileFile() { return Api.opticProfileFile(); }
    public static void setSettings(se.krka.kahlua.vm.KahluaTable a0) { Api.setSettings(a0); }
    public static java.lang.Double viewPitchDeg() { return Api.viewPitchDeg(); }
    public static void setRemoteAim(java.lang.Object a0, java.lang.Object a1, java.lang.Object a2) { Api.setRemoteAim(a0, a1, a2); }
    public static void setSteady(java.lang.Object a0) { Api.setSteady(a0); }
    public static se.krka.kahlua.vm.KahluaTable getSteady(se.krka.kahlua.vm.KahluaTable a0) { return Api.getSteady(a0); }
    public static void setBipod(java.lang.Object a0) { Api.setBipod(a0); }
    public static se.krka.kahlua.vm.KahluaTable getRest(se.krka.kahlua.vm.KahluaTable a0) { return Api.getRest(a0); }
    public static void presenceTick() { Api.presenceTick(); }
    public static se.krka.kahlua.vm.KahluaTable getPresence(se.krka.kahlua.vm.KahluaTable a0) { return Api.getPresence(a0); }
    public static void setPipReticle(java.lang.Object a0, java.lang.Object a1, java.lang.Object a2, java.lang.Object a3, java.lang.Object a4, java.lang.Object a5, java.lang.Object a6, java.lang.Object a7) { Api.setPipReticle(a0, a1, a2, a3, a4, a5, a6, a7); }
    public static se.krka.kahlua.vm.KahluaTable getState(se.krka.kahlua.vm.KahluaTable a0) { return Api.getState(a0); }
    public static java.lang.String probe() { return Api.probe(); }
}
