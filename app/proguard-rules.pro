# Open-source app: keep names so crash traces point to real classes and lines.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Saved data (articles and settings) is read and written with Gson by reflection.
-keep class app.mari.reader.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**

# Jsoup optional regex engine.
-dontwarn com.google.re2j.**
