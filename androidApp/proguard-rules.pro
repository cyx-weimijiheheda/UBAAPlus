# ONNX Runtime JNI looks up these classes, constructors and fields by their original names.
# Keep the small Java bridge; R8 can still remove unrelated app/dependency code.
-keep class ai.onnxruntime.** { *; }

# jsoup probes this optional engine via Class.forName and falls back to java.util.regex.
# The app uses DOM/CSS selectors and does not bundle or enable RE2/J.
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
