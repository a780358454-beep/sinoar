# Keep JNI entry points used by whisper.cpp / ONNX bridges (added in later phases).
-keepclasseswithmembernames class * {
    native <methods>;
}
