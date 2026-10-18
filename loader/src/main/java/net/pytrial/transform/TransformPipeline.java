package net.pytrial.transform;

import net.pytrial.util.PyTrialLog;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TransformPipeline implements ClassFileTransformer {

    private final List<ClassTransformer> transformers = new CopyOnWriteArrayList<>();

    public void add(ClassTransformer transformer) {
        transformers.add(transformer);
        PyTrialLog.debug("Registering bytecode transformer: " + transformer.name());
    }

    public List<String> names() {
        List<String> result = new ArrayList<>();
        transformers.forEach(transformer -> result.add(transformer.name()));
        return result;
    }

    public boolean isEmpty() {
        return transformers.isEmpty();
    }

    public void clear() {
        transformers.clear();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || transformers.isEmpty()) {
            return null;
        }

        TransformContext context = new TransformContext(className, loader, protectionDomain);
        byte[] current = classfileBuffer;

        for (ClassTransformer transformer : transformers) {
            try {
                byte[] result = transformer.transform(context, current);
                if (result != null && result.length > 0) {
                    current = result;
                }
            } catch (Throwable t) {
                PyTrialLog.error("Transformer " + transformer.name() + " failed on " + className, t);
            }
        }
        return current == classfileBuffer ? null : current;
    }
}
