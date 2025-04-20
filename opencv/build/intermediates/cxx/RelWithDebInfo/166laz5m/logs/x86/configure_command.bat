@echo off
"C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\libcxx_helper" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=21" ^
  "-DANDROID_PLATFORM=android-21" ^
  "-DANDROID_ABI=x86" ^
  "-DCMAKE_ANDROID_ARCH_ABI=x86" ^
  "-DANDROID_NDK=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\ndk\\25.1.8937393" ^
  "-DCMAKE_ANDROID_NDK=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\ndk\\25.1.8937393" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\ndk\\25.1.8937393\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\build\\intermediates\\cxx\\RelWithDebInfo\\166laz5m\\obj\\x86" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\build\\intermediates\\cxx\\RelWithDebInfo\\166laz5m\\obj\\x86" ^
  "-DCMAKE_BUILD_TYPE=RelWithDebInfo" ^
  "-BC:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\.cxx\\RelWithDebInfo\\166laz5m\\x86" ^
  -GNinja ^
  "-DANDROID_STL=c++_shared"
