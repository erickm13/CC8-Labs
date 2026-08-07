# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo collects the labs for the **CC8** university course. Each `labN/` directory is an independent, self-contained Java project — there is no shared build at the repo root. Always `cd` into the specific lab directory before building or running.

- `lab1/` — TCP socket exercise. Single class `Client.java` that parses `PORT`/`PROTOCOL`/`IP` CLI arguments. No Makefile: compile with `javac Client.java`, run with `java Client PORT <p> PROTOCOL <proto> IP <ip>` (`--help` for usage).
- `lab2/` — Multithreaded HTTP web server (the main project). Has its own **`lab2/CLAUDE.md`** with the full command list and architecture — read it before working in `lab2/`.

## lab2 quick reference

```bash
cd lab2
make                              # compile all classes
make run                          # run (defaults: port 1000, 2 threads, 5s delay)
make run ARGS="-port 8080 -threads 4 -delay 3"
make clean                        # remove *.class and logs/*
```

Requires JDK 21+ and Make.

Key constraints (see `lab2/CLAUDE.md` and `lab2/README.md` for detail):
- **Only modify** `Request.java`, `Response.java`, and `Makefile`. Do **not** modify `Server.java`, `ThreadServer.java`, or `FormatterWebServer.java`.
- The `Request.getData(...)` and `Response.sendData(...)` signatures are fixed contracts consumed by `ThreadServer`.
- New `.java` files must be added to the `CLASSES` list in `lab2/Makefile`, or they won't compile.
- Static content is served from `lab2/www/` relative to the working directory `make run` is launched from.

## Notes

- `lab2/__MACOSX/`, `.DS_Store`, and `logs/*.log` files are macOS/runtime cruft — ignore them; they are not source.
- The codebase and its comments/docs are written in Spanish; match that language when editing lab-facing text.
