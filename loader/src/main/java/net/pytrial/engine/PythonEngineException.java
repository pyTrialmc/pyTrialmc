package net.pytrial.engine;

public class PythonEngineException extends RuntimeException {

    public PythonEngineException(String message) {
        super(message);
    }

    public PythonEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
