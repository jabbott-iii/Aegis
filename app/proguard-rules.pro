# R8 rules for the release build (NFR-SEC-05).

# Defense in depth for PRIVACY.md § Logging: LogPolicy.RELEASE already drops everything at INFO
# and below, and R8 also removes any verbose, debug, or info logcat call that remains.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
