# v0.2.1 validation status

This repository is a development/qualification baseline, not a completed security guarantee.

## Performed while assembling this source bundle

- Repository inventory and forbidden-file review.
- XML well-formedness checks for Android resources and manifest files.
- Java source structural sanity checks.
- Standalone `javac` compilation of the configuration model (`Ipv4`, `ConfigKeys`, `Profile`) using a minimal `SharedPreferences` compile stub.
- Verification that the manifest does not request `INTERNET` permission.
- Verification that legacy Xposed entry metadata/API imports are absent.
- Verification that modern `META-INF/xposed/` metadata is present.
- ZIP integrity verification after packaging.

## Canonical build still required

The source bundle must receive a clean Android build before publication:

```text
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```

The included GitHub Actions workflow provisions the declared JDK/Gradle toolchain and performs this build.

## Physical qualification still required

Run `docs/DEVICE-TEST-MATRIX.md` against Android 15 and Android 16, including active-VPN, Wi-Fi, cellular, multi-process, Private DNS, proxy, IPv6/CLAT and routing-invariance cases.

Do not describe the module as comprehensively hiding native/kernel-visible networking state; those paths are explicitly outside the v0.2.1 Java backend.
