package android.os;

/**
 * Test implementation of android.os.ConditionVariable for JVM unit tests,
 * allowing Media3 SimpleCache background initialization to synchronize properly.
 */
public class ConditionVariable {
    private volatile boolean state;

    public ConditionVariable() {
        this(false);
    }

    public ConditionVariable(boolean state) {
        this.state = state;
    }

    public synchronized void open() {
        state = true;
        notifyAll();
    }

    public synchronized void close() {
        state = false;
    }

    public synchronized void block() {
        while (!state) {
            try {
                wait();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public synchronized boolean block(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!state) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            try {
                wait(remaining);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return state;
    }
}
