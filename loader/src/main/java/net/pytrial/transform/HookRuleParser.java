package net.pytrial.transform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pytrial.util.Json;
import net.pytrial.util.PyTrialLog;

import java.util.ArrayList;
import java.util.List;

public final class HookRuleParser {

    private HookRuleParser() {
    }

    public static List<HookRule> parse(JsonArray array, String modId) {
        List<HookRule> rules = new ArrayList<>();
        if (array == null) {
            return rules;
        }
        int index = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                PyTrialLog.warn("Ignored a non-object hook declaration (mod " + modId + ")");
                continue;
            }
            HookRule rule = parse(element.getAsJsonObject(), modId, index);
            if (rule != null) {
                rules.add(rule);
            }
            index++;
        }
        return rules;
    }

    private static HookRule parse(JsonObject json, String modId, int index) {
        String target = Json.optString(json, "target", null);
        String method = Json.optString(json, "method", null);
        if (target == null || target.isBlank() || method == null || method.isBlank()) {
            PyTrialLog.warn("Hook declaration is missing target/method, ignored (mod " + modId + ")");
            return null;
        }
        if (method.startsWith("<")) {
            PyTrialLog.warn("Injecting into constructors / static initialisers is not supported: " + target + "#" + method);
            return null;
        }

        String id = Json.optString(json, "id", modId + ".hook" + index);
        String descriptor = Json.optString(json, "descriptor", "*");
        String invokeTarget = Json.optString(json, "invokeTarget",
                Json.optString(json, "invoke_target", null));

        HookRule.InjectionPoint point = parsePoint(Json.optString(json, "at", "HEAD"));
        if ((point == HookRule.InjectionPoint.BEFORE_INVOKE || point == HookRule.InjectionPoint.AFTER_INVOKE)
                && (invokeTarget == null || invokeTarget.isBlank())) {
            PyTrialLog.warn("at=" + point + " requires invokeTarget, ignored (mod " + modId + ")");
            return null;
        }

        return new HookRule(id, target, method, descriptor, point, invokeTarget, modId);
    }

    private static HookRule.InjectionPoint parsePoint(String raw) {
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        for (HookRule.InjectionPoint point : HookRule.InjectionPoint.values()) {
            if (point.name().equals(normalized)) {
                return point;
            }
        }
        PyTrialLog.warn("Unknown injection point \"" + raw + "\", falling back to HEAD");
        return HookRule.InjectionPoint.HEAD;
    }
}
