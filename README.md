# Mineshaft Internal

**Mineshaft Internal** is the server-side component of Mineshaft. It is a Minecraft Paper plugin that exposes an authenticated HTTP API for interacting with a Minecraft server.

It allows compatible clients, including the Mineshaft Haxe SDK and Mineshaft CLI, to query server information and execute Minecraft commands remotely.

## Features

- HTTP API for Mineshaft clients.
- Bearer-token authentication.
- Server and plugin status endpoint.
- Minecraft command execution.
- Online player information.
- Installed plugin information.
- Datapack directory inspection.
- Minecraft, Paper, and plugin version information.
- General server information.

Available features depend on the installed plugin version.

## Requirements

- A Minecraft server running a compatible version of [Paper](https://papermc.io/).
- Java compatible with your Paper server version.
- Mineshaft Internal compiled as a plugin JAR.
- A compatible Mineshaft Haxe SDK or Mineshaft CLI, if you want to use those clients.

## Installation

1. Download or compile the Mineshaft Internal plugin JAR.
2. Stop your Minecraft server.
3. Place the JAR in the server's `plugins` directory.
4. Start the server.
5. Check the server console for plugin startup messages.
6. Review the generated configuration file before enabling remote access.

Example server structure:

```text
server/
├── plugins/
│   └── MineshaftInternal.jar
├── world/
├── world_nether/
└── server.properties
```

The exact JAR filename depends on your build configuration.

## Configuration

Mineshaft Internal uses a configuration file to determine its HTTP host, port, and authentication token.

Example configuration:

```yaml
host: 127.0.0.1
port: 8765
token: "K"
```

The default development token is `K`.

**Security warning:** `K` is a development default, not a secure production secret. Keep the API bound to localhost when using this default. If remote access is required, use a strong, private token and restrict network access with appropriate firewall rules. Never commit real production credentials to a public repository.

The actual configuration keys and defaults must match the version of Mineshaft Internal you install.

## HTTP API

The default local API address is:

```text
http://127.0.0.1:8765
```

All API endpoints require authentication using a Bearer token.

Example request:

```http
GET /v1/status HTTP/1.1
Host: 127.0.0.1:8765
Authorization: Bearer K
```

## Endpoints

### `GET /v1/status`

Checks whether Mineshaft Internal is responding.

Example response:

```json
{
  "version": "0.3.0",
  "ok": true,
  "name": "MineshaftInternal"
}
```

### `POST /v1/command`

Submits a Minecraft command for execution by the server.

Example request:

```http
POST /v1/command HTTP/1.1
Host: 127.0.0.1:8765
Authorization: Bearer K
Content-Type: application/json

{
  "command": "say Hello from Mineshaft!"
}
```

Commands must not start with `/`. The plugin validates the request and schedules command execution through the Bukkit console sender.

A successful request acceptance does not necessarily mean that the command itself succeeded. The endpoint does not guarantee that actual console output is returned.

### `GET /v1/extras/players`

Returns information about currently online players.

### `GET /v1/extras/plugins`

Returns information about installed plugins, including their names, versions, and enabled status.

### `GET /v1/extras/datapacks`

Inspects datapack directories associated with world folders.

Directory inspection does not necessarily establish whether each datapack is enabled in-game.

### `GET /v1/extras/version`

Returns server version information, including Minecraft, Paper, and Mineshaft Internal versions.

### `GET /v1/extras/info`

Returns general server information, such as online player count, maximum player count, world names, and plugin version.

## Connecting from Haxe

Use the Mineshaft Haxe SDK to communicate with the plugin:

```haxe
import mineshaft.Connect;

class Main {
    static function main():Void {
        var connection = new Connect(
            "http://127.0.0.1:8765",
            "K"
        );

        if (connection.pair()) {
            trace("Connected to Mineshaft Internal");
            connection.command("say Hello from Haxe!");
        }
    }
}
```

The SDK and plugin must be compatible with each other.

## Building from source

The Java source code is maintained in the `internal` directory of the Mineshaft project.

```text
Mineshaft/
└── internal/
    └── src/
        └── main/
            └── java/
                └── dev/
                    └── mineshaft/
                        └── internal/
                            └── MineshaftInternal.java
```

Compile the plugin using the build configuration included in the project. The output should be a JAR suitable for installation in the Paper server's `plugins` directory.

Build requirements and commands depend on the project's configured build system.

## Security considerations

Mineshaft Internal provides access to server commands, which may grant substantial control over a Minecraft server.

- Do not expose the HTTP API directly to the public internet without appropriate protection.
- Use a strong token for remote deployments.
- Restrict network access to trusted clients.
- Do not publish live server credentials.
- Keep the plugin and its dependencies updated.
- Run the server with only the permissions and system access it needs.

## Troubleshooting

**The connection is refused**

- Confirm that the Paper server is running.
- Check that Mineshaft Internal loaded successfully.
- Verify the configured host and port.
- Check firewall rules and network connectivity.

**The API returns `401 Unauthorized`**

- Verify that the client token matches the plugin configuration.
- Confirm that the request includes the `Authorization: Bearer <token>` header.

**An endpoint returns `404 Not Found`**

- Verify that the installed plugin version supports that endpoint.
- Check that the request path
