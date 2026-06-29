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
  controllers/ChatController.scala → Action.async(parse.json) → /api/chat
  services/ChatService.scala      → delega a PythonClient
  clients/PythonClient.scala      → WS POST a python-engine:8000/chat
  dto/
    ChatRequest.scala             → case class ChatRequest(message: String)
    JsonFormats.scala             → Json.format[ChatRequest]
Dockerfile                        → CMD ["sbt","run"]  (no prod-ready)
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

play.filters.hosts {
  allowed = ["localhost:9000", "127.0.0.1:9000", "scala-backend:9000"]
}
```

- `python.engine.url` overrideable por env `PYTHON_ENGINE_URL`.
- `play.filters.hosts.allowed` sólo permite esos tres hosts. Si expones Scala a otro dominio, agregarlo aquí.

## 5. Endpoints

| Método | Ruta | Body | Descripción |
|---|---|---|---|
| POST | `/api/chat` | `{"message": "..."}` | Passthrough a Python `/chat` |

**Flujo**:
1. `ChatController.chat` recibe `JsValue`, valida con `validate[ChatRequest]`.
2. Si inválido → `BadRequest(Json.obj("message" -> "Invalid request"))` (NOTA: no sigue el envelope `{success, message, data}` — ver gotcha).
3. Válido → `chatService.processMessage(message)` → `PythonClient.sendMessage` → `WS POST $pythonUrl/chat` con `{"message": message}`.
4. Devuelve el JSON de Python **sin reenvolver** (tal cual llega).

## 6. Gotchas (no reintroducir)

1. **Sin CORS habilitado**. `application.conf` sólo define `play.filters.hosts.allowed` (filtro de Host header, no CORS). Funciona en dev por el proxy de Vite, pero en prod hay que habilitar `play.filters.cors.CORSFilter` y agregar `play.filters.enabled += "play.filters.cors.CORSFilter"` + configuración de `play.filters.cors.allowedOrigins`.
2. **`allowed` hosts** no incluye el dominio público ni `localhost:5173`.
3. **Sin `/health`** propio. La ruta `GET /` (HomeController) está comentada y además el `HomeController` no existe en `app/controllers/` → si se descomenta rompe la build.
4. **Manejo de errores del WS ausente**: si Python cae o tarda, `PythonClient.sendMessage` propaga el Future fallido a `ChatController` → el cliente recibe 500 genérico sin `success: false`. Falta `.recover`/`.recoverWith` o `Future.successful(InternalServerError(...))` con envelope.
5. **Dockerfile usa `sbt run`** (dev mode). Para prod: `sbt stage` + `target/universal/stage/bin/<dist>` sobre JRE-only.
6. **`BadRequest(Json.obj("message"->"..."))`** en `ChatController` no sigue el contrato `{success, message, data}` del resto del sistema.
7. **Sin timeout en WS**: `ws.url(...).post(...)` sin `.withRequestTimeout(...)` → si Python cuelga, Scala también.
8. **Sin tests** en `test/` pese a tener scalatestplus-play disponible.
9. **Sin logs**: no hay logging estructurado (sólo logback default). Considerar `Logger` de Play para errores del WS.

## 7. Cómo extender

- **Nuevo endpoint»: fila en `conf/routes` → `METHOD /path controllers.XController.method`. Crear controller en `app/controllers/`. Responder con envelope (`Ok(Json.obj("success"->true, "message"->..., "data"->...))`).
- **Nuevo DTO**: `case class` en `dto/`, `Json.format` en `dto/JsonFormats.scala`.
- **Nuevo cliente externo**: clase en `clients/` con `@Inject()(ws: WSClient, config: Configuration)`, URL configurable por `application.conf` y env var.
- **Habilitar CORS**: añadir a `application.conf`:
  ```
  play.filters.enabled += "play.filters.cors.CORSFilter"
  play.filters.cors {
    allowedOrigins = ["http://localhost:5173", "https://tu-dominio"]
  }
  ```

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
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.11_2.13.18
WORKDIR /app
COPY . .
EXPOSE 9000
CMD ["sbt", "run"]
```

Mejoras pendientes:
- **Multi-stage build**: stage1 `sbt stage`, stage2 JRE-slim copiando `target/universal/stage/`.
- Pinneo por digest.
- `HEALTHCHECK` con `curl localhost:9000/health` (cuando exista el endpoint).

## 10. Reglas para agentes (específicas)

- Mantén el contrato `{success, message, data}` en nuevos endpoints (no repetir el `BadRequest(Json.obj("message"->...))` del `ChatController`).
- Toda URL externa configurable; nunca hardcodes URLs.
- Todo handler asíncrono debe `.recover`/`.recoverWith` para devolver envelope de error consistente.
- No agregues dependencias sin verificar `build.sbt` y confirmar con el usuario.
- Comenta la lógica only si no es obvia (Play es declarativo, normalmente no hace falta).
- Si añades un endpoint público en prod, recuerda el CORS y el `play.filters.hosts.allowed`.
- Modifica este archivo y el del padre si cambias rutas, paquetes o configuración.