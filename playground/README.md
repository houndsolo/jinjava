# Disposable Jinjava IP playground

Requirements: Docker Desktop (or Docker Engine with the Compose plugin). No Java,
Maven, Python, Node, or Git installation is needed on your computer.

Download the ZIP of this branch from GitHub and extract it. Open a terminal in
the extracted repository's `playground` folder and run:

```sh
docker compose up --build
```

The first build downloads the Java/Maven images and dependencies and can take a
few minutes. When you see "Jinjava IP playground is listening on port 8080",
open `http://localhost:8080` in your browser.

Load the IPv4, IPv6, or CLI example, edit the template and JSON variables, and
click **Render template**. Paste your own multiline CLI templates too. Rendering
uses the actual Jinjava library, not an emulation.

This playground branch is based on `houndsolo/jinjava`'s
`feat/ipaddr-index-wildcard`, commit `9718774d03024745e80197153698d8b4b04ba88d`.
The container builds the library source from the repository alongside this folder.
It adds numeric indexes and wildcard masks only. Existing Jinjava filters retain
their original behavior. An indexed address includes the prefix; chain
`| ipaddr('address')` to remove it.

This builds a disposable playground, not a release: the Docker build compiles
the library without running its test suite or release checks. The branch itself
passed clean Maven verification before this playground was created.

## Stop and remove

Press Ctrl+C to stop. Then remove this project's container and built image:

```sh
docker compose down --rmi local
```

Delete the extracted folder and ZIP when finished. There are no host Java
installations, named volumes, or project data to clean up. Docker may retain the
shared base images and build cache; you can remove those in Docker Desktop if
you no longer need them.

If port 8080 is already used, create a `.env` file in this folder containing
`PORT=8081` and start again. Then open `http://localhost:8081`.
