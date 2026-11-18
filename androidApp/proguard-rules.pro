# ONNX Runtime JNI looks up these classes, constructors and fields by their original names.
# Keep the small Java bridge; R8 can still remove unrelated app/dependency code.
-keep class ai.onnxruntime.** { *; }
