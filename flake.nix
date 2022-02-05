{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system: let
        pkgs = import nixpkgs { inherit system; };
      in rec {
        packages.hvsc = pkgs.fetchzip {
          name = "hvsc-76";
          url = "https://kohina.duckdns.org/HVSC/HVSC_76-all-of-them.7z";

          postFetch = ''
            ${pkgs.p7zip}/bin/7z x $downloadedFile

            mkdir -p $out
            cp -R C64Music/DEMOS $out/DEMOS
            cp -R C64Music/GAMES $out/GAMES
            cp -R C64Music/MUSICIANS $out/MUSICIANS
          '';
          sha256 = "FDJfDqIqC1r84XqF/da06WI05KGVXTgYR0g85c4tljc=";
        };

        packages.overvoltage = let
          overvoltage = (pkgs.buildMaven ./project-info.json);
        in pkgs.stdenv.mkDerivation {
          pname = "overvoltage";
          version = overvoltage.info.project.version;

          src = ./.;
          
          nativeBuildInputs = [ pkgs.maven pkgs.makeWrapper ];
          buildInputs = [ pkgs.jdk ];
          buildPhase = "mvn --offline --settings ${overvoltage.settings} compile";
          installPhase = ''
            mkdir -p $out/share/java
            mkdir -p $out/bin

            mvn --offline --settings ${overvoltage.settings} package
            cp target/*.jar $out/share/java/

            makeWrapper ${pkgs.jre}/bin/java $out/bin/overvoltage \
              --add-flags "-cp '$out/share/java/*' net.rastertail.overvoltage.Overvoltage"
          '';
        };

        packages.container = pkgs.dockerTools.buildLayeredImage {
          name = "overvoltage";
          tag = packages.overvoltage.version;
          contents = [ packages.overvoltage packages.hvsc ];

          config = {
            Cmd = [ "overvoltage" ];
            Env = [
              "HVSC_PATH=${packages.hvsc}"
              "DATA_DIR=/var/overvoltage"
            ];
          };
        };

        defaultPackage = packages.overvoltage;

        devShell = pkgs.mkShell {
          buildInputs = [
            pkgs.jdk pkgs.maven pkgs.java-language-server
          ];

          HVSC_PATH = "${packages.hvsc}";

          shellHook = ''
            set -a
            source .env
            set +a
          '';
        };
      }
    );
}
