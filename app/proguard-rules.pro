# ============================================================================
#  Axiom Calc - R8 / ProGuard rules (release build type)
# ----------------------------------------------------------------------------
#  The application is fully offline and contains no reflection-based plugin
#  system, so the rule set is deliberately small. Everything here exists to
#  keep persisted data and generated code intact after shrinking.
# ============================================================================

# ---- Room ----
# Room generates `*_Impl` classes that are instantiated by name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---- Kotlin metadata / coroutines ----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# ---- java.math (BigDecimal / BigInteger) ----
# The calculation engine depends on exact decimal semantics; never optimise
# away or rename its members.
-keep class java.math.BigDecimal { *; }
-keep class java.math.BigInteger { *; }
-keepclassmembers class com.shohankhan.axiomcalc.math.** { *; }
-keepclassmembers class com.shohankhan.axiomcalc.domain.** { *; }

# ---- WorkManager ----
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class com.shohankhan.axiomcalc.worker.** { *; }

# ---- Enum valueOf / values used by settings serialisation ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Compose ----
-dontwarn androidx.compose.**

# ---- Keep line numbers for readable crash reports (no crash SDK is bundled) ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute AxiomCalc
