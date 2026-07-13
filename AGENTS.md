# AGENTS.md — diagnostico-scalaBackendAPI

API gateway Scala (Play). Escucha en :9000. Lee primero el `AGENTS.md` del repo padre para contexto global.

## 1. Stack

- **Scala 2.13.18** sobre **JDK 21** (imagen `sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.11_2.13.18`).
- **Play Framework 3.0.11** (plugin sbt en `project/plugins.sbt`).
- **sbt 1.12.11** como build tool.
- **Guice** para DI (`@Singleton`, `@Inject`).
- **WSClient** (`ws` de Play) para llamadas HTTP a Python.
- Test: `scalatestplus-play 7.0.2` (aún sin tests definidos).

## 2. Estructura

```
build.sbt                         → deps: guice, ws, scalatestplus-play % Test
project/
  build.properties                → sbt 1.12.11
  plugins.sbt                     → sbt-plugin Play 3.0.11
conf/
  application.conf                → python.engine.url, play.filters.hosts.allowed
  routes                          → POST /api/chat → ChatController.chat
  logback.xml / messages
app/
  controllers/
    ChatController.scala   → Action.async(parse.json) → /api/chat
    HealthController.scala → GET /health
  services/ChatService.scala      → delega a PythonClient
  clients/PythonClient.scala      → WS POST a python-engine:8000/chat
  dto/
    ChatRequest.scala             → case class ChatRequest(message: String)
    JsonFormats.scala             → Json.format[ChatRequest]
Dockerfile                        → multi-stage: sbt stage + JRE-only
test/                             → vacío (sin specs)
```

## 3. Convenciones Scala

- Indentación: **2 espacios**.
- Estructura en paquetes: `controllers` / `services` / `clients` / `dto`. No mezclar responsabilidades.
- Controladores: `extends BaseController`, anotados `@Singleton` + `@Inject()` con `ControllerComponents`.
- Servicios: `@Singleton` + `@Inject()` con sus dependencias. Futures explícitos.
- Clientes: usan `WSClient` y leen URLs de `Configuration.get[String](...)`.
- DTOs como `case class` + `Json.format[...]` en `dto/JsonFormats.scala`. No poner lógica en DTOs.
- Rutas declarativas en `conf/routes` (no routing programático).
- `ExecutionContext` inyectado implícito en controladores/servicios.

## 4. Configuración

`conf/application.conf`:
```
python.engine.url = "http://localhost:8000"
python.engine.url = ${?PYTHON_ENGINE_URL}

play.http.secret.key = "UY4Bm9zPx7Lq2Vk8Rt5Nw3Jf6Hd1CsAe0GyObnMpXiQuSvWaKc"
play.http.secret.key = ${?APPLICATION_SECRET}

play.filters.hosts {
  allowed = ["localhost:9000", "127.0.0.1:9000", "scala-backend:9000"]
}

play.filters.enabled += "play.filters.cors.CORSFilter"

play.filters.cors {
  allowedOrigins = ["http://localhost:5173", "http://localhost:9000"]
  allowedHttpMethods = ["GET", "POST", "OPTIONS"]
  allowedHttpHeaders = ["Content-Type", "Accept"]
}
```

- `python.engine.url` overrideable por env `PYTHON_ENGINE_URL`.
- `play.http.secret.key` tiene valor hardcodeado (ver gotcha vigente) + override por `${?APPLICATION_SECRET}`.
- `play.filters.hosts.allowed` sólo permite esos tres hosts. Si expones Scala a otro dominio, agregarlo aquí.
- CORS ya habilitado con `allowedOrigins` para `localhost:5173` y `localhost:9000`.

## 5. Endpoints

| Método | Ruta | Body | Descripción |
|---|---|---|---|
| POST | `/api/chat` | `{"message": "..."}` | Passthrough a Python `/chat` |

**Flujo**:
1. `ChatController.chat` recibe `JsValue`, valida con `validate[ChatRequest]`.
2. Si inválido → `BadRequest` con envelope `{success: false, message, data: null}`.
3. Válido → `chatService.processMessage(message)` → `PythonClient.sendMessage` → `WS POST $pythonUrl/chat` con `{"message": message}`.
4. Devuelve el JSON de Python **sin reenvolver** (tal cual llega).
5. Si Python falla, `PythonClient` tiene `.recover` que devuelve envelope de error; `ChatController` también tiene `.recover` como respaldo.

## 6. Gotchas (no reintroducir)

### Resueltos (no reintroducir)

1. ~~**Sin CORS**~~ — `application.conf` ya habilita `play.filters.cors.CORSFilter` con `allowedOrigins` para `localhost:5173` y `localhost:9000`.
2. ~~**`allowed` hosts sin `localhost:5173`**~~ — ya incluido en `play.filters.cors.allowedOrigins`.
3. ~~**Sin `/health`**~~ — `HealthController` implementa `GET /health` con envelope correcto.
4. ~~**Manejo de errores WS ausente**~~ — `PythonClient` tiene `.recover` y timeout de 60s; `ChatController` también tiene `.recover`.
5. ~~**Dockerfile usa `sbt run`**~~ — multi-stage build: `sbt stage` + JRE-only (`eclipse-temurin:21-jre`).
6. ~~**`BadRequest` sin envelope**~~ — ya responde `{success: false, message, data: null}`.
7. ~~**Sin timeout en WS**~~ — `PythonClient` usa `.withRequestTimeout(60.seconds)`.

### Vigentes

1. **Sin tests** en `test/` pese a tener scalatestplus-play disponible.
2. **Sin logging estructurado** — sólo logback default. Considerar `Logger` de Play para errores del WS.
3. **Secret key hardcodeado** en `application.conf` (`play.http.secret.key = "UY4Bm9z..."`) — debería eliminarse la línea fija y dejar sólo el override por env `${?APPLICATION_SECRET}`.

## 7. Cómo extender

- **Nuevo endpoint**: fila en `conf/routes` → `METHOD /path controllers.XController.method`. Crear controller en `app/controllers/`. Responder con envelope (`Ok(Json.obj("success"->true, "message"->..., "data"->...))`).
- **Nuevo DTO**: `case class` en `dto/`, `Json.format` en `dto/JsonFormats.scala`.
- **Nuevo cliente externo**: clase en `clients/` con `@Inject()(ws: WSClient, config: Configuration)`, URL configurable por `application.conf` y env var.

## 8. Verificación

| Comando | Uso |
|---|---|
| `sbt compile` | typecheck/compila |
| `sbt run` | dev server en :9000 |
| `sbt stage` | build prod (genera `target/universal/stage/`) |
| `sbt test` | scalatestplus-play (vacío por ahora) |
| `curl -X POST http://localhost:9000/api/chat -H "Content-Type: application/json" -d '{"message":"fiebre y tos"}' \| jq` | smoke test (requiere Python↑) |

Sin scalafmt configurado (preguntar al usuario antes de invocar).

## 9. Docker

```dockerfile
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.11_2.13.18 AS builder
WORKDIR /app
COPY project project
COPY build.sbt .
RUN sbt update
COPY . .
RUN sbt stage

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/target/universal/stage .
EXPOSE 9000
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:9000/health || exit 1
CMD ["bin/diagnostico-scalabackendapi"]
```

Mejora pendiente: pinnear imágenes por digest SHA para builds 100% reproducibles.

## 10. Reglas para agentes (específicas)

- Mantén el contrato `{success, message, data}` en nuevos endpoints.
- Toda URL externa configurable; nunca hardcodes URLs.
- Todo handler asíncrono debe `.recover`/`.recoverWith` para devolver envelope de error consistente.
- No agregues dependencias sin verificar `build.sbt` y confirmar con el usuario.
- Comenta la lógica only si no es obvia (Play es declarativo, normalmente no hace falta).
- Si añades un endpoint público en prod, recuerda el CORS y el `play.filters.hosts.allowed`.
- Modifica este archivo y el del padre si cambias rutas, paquetes o configuración.