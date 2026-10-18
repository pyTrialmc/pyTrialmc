package net.pytrial.transform;

public record HookRule(
        String id,
        String target,
        String method,
        String descriptor,
        InjectionPoint point,
        String invokeTarget,
        String modId
) {
    public enum InjectionPoint {

        HEAD,
        RETURN,
        BEFORE_INVOKE,
        AFTER_INVOKE
    }

    public String internalName() {
        return target.replace('.', '/');
    }

    public String classDescriptor() {
        return 'L' + internalName() + ';';
    }

    public boolean matchesAnyDescriptor() {
        return descriptor == null || descriptor.isBlank() || "*".equals(descriptor);
    }
}
