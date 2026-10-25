package libcore;

/** Compile-only stand-in for gomobile {@code libcore.BoxPlatformInterface}. */
public interface BoxPlatformInterface {
    void autoDetectInterfaceControl(int fd) throws Exception;

    long openTun(String singTunOptionsJson, String tunPlatformOptionsJson) throws Exception;

    boolean useProcFS();

    int findConnectionOwner(
            int ipProtocol,
            String sourceAddress,
            int sourcePort,
            String destinationAddress,
            int destinationPort
    ) throws Exception;

    String packageNameByUid(int uid) throws Exception;

    int uidByPackageName(String packageName) throws Exception;

    String wifiState();
}
