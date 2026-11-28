package t3gunworks.ads;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelInstanceRenderDataList;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

public final class SkipRefused {
    private static boolean tried;
    private static boolean broken;
    private static Method refused;
    private static final ArrayList<Object> held = new ArrayList<>();
    private static final ArrayList<Integer> at = new ArrayList<>();
    private static List<ModelInstanceRenderData> heldFrom;
    private static final Set<String> logged = new HashSet<>();

    public static void enter(Object var0) {
        if (!broken) {
            try {
                restore();
                if (!(var0 instanceof ModelSlotRenderData)) {
                    return;
                }

                if (!tried) {
                    tried = true;
                    refused = VP.cls("viewpoint.render.ModelDraws").getMethod("refused", ModelMesh.class);
                }

                if (refused == null) {
                    return;
                }

                ModelInstanceRenderDataList var1 = ((ModelSlotRenderData)var0).modelData;
                if (var1 == null || var1.size() < 2) {
                    return;
                }

                int var2 = 0;

                for (int var3 = 0; var3 < var1.size(); var3++) {
                    if (!isRefused((ModelInstanceRenderData)var1.get(var3))) {
                        var2++;
                    }
                }

                if (var2 == 0 || var2 == var1.size()) {
                    return;
                }

                for (int var7 = var1.size() - 1; var7 >= 0; var7--) {
                    ModelInstanceRenderData var4 = (ModelInstanceRenderData)var1.get(var7);
                    if (isRefused(var4)) {
                        held.add(0, var4);
                        at.add(0, var7);
                        var1.remove(var7);
                        String var5 = var4.modelInstance != null && var4.modelInstance.modelScript != null
                            ? var4.modelInstance.modelScript.getName()
                            : String.valueOf(var4.model);
                        if (logged.add(var5)) {
                            Log.info(
                                "model " + var5 + " cannot be drawn by Viewpoint (mesh without texture coordinates): left out so the character is still drawn"
                            );
                        }
                    }
                }

                heldFrom = var1;
            } catch (Throwable var6) {
                broken = true;
                Log.error("skip undrawable models", var6);
            }
        }
    }

    public static void exit() {
        if (!broken) {
            try {
                restore();
            } catch (Throwable var1) {
                broken = true;
                Log.error("skip undrawable models (restore)", var1);
            }
        }
    }

    private static void restore() {
        if (heldFrom != null) {
            for (int var0 = 0; var0 < held.size(); var0++) {
                heldFrom.add(Math.min(at.get(var0), heldFrom.size()), (ModelInstanceRenderData)held.get(var0));
            }

            held.clear();
            at.clear();
            heldFrom = null;
        }
    }

    private static boolean isRefused(ModelInstanceRenderData var0) throws Exception {
        return var0 != null && var0.model != null && var0.model.mesh != null && (Boolean)refused.invoke(null, var0.model.mesh);
    }
}
