# Pleos SDK: AIDL·Parcelable·kotlinx.serialization 모델은 이름이 바뀌면 서비스와 통신할 수 없다.
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**
# android.car는 기기 부트클래스패스에서 제공된다
-dontwarn android.car.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ai.heartz.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ai.heartz.**$$serializer { *; }
