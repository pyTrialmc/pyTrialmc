package net.pytrial.mapping;

import java.util.HashMap;
import java.util.Map;

public final class MappingSet {

    public record MethodRef(String owner, String name, String descriptor) {
    }

    public static final MappingSet EMPTY = new MappingSet();

    static final class ClassMapping {
        final String obfName;
        final Map<String, String> methodNames = new HashMap<>();
        final Map<String, String> fieldNames = new HashMap<>();

        ClassMapping(String obfName) {
            this.obfName = obfName;
        }
    }

    private final Map<String, ClassMapping> classes = new HashMap<>();

    ClassMapping addClass(String officialName, String obfName) {
        ClassMapping mapping = new ClassMapping(obfName);
        classes.put(officialName, mapping);
        return mapping;
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }

    public int classCount() {
        return classes.size();
    }

    public String obfClassName(String officialName) {
        ClassMapping mapping = classes.get(officialName);
        return mapping != null ? mapping.obfName : officialName;
    }

    public MethodRef obfMethod(String officialOwner, String officialName, String officialDescriptor) {
        ClassMapping mapping = classes.get(officialOwner);
        if (mapping == null) {
            return classes.isEmpty()
                    ? new MethodRef(officialOwner.replace('.', '/'), officialName, officialDescriptor)
                    : null;
        }
        String obfName = mapping.methodNames.get(officialName + officialDescriptor);
        if (obfName == null) {
            return null;
        }
        return new MethodRef(mapping.obfName, obfName,
                DescriptorUtil.obfuscate(officialDescriptor, this::obfClassName));
    }

    public String obfFieldName(String officialOwner, String officialFieldName) {
        ClassMapping mapping = classes.get(officialOwner);
        if (mapping == null) {
            return classes.isEmpty() ? officialFieldName : null;
        }
        return mapping.fieldNames.get(officialFieldName);
    }
}
