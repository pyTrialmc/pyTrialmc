package net.pytrial.transform;

public interface ClassTransformer {

    String name();

    byte[] transform(TransformContext context, byte[] original);
}
