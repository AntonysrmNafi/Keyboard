# Point 3 hardening: release builds are minified and obfuscated so the compiled APK
# is harder to read, patch, or bypass (e.g. patching out the Dictionary Security
# Lock check). No reflection, no JSON model classes, and no native code are used in
# this app, so no extra -keep rules are required beyond R8's own defaults, which
# already keep every Activity/Service declared in AndroidManifest.xml plus their
# no-argument constructors.

# Keep line numbers in stack traces for crash triage, but strip the source file name.
-keepattributes SourceFile
-renamesourcefileattribute SourceFile
 
