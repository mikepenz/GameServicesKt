"""Build relocatable JNI resources using the host Xcode toolchain."""
import pathlib
import shutil
import subprocess
import sys

arch, native, source, java, destination = sys.argv[1:]
output = pathlib.Path(destination)
output.mkdir(parents=True, exist_ok=True)
library = output / "libgs_gamecenter.dylib"
shutil.copyfile(native, library)
subprocess.run(["xcrun", "install_name_tool", "-id", "@rpath/libgs_gamecenter.dylib", str(library)], check=True)
subprocess.run(["codesign", "--force", "--sign", "-", str(library)], check=True)
subprocess.run([
    "xcrun", "clang++", "-std=c++17", "-dynamiclib", "-arch", arch, "-mmacosx-version-min=12.0",
    f"-I{java}/include", f"-I{java}/include/darwin", source,
    f"-L{output}", "-lgs_gamecenter", "-Wl,-rpath,@loader_path",
    "-Wl,-install_name,@rpath/libgs_gamecenter_jni.dylib",
    "-o", str(output / "libgs_gamecenter_jni.dylib"),
], check=True)
