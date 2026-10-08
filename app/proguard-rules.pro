# Forget-it release rules. Room, WorkManager, Glance, Coil and ML Kit ship their own consumer rules.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Receivers and services are referenced from the manifest, which R8 already keeps; the Gmail JSON reader uses org.json from the platform.
-dontwarn org.slf4j.**

# Patch library: commons-compress names optional codecs that are not shipped.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-keep class io.sigpipe.jbsdiff.** { *; }
