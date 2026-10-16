# release 빌드에서 android.util.Log 호출을 제거한다.
# 실수로 본문이 로그 인자에 들어가더라도 release APK에는 남지 않도록 하는 이중 안전장치.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static boolean isLoggable(java.lang.String, int);
}
