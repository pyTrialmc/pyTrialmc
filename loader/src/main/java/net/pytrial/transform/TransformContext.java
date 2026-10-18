package net.pytrial.transform;

import java.security.ProtectionDomain;

public record TransformContext(String className, ClassLoader classLoader, ProtectionDomain protectionDomain) {
    public String dottedName() {
        return className.replace('/', '.');
    }
}
