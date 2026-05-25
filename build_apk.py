import os
import subprocess
import shutil

WORKDIR = "/home/ubuntu/gimy_tv_app"
os.chdir(WORKDIR)

print("Creating build directories...")
os.makedirs("bin", exist_ok=True)
os.makedirs("obj", exist_ok=True)
os.makedirs("res", exist_ok=True) # Keep empty res folder

# 1. Package AndroidManifest.xml and generate base template
print("1. Packaging resources with aapt...")
subprocess.run([
    "aapt", "package", "-f", "-m",
    "-M", "AndroidManifest.xml",
    "-S", "res",
    "-I", "/usr/lib/android-sdk/platforms/android-23/android.jar",
    "-F", "bin/resources.ap_"
], check=True)

# 2. Compile Java classes targeting standard JVM 1.8 (Dalvik compatible)
print("2. Compiling Java classes...")
import glob
java_files = glob.glob("src/com/gimytv/horror/*.java")
subprocess.run([
    "javac", "-target", "1.8", "-source", "1.8",
    "-bootclasspath", "/usr/lib/android-sdk/platforms/android-23/android.jar",
    "-d", "obj"
] + java_files, check=True)

# 3. Compile Java bytecode to Android Dalvik bytecode (.dex)
print("3. Compiling bytecode to dex via dalvik-exchange...")
subprocess.run([
    "dalvik-exchange", "--dex", "--output=bin/classes.dex", "obj"
], check=True)

# 4. Copy raw apk and add classes.dex directly to root
# CRITICAL PATH: aapt add preserves directories. To add classes.dex to the root 
# of the APK instead of bin/classes.dex, copy it to CWD first before adding.
print("4. Packaging classes.dex to the APK root...")
shutil.copy("bin/resources.ap_", "bin/unsigned.apk")
shutil.copy("bin/classes.dex", "classes.dex")
subprocess.run([
    "aapt", "add", "bin/unsigned.apk", "classes.dex"
], check=True)
os.remove("classes.dex")

# 5. Optimize memory alignment (zipalign)
print("5. Aligning APK...")
subprocess.run([
    "zipalign", "-f", "4", "bin/unsigned.apk", "bin/aligned.apk"
], check=True)

# 6. Generate a self-signed key if not exists
print("6. Creating debug/release signing key...")
if not os.path.exists("my-release-key.jks"):
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

# 7. Cryptographically sign the APK (modern V2/V3 signing)
print("7. Signing APK...")
subprocess.run([
    "apksigner", "sign",
    "--ks", "my-release-key.jks",
    "--ks-pass", "pass:123456",
    "--key-pass", "pass:123456",
    "--out", "bin/signed.apk",
    "bin/aligned.apk"
], check=True)

print("\nBUILD SUCCESS! /home/ubuntu/gimy_tv_app/bin/signed.apk is ready!")
