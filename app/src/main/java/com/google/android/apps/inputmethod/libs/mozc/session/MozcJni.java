package com.google.android.apps.inputmethod.libs.mozc.session;

/** Names and signatures are registered by the upstream libmozc Android JNI entry point. */
public final class MozcJni {
    private MozcJni() {}
    public static native boolean initialize();
    public static native boolean onPostLoad(String profileDirectory, String dataFile);
    public static native String getDataVersion();
    public static native byte[] evalCommand(byte[] command);
}
