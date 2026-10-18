package net.pytrial.agent;

import net.pytrial.PyTrialEnvironment;
import net.pytrial.PyTrialKernel;
import net.pytrial.util.PyTrialLog;

import java.lang.instrument.Instrumentation;

public final class PyTrialAgent {

    private PyTrialAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        bootstrap(agentArgs, instrumentation, "premain");
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        bootstrap(agentArgs, instrumentation, "agentmain");
    }

    private static void bootstrap(String agentArgs, Instrumentation instrumentation, String phase) {
        try {
            PyTrialKernel kernel = PyTrialKernel.get();
            kernel.prepare(PyTrialEnvironment.fromAgentArgs(agentArgs), instrumentation);
        } catch (Throwable t) {
            PyTrialLog.error("pyTrial failed to attach as " + phase + " (the game will continue vanilla)", t);
        }
    }
}
