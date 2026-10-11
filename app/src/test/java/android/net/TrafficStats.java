package android.net;

/**
 * Test implementation of android.net.TrafficStats for JVM unit tests,
 * allowing Media3 DefaultHttpDataSource to open connections without android.jar stubs.
 */
public class TrafficStats {
    private static int threadStatsTag = 0;

    public static void setThreadStatsTag(int tag) {
        threadStatsTag = tag;
    }

    public static int getThreadStatsTag() {
        return threadStatsTag;
    }

    public static void clearThreadStatsTag() {
        threadStatsTag = 0;
    }
}
