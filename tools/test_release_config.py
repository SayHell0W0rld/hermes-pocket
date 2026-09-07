from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


class ReleaseConfigTests(unittest.TestCase):
    def test_release_signing_is_configured_from_secrets_without_hardcoding(self) -> None:
        build_file = read("app/build.gradle.kts")

        self.assertIn('rootProject.file("keystore.properties")', build_file)
        self.assertIn('signingCredential("KEYSTORE_FILE", "storeFile")', build_file)
        self.assertIn('signingCredential("KEYSTORE_PASSWORD", "storePassword")', build_file)
        self.assertIn('signingCredential("KEY_ALIAS", "keyAlias")', build_file)
        self.assertIn('signingCredential("KEY_PASSWORD", "keyPassword")', build_file)
        self.assertIn("signingConfig = if (releaseStorePath != null)", build_file)
        for secret in ("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertNotIn(f'"{secret} = ', build_file)

    def test_release_workflow_supports_signed_and_debug_fallback(self) -> None:
        workflow = read(".github/workflows/release.yml")
        secret_expression = "$" + "{{ secrets.KEYSTORE_BASE64 }}"

        self.assertIn("KEYSTORE_BASE64: " + secret_expression, workflow)
        self.assertIn("signed=true", workflow)
        self.assertIn("signed=false", workflow)
        self.assertIn("./gradlew assembleRelease", workflow)
        self.assertIn("./gradlew assembleDebug", workflow)
        output_expression = "$" + "{{ steps.build.outputs.apk_path }}"
        self.assertIn(output_expression, workflow)
        self.assertIn("release.apk", workflow)
        self.assertIn("debug.apk", workflow)

    def test_ci_uses_stable_debug_keystore(self) -> None:
        ci = read(".github/workflows/ci.yml")

        self.assertIn("android-debug-keystore", ci)
        self.assertIn("~/.android/debug.keystore", ci)


if __name__ == "__main__":
    unittest.main()

