import os
import subprocess
import shutil
import glob
import sys

WORKDIR = "/home/ubuntu/gimy-tv-app"
os.chdir(WORKDIR)

print("=================================================")
print("  Gimy TV App - Automated Build & Test System     ")
print("=================================================")

# Create clean build directories
print("\n[Build] Preparing build directories...")
shutil.rmtree("app/build", ignore_errors=True)
os.makedirs("app/build/bin", exist_ok=True)
os.makedirs("app/build/obj", exist_ok=True)

# 1. Compile all Java classes (Main sources + Test sources)
print("\n[Build] 1. Compiling Java classes (main & test)...")
java_files = (
    glob.glob("app/src/main/java/com/gimytv/horror/*.java") +
    glob.glob("app/src/test/java/com/gimytv/horror/*.java")
)
subprocess.run([
    "javac", "-target", "1.8", "-source", "1.8",
    "-bootclasspath", "/usr/lib/android-sdk/platforms/android-23/android.jar",
    "-d", "app/build/obj"
] + java_files, check=True)

# 2. Execute Automated Unit Tests (TDD Enforcement)
print("\n[Test] 2. Executing automated test suite...")
test_runner = subprocess.run([
    "java", "-cp", "app/build/obj", "com.gimytv.horror.TestRunner"
])

if test_runner.returncode != 0:
    print("\n[Test] !!! REGRESSION DETECTED: Unit tests failed! Aborting build process !!!")
    sys.exit(1)
else:
    print("\n[Test] >>> Unit tests passed successfully! Proceeding with packaging.")

# 3. Package resources with aapt
print("\n[Build] 3. Packaging resources with aapt...")
subprocess.run([
    "aapt", "package", "-f", "-m",
    "-M", "app/src/main/AndroidManifest.xml",
    "-S", "app/src/main/res",
    "-I", "/usr/lib/android-sdk/platforms/android-23/android.jar",
    "-F", "app/build/bin/resources.ap_"
], check=True)

# 4. Compile Java bytecode to Android Dalvik bytecode (.dex)
# Note: To keep the production APK clean and tiny, we only compile main classes to Dalvik.
# We create a temporary obj folder for Dalvik classes to exclude test runner classes.
print("\n[Build] 4. Compiling bytecode to dex via dalvik-exchange...")
dx_obj_dir = "app/build/dx_obj"
target_package_dir = os.path.join(dx_obj_dir, "com", "gimytv", "horror")
os.makedirs(target_package_dir, exist_ok=True)
for f in glob.glob("app/build/obj/com/gimytv/horror/*.class"):
    filename = os.path.basename(f)
    # Exclude test files from the final production APK
    if "Test" not in filename:
        shutil.copy(f, os.path.join(target_package_dir, filename))

subprocess.run([
    "dalvik-exchange", "--dex", "--output=app/build/bin/classes.dex", dx_obj_dir
], check=True)
shutil.rmtree(dx_obj_dir)

# 5. Packaging classes.dex to the APK root
print("\n[Build] 5. Packaging classes.dex to the APK root...")
shutil.copy("app/build/bin/resources.ap_", "app/build/bin/unsigned.apk")
shutil.copy("app/build/bin/classes.dex", "classes.dex")
subprocess.run([
    "aapt", "add", "app/build/bin/unsigned.apk", "classes.dex"
], check=True)
os.remove("classes.dex")

# 6. Optimize memory alignment (zipalign)
print("\n[Build] 6. Aligning APK...")
subprocess.run([
    "zipalign", "-f", "4", "app/build/bin/unsigned.apk", "app/build/bin/aligned.apk"
], check=True)

# 7. Generate a self-signed key if it does not exist
print("\n[Build] 7. Checking signing key...")
if not os.path.exists("my-release-key.jks"):
    print("[Build] Creating new self-signed release key...")
    subprocess.run([
        "keytool", "-genkey", "-v",
        "-keystore", "my-release-key.jks",
        "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10000",
        "-alias", "my-alias",
        "-storepass", "123456",
        "-keypass", "123456",
        "-dname", "CN=User, O=Company, C=US"
    ], check=True)

# 8. Cryptographically sign the APK (modern V2/V3 signing)
print("\n[Build] 8. Signing APK...")
subprocess.run([
    "apksigner", "sign",
    "--ks", "my-release-key.jks",
    "--ks-pass", "pass:123456",
    "--key-pass", "pass:123456",
    "--out", "app/build/bin/signed.apk",
    "app/build/bin/aligned.apk"
], check=True)

# Update bin copy to maintain old install paths if expected by helper scripts
os.makedirs("bin", exist_ok=True)
shutil.copy("app/build/bin/signed.apk", "bin/signed.apk")

print("\n=================================================")
print(" BUILD SUCCESSFUL!")
print(" Production APK: app/build/bin/signed.apk")
print(" Quick Link APK: bin/signed.apk")
print("=================================================")
