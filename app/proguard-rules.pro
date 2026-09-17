# Keep diagnostic metadata useful without exposing local source paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JSch resolves negotiated SSH algorithms from configured class names. Removing
# or renaming these implementations makes a release build fail only at runtime.
-keep class com.jcraft.jsch.** { *; }
# Optional desktop-only JSch integrations are absent on Android and are not
# selected by Didban's SSH algorithm/authentication policy.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

# Bouncy Castle providers register algorithm implementation class names and
# instantiate them reflectively through java.security.Provider.
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# Preserve generic signatures and runtime annotations used by platform and
# library adapters while allowing application classes to be optimized.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
