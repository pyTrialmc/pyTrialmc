package net.pytrial.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class MappingParser {

    private static final Pattern LINE_PREFIX = Pattern.compile("^\\d+:\\d+:");

    private MappingParser() {
    }

    public static MappingSet parse(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static MappingSet parse(List<String> lines) {
        MappingSet mappings = new MappingSet();
        MappingSet.ClassMapping current = null;

        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            char first = raw.charAt(0);
            if (first != ' ' && first != '\t') {
                current = parseClassLine(mappings, raw);
            } else if (current != null) {
                parseMemberLine(current, raw.trim());
            }
        }
        return mappings;
    }

    private static MappingSet.ClassMapping parseClassLine(MappingSet mappings, String line) {
        int arrow = line.indexOf(" -> ");
        if (arrow < 0) {
            return null;
        }
        String official = line.substring(0, arrow).trim();
        String obf = line.substring(arrow + 4).trim();
        if (obf.endsWith(":")) {
            obf = obf.substring(0, obf.length() - 1);
        }
        if (official.isEmpty() || obf.isEmpty()) {
            return null;
        }
        return mappings.addClass(official, obf);
    }

    private static void parseMemberLine(MappingSet.ClassMapping mapping, String line) {
        int arrow = line.indexOf(" -> ");
        if (arrow < 0) {
            return;
        }
        String left = LINE_PREFIX.matcher(line.substring(0, arrow).trim()).replaceFirst("");
        String obf = line.substring(arrow + 4).trim();
        if (obf.isEmpty()) {
            return;
        }

        int paren = left.indexOf('(');
        if (paren >= 0) {
            parseMethod(mapping, left, obf, paren);
        } else {
            parseField(mapping, left, obf);
        }
    }

    private static void parseMethod(MappingSet.ClassMapping mapping, String left, String obf, int paren) {
        int close = left.lastIndexOf(')');
        if (close < paren) {
            return;
        }
        String beforeParen = left.substring(0, paren).trim();
        int space = beforeParen.lastIndexOf(' ');
        if (space < 0) {
            return;
        }
        String returnType = beforeParen.substring(0, space).trim();
        String name = beforeParen.substring(space + 1).trim();
        String parameters = left.substring(paren + 1, close);

        StringBuilder descriptor = new StringBuilder("(");
        if (!parameters.isBlank()) {
            for (String parameter : parameters.split(",")) {
                descriptor.append(DescriptorUtil.typeToDescriptor(parameter));
            }
        }
        descriptor.append(')').append(DescriptorUtil.typeToDescriptor(returnType));

        mapping.methodNames.put(name + descriptor, obf);
    }

    private static void parseField(MappingSet.ClassMapping mapping, String left, String obf) {
        int space = left.lastIndexOf(' ');
        if (space < 0) {
            return;
        }
        String name = left.substring(space + 1).trim();
        if (!name.isEmpty()) {
            mapping.fieldNames.put(name, obf);
        }
    }
}
