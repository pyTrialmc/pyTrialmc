package net.pytrial.transform;

import net.pytrial.util.PyTrialLog;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AsmHookTransformer implements ClassTransformer {

    private static final String DISPATCHER = "net/pytrial/bridge/HookDispatcher";

    private final Map<String, List<HookRule>> rulesByOwner = new LinkedHashMap<>();
    private final List<String> appliedHooks = new ArrayList<>();

    public AsmHookTransformer(List<HookRule> rules) {
        for (HookRule rule : rules) {
            rulesByOwner.computeIfAbsent(rule.internalName(), key -> new ArrayList<>()).add(rule);
        }
    }

    @Override
    public String name() {
        return "AsmHookTransformer(" + rulesByOwner.size() + " target class(es))";
    }

    public List<String> appliedHooks() {
        return List.copyOf(appliedHooks);
    }

    @Override
    public byte[] transform(TransformContext context, byte[] original) {
        List<HookRule> rules = rulesByOwner.get(context.className());
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        ClassNode node = new ClassNode();
        try {
            new ClassReader(original).accept(node, ClassReader.EXPAND_FRAMES);
        } catch (Throwable t) {
            PyTrialLog.error("Cannot parse class " + context.dottedName(), t);
            return null;
        }

        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (HookRule rule : rules) {
                if (!rule.method().equals(method.name)) {
                    continue;
                }
                if (!rule.matchesAnyDescriptor() && !rule.descriptor().equals(method.desc)) {
                    continue;
                }
                if (inject(method, rule)) {
                    changed = true;
                    appliedHooks.add(rule.id());
                }
            }
        }

        if (!changed) {
            return null;
        }

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.debug("Injected hooks into " + context.dottedName());
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back class " + context.dottedName() + ", keeping the original bytecode", t);
            return null;
        }
    }

    private boolean inject(MethodNode method, HookRule rule) {
        if (method.instructions == null || method.instructions.size() == 0) {
            return false;
        }
        return switch (rule.point()) {
            case HEAD -> injectAtHead(method, rule);
            case RETURN -> injectBeforeReturns(method, rule);
            case BEFORE_INVOKE -> injectAroundInvoke(method, rule, true);
            case AFTER_INVOKE -> injectAroundInvoke(method, rule, false);
        };
    }

    private boolean injectAtHead(MethodNode method, HookRule rule) {
        method.instructions.insert(dispatchCall(rule.id(), "onHead"));
        return true;
    }

    private boolean injectBeforeReturns(MethodNode method, HookRule rule) {
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                returns.add(insn);
            }
        }
        for (AbstractInsnNode insn : returns) {
            method.instructions.insertBefore(insn, dispatchCall(rule.id(), "onReturn"));
        }
        return !returns.isEmpty();
    }

    private boolean injectAroundInvoke(MethodNode method, HookRule rule, boolean before) {
        String owner = invokeOwner(rule.invokeTarget());
        String name = invokeMethodName(rule.invokeTarget());
        if (owner == null || name == null) {
            PyTrialLog.warn("Cannot resolve invokeTarget: " + rule.invokeTarget());
            return false;
        }

        List<AbstractInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                matches.add(insn);
            }
        }
        for (AbstractInsnNode insn : matches) {
            InsnList call = dispatchCall(rule.id(), before ? "onBeforeInvoke" : "onAfterInvoke");
            if (before) {
                method.instructions.insertBefore(insn, call);
            } else {
                method.instructions.insert(insn, call);
            }
        }
        return !matches.isEmpty();
    }

    private static InsnList dispatchCall(String hookId, String dispatcherMethod) {
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(hookId));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER, dispatcherMethod,
                "(Ljava/lang/String;)V", false));
        return list;
    }

    private static String invokeOwner(String invokeTarget) {
        int sharp = invokeTarget.indexOf('#');
        if (sharp <= 0) {
            return null;
        }
        return invokeTarget.substring(0, sharp).trim().replace('.', '/');
    }

    private static String invokeMethodName(String invokeTarget) {
        int sharp = invokeTarget.indexOf('#');
        if (sharp < 0 || sharp + 1 >= invokeTarget.length()) {
            return null;
        }
        String rest = invokeTarget.substring(sharp + 1).trim();
        int paren = rest.indexOf('(');
        return paren >= 0 ? rest.substring(0, paren).trim() : rest;
    }
}
