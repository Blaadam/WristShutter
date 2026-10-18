plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the app module applies no Kotlin plugin.
    // Declaring KGP here only pins the Kotlin compiler version that AGP uses.
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}
