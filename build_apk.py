import os
import sys
import shutil
import zipfile
import subprocess

# Paths
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
APP_DIR = os.path.join(PROJECT_DIR, 'app')
MAIN_DIR = os.path.join(APP_DIR, 'src', 'main')
BUILD_DIR = os.path.join(APP_DIR, 'build')

SDK_ROOT = r'C:\Android\Sdk'
BUILD_TOOLS = os.path.join(SDK_ROOT, 'build-tools', '34.0.0')
PLATFORM_JAR = os.path.join(SDK_ROOT, 'platforms', 'android-34', 'android.jar')
JDK_BIN = r'C:\Android\jdk-17\bin'

AAPT2 = os.path.join(BUILD_TOOLS, 'aapt2.exe')
D8 = os.path.join(BUILD_TOOLS, 'd8.bat')
ZIPALIGN = os.path.join(BUILD_TOOLS, 'zipalign.exe')
APKSIGNER = os.path.join(BUILD_TOOLS, 'apksigner.bat')
JAVAC = os.path.join(JDK_BIN, 'javac.exe')
KEYTOOL = os.path.join(JDK_BIN, 'keytool.exe')

ENV = os.environ.copy()
ENV['JAVA_HOME'] = r'C:\Android\jdk-17'
ENV['PATH'] = JDK_BIN + ';' + BUILD_TOOLS + ';' + ENV.get('PATH', '')

def run_cmd(cmd, desc):
    print(f"[*] {desc}...")
    res = subprocess.run(cmd, env=ENV, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"[!] Error during: {desc}")
        print(res.stdout)
        print(res.stderr)
        sys.exit(1)
    return res.stdout

def build():
    # 0. Clean & Prepare Build Dir
    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    os.makedirs(BUILD_DIR, exist_ok=True)

    compiled_res = os.path.join(BUILD_DIR, 'compiled_res')
    os.makedirs(compiled_res, exist_ok=True)
    gen_dir = os.path.join(BUILD_DIR, 'gen')
    os.makedirs(gen_dir, exist_ok=True)
    classes_dir = os.path.join(BUILD_DIR, 'classes')
    os.makedirs(classes_dir, exist_ok=True)

    res_dir = os.path.join(MAIN_DIR, 'res')
    manifest_xml = os.path.join(MAIN_DIR, 'AndroidManifest.xml')
    assets_dir = os.path.join(MAIN_DIR, 'assets')

    # 1. Compile Resources with aapt2
    run_cmd([AAPT2, 'compile', '--dir', res_dir, '-o', compiled_res], "Compiling Android resources")

    # 2. Link Resources with aapt2
    flat_files = [os.path.join(compiled_res, f) for f in os.listdir(compiled_res) if f.endswith('.flat')]
    unaligned_apk = os.path.join(BUILD_DIR, 'app.unaligned.apk')
    
    link_cmd = [
        AAPT2, 'link',
        '-I', PLATFORM_JAR,
        '--manifest', manifest_xml,
        '--min-sdk-version', '24',
        '--target-sdk-version', '34',
        '--java', gen_dir,
        '-A', assets_dir,
        '-o', unaligned_apk,
        '--auto-add-overlay'
    ] + flat_files
    run_cmd(link_cmd, "Linking resources & packaging assets into APK")

    # 3. Compile Java with javac
    java_files = []
    for root, _, files in os.walk(os.path.join(MAIN_DIR, 'java')):
        for f in files:
            if f.endswith('.java'):
                java_files.append(os.path.join(root, f))
    for root, _, files in os.walk(gen_dir):
        for f in files:
            if f.endswith('.java'):
                java_files.append(os.path.join(root, f))

    javac_cmd = [
        JAVAC,
        '-encoding', 'UTF-8',
        '-classpath', PLATFORM_JAR,
        '-d', classes_dir
    ] + java_files
    run_cmd(javac_cmd, "Compiling Java classes")

    # 4. Dex with D8
    class_files = []
    for root, _, files in os.walk(classes_dir):
        for f in files:
            if f.endswith('.class'):
                class_files.append(os.path.join(root, f))

    dex_dir = os.path.join(BUILD_DIR, 'dex')
    os.makedirs(dex_dir, exist_ok=True)
    d8_cmd = [
        D8,
        '--output', dex_dir,
        '--lib', PLATFORM_JAR
    ] + class_files
    run_cmd(d8_cmd, "Translating bytecode to Dalvik Executable (classes.dex)")

    # 5. Insert classes.dex into unaligned APK
    print("[*] Adding classes.dex to APK...")
    dex_path = os.path.join(dex_dir, 'classes.dex')
    with zipfile.ZipFile(unaligned_apk, 'a', zipfile.ZIP_DEFLATED) as zf:
        zf.write(dex_path, 'classes.dex')

    # 6. Zipalign APK
    aligned_apk = os.path.join(BUILD_DIR, 'app.aligned.apk')
    run_cmd([ZIPALIGN, '-f', '-p', '4', unaligned_apk, aligned_apk], "Aligning APK boundaries (zipalign)")

    # 7. Create Keystore if needed
    keystore_path = os.path.join(PROJECT_DIR, 'debug.keystore')
    if not os.path.exists(keystore_path):
        keytool_cmd = [
            KEYTOOL,
            '-genkeypair', '-v',
            '-keystore', keystore_path,
            '-alias', 'androiddebugkey',
            '-keyalg', 'RSA',
            '-keysize', '2048',
            '-validity', '10000',
            '-storepass', 'android',
            '-keypass', 'android',
            '-dname', 'CN=Android Debug,O=Android,C=US'
        ]
        run_cmd(keytool_cmd, "Generating debug keystore")

    # 8. Sign APK with apksigner
    final_apk = os.path.join(PROJECT_DIR, 'LegecloPlayer.apk')
    apksigner_cmd = [
        APKSIGNER, 'sign',
        '--ks', keystore_path,
        '--ks-key-alias', 'androiddebugkey',
        '--ks-pass', 'pass:android',
        '--key-pass', 'pass:android',
        '--out', final_apk,
        aligned_apk
    ]
    run_cmd(apksigner_cmd, "Signing APK with apksigner")

    apk_sz = os.path.getsize(final_apk) / (1024 * 1024)
    print("==================================================")
    print(">> Build SUCCESSFUL!")
    print(f"Output APK: {final_apk}")
    print(f"APK Size:   {apk_sz:.2f} MB")
    print("==================================================")

if __name__ == '__main__':
    build()
