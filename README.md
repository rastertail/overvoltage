# Overvoltage

A Discord bot for playing tunes from the HVSC.
Built with [JSIDPlay2](https://haendel.ddns.net/~ken/) and [JDA](https://github.com/DV8FromTheWorld/JDA).

## [Invite](https://discord.com/api/oauth2/authorize?client_id=889686208786104341&permissions=36700160&scope=bot%20applications.commands)

## Usage

* `/play <search query>` - Play a tune from the HVSC
* `/leave` - Leave the voice channel

## Development and deployment

Overvoltage exists as a [Nix](https://nixos.org/) flake, so to start, install Nix with flakes support.

To develop locally, clone the repository and use the `nix develop` command.
The bot expects the environment variables `HVSC_PATH`, `DATA_DIR`, and `BOT_TOKEN` to be set.
`HVSC_PATH` is automatically set when entering the development shell, and a `.env` file is sourced where you can specify the other two variables.
The bot can then be run with `mvn compile exec:java`.

To deploy, first build the Docker container for the bot.
This can be done either with `nix build .#container` within the repository, or with `nix build github:rastertail/overvoltage#container` anywhere.
Next, install the image into Docker with `docker load < ./result`.
Finally, you must manually specify a tempdir for the container with `--tmpfs /tmp:exec`, pass in the `BOT_TOKEN` environment variable, and optionally mount a volume to `/var/overvoltage` to persist the SID search index.

## Legal

Overvoltage is licensed under the GNU General Public License Version 3.
See the LICENSE file for details.
