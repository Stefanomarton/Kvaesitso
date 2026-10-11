{
  description = "Kvaesitso (TickTick fork) dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      buildTools = "37.0.0";
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37.0" "37.1" ];
        buildToolsVersions = [ buildTools "36.0.0" ];
        includeEmulator = false;
        includeNDK = false;
      };
      sdk = "${android.androidsdk}/libexec/android-sdk";
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ pkgs.jdk21 android.androidsdk ];
        ANDROID_HOME = sdk;
        ANDROID_SDK_ROOT = sdk;
        # Maven's aapt2 is dynamically linked and won't run on NixOS
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildTools}/aapt2";
      };
    };
}
