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
