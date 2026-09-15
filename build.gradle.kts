plugins {
    id("com.android.application") version "9.4.0" apply false
}

val requiredJdk = 25
val runningJdk = JavaVersion.current().majorVersion.toInt()
if (runningJdk < requiredJdk) {
    throw GradleException("本工程要求 JDK $requiredJdk 及以上，当前运行于 JDK $runningJdk")
}
