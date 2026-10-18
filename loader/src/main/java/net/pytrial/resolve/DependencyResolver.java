package net.pytrial.resolve;

import net.pytrial.discovery.ModCandidate;
import net.pytrial.lifecycle.ModContainer;
import net.pytrial.lifecycle.ModState;
import net.pytrial.metadata.ModDependency;
import net.pytrial.metadata.VersionConstraint;
import net.pytrial.util.PyTrialLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DependencyResolver {

    private DependencyResolver() {
    }

    public static List<ModContainer> resolve(List<ModCandidate> candidates) {
        Map<String, ModContainer> byId = new LinkedHashMap<>();
        for (ModCandidate candidate : candidates) {
            ModContainer container = new ModContainer(candidate);
            ModContainer previous = byId.putIfAbsent(container.id(), container);
            if (previous != null) {
                throw new DependencyException("Mod id conflict: " + container.id()
                        + " appears in both " + previous.root() + " and " + container.root());
            }
        }

        List<String> missing = new ArrayList<>();
        for (ModContainer container : byId.values()) {
            for (ModDependency dependency : container.metadata().dependencies()) {
                ModContainer target = byId.get(dependency.id());
                if (target == null) {
                    if (!dependency.optional()) {
                        missing.add(container.id() + " requires " + dependency);
                    } else {
                        PyTrialLog.debug("Optional dependency not installed: " + container.id() + " -> " + dependency);
                    }
                    continue;
                }
                if (!dependency.satisfiedBy(target.metadata().version())) {
                    missing.add(container.id() + " requires " + dependency
                            + " but the installed version is " + target.metadata().version());
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new DependencyException("Unsatisfied dependencies:\n  - " + String.join("\n  - ", missing));
        }

        List<ModContainer> ordered = topologicalOrder(byId);
        ordered.forEach(container -> container.setState(ModState.RESOLVED));
        return ordered;
    }

    private static List<ModContainer> topologicalOrder(Map<String, ModContainer> byId) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (String id : byId.keySet()) {
            edges.put(id, new LinkedHashSet<>());
            inDegree.put(id, 0);
        }

        for (ModContainer container : byId.values()) {
            for (ModDependency dependency : container.metadata().dependencies()) {
                if (dependency.optional() || !byId.containsKey(dependency.id())) {
                    continue;
                }

                addEdge(edges, inDegree, dependency.id(), container.id());
            }
            for (String after : container.metadata().loadAfter()) {
                if (byId.containsKey(after)) {
                    addEdge(edges, inDegree, after, container.id());
                }
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                queue.add(id);
            }
        });

        List<ModContainer> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            result.add(byId.get(id));
            for (String next : edges.get(id)) {
                int degree = inDegree.merge(next, -1, Integer::sum);
                if (degree == 0) {
                    queue.add(next);
                }
            }
        }

        if (result.size() != byId.size()) {
            List<String> stuck = new ArrayList<>();
            byId.keySet().stream().filter(id -> inDegree.get(id) > 0).forEach(stuck::add);
            PyTrialLog.warn("Circular dependency detected, these mods fall back to discovery order: " + stuck);
            for (String id : byId.keySet()) {
                if (inDegree.get(id) > 0 && result.stream().noneMatch(c -> c.id().equals(id))) {
                    result.add(byId.get(id));
                }
            }
        }
        return result;
    }

    private static void addEdge(Map<String, Set<String>> edges, Map<String, Integer> inDegree,
                                String from, String to) {
        if (from.equals(to)) {
            return;
        }
        if (edges.get(from).add(to)) {
            inDegree.merge(to, 1, Integer::sum);
        }
    }

    public static List<String> idsOf(List<ModContainer> containers) {
        List<String> ids = new ArrayList<>();
        containers.forEach(container -> ids.add(container.id()));
        return ids;
    }

    public static Map<String, String> versions(List<ModContainer> containers) {
        Map<String, String> map = new HashMap<>();
        containers.forEach(container -> map.put(container.id(), container.metadata().version()));
        return map;
    }

    public static boolean versionMatches(String actual, String requirement) {
        return VersionConstraint.matches(actual, requirement);
    }
}
