package net.pytrial.mapping;

import java.util.function.UnaryOperator;

public final class DescriptorUtil {

    private DescriptorUtil() {
    }

    public static String typeToDescriptor(String type) {
        return typeToDescriptor(type, name -> name);
    }

    public static String typeToDescriptor(String type, UnaryOperator<String> classMapper) {
        String trimmed = type.trim();
        int arrayDepth = 0;
        while (trimmed.endsWith("[]")) {
            arrayDepth++;
            trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
        }

        StringBuilder result = new StringBuilder();
        result.append("[".repeat(arrayDepth));

        switch (trimmed) {
            case "void" -> result.append('V');
            case "boolean" -> result.append('Z');
            case "byte" -> result.append('B');
            case "char" -> result.append('C');
            case "short" -> result.append('S');
            case "int" -> result.append('I');
            case "long" -> result.append('J');
            case "float" -> result.append('F');
            case "double" -> result.append('D');
            default -> result.append('L')
                    .append(classMapper.apply(trimmed).replace('.', '/'))
                    .append(';');
        }
        return result.toString();
    }

    public static String obfuscate(String officialDescriptor, UnaryOperator<String> classMapper) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < officialDescriptor.length()) {
            char current = officialDescriptor.charAt(index);
            if (current != 'L') {
                result.append(current);
                index++;
                continue;
            }
            int end = officialDescriptor.indexOf(';', index);
            if (end < 0) {
                result.append(officialDescriptor.substring(index));
                break;
            }
            String name = officialDescriptor.substring(index + 1, end).replace('/', '.');
            result.append('L').append(classMapper.apply(name).replace('.', '/')).append(';');
            index = end + 1;
        }
        return result.toString();
    }
}
