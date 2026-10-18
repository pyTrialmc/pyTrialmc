package net.pytrial.bootstrap.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pytrial.bootstrap.util.BootstrapLog;
import net.pytrial.bootstrap.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ArgumentBuilder {

    private ArgumentBuilder() {
    }

    public static List<String> build(List<Object> raw, Map<String, String> variables, Map<String, Boolean> features) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (Object element : raw) {
            if (element == null) {
                continue;
            }
            JsonElement json = Json.PRETTY.toJsonTree(element);

            if (json.isJsonPrimitive()) {
                result.add(expand(json.getAsString(), variables));
                continue;
            }
            if (!json.isJsonObject()) {
                continue;
            }

            JsonObject object = json.getAsJsonObject();
            JsonArray rules = object.has("rules") ? object.getAsJsonArray("rules") : null;
            if (rules != null && !RuleEvaluator.allows(parseRules(rules), features)) {
                continue;
            }
            if (!object.has("value")) {
                continue;
            }
            JsonElement value = object.get("value");
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    result.add(expand(item.getAsString(), variables));
                }
            } else {
                result.add(expand(value.getAsString(), variables));
            }
        }
        return result;
    }

    public static List<String> buildLegacy(String minecraftArguments, Map<String, String> variables) {
        List<String> result = new ArrayList<>();
        if (minecraftArguments == null || minecraftArguments.isBlank()) {
            return result;
        }
        for (String token : minecraftArguments.trim().split("\\s+")) {
            result.add(expand(token, variables));
        }
        return result;
    }

    public static String expand(String template, Map<String, String> variables) {
        if (template == null || template.indexOf("${") < 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (true) {
            int start = template.indexOf("${", index);
            if (start < 0) {
                sb.append(template, index, template.length());
                break;
            }
            sb.append(template, index, start);
            int end = template.indexOf('}', start);
            if (end < 0) {
                sb.append(template.substring(start));
                break;
            }
            String name = template.substring(start + 2, end);
            String value = variables.get(name);
            if (value == null) {
                BootstrapLog.debug("Unresolved launch variable: ${" + name + "}");
                value = "";
            }
            sb.append(value);
            index = end + 1;
        }
        return sb.toString();
    }

    private static List<VersionJson.Rule> parseRules(JsonArray array) {
        List<VersionJson.Rule> rules = new ArrayList<>();
        for (JsonElement element : array) {
            VersionJson.Rule rule = Json.PRETTY.fromJson(element, VersionJson.Rule.class);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }
}
