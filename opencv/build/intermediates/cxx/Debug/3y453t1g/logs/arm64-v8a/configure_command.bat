@echo off
"C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\libcxx_helper" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=21" ^
  "-DANDROID_PLATFORM=android-21" ^
  "-DANDROID_ABI=arm64-v8a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a" ^
  "-DANDROID_NDK=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\ndk\\25.1.8937393" ^
  "-DCMAKE_ANDROID_NDK=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\ndk\\25.1.8937393" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\ndk\\25.1.8937393\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Program Files\\Android\\android-sdk_r24.4.1-windows\\android-sdk-windows\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\build\\intermediates\\cxx\\Debug\\3y453t1g\\obj\\arm64-v8a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\build\\intermediates\\cxx\\Debug\\3y453t1g\\obj\\arm64-v8a" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BC:\\Users\\omen of Hansen\\Desktop\\workspace\\AN\\front\\opencv\\.cxx\\Debug\\3y453t1g\\arm64-v8a" ^
  -GNinja ^
  "-DANDROID_STL=c++_shared"
