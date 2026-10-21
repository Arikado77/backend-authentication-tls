# [Práctica-1] Backend Authentication & TLS/SSL — HU-SEC-001

API de práctica con autenticación de usuarios, autorización por roles, JWT firmado y transporte HTTPS. No usa base de datos: las cuentas demo se crean en memoria al iniciar.

## Objetivos

- Autenticar con usuario y contraseña y emitir JWT HS256 de corta duración.
- Proteger rutas con `ROLE_USER` y `ROLE_ADMIN`, con sesiones stateless.
- Responder errores HTTP en JSON y registrar rechazos sin escribir contraseñas ni tokens.
- Servir exclusivamente HTTPS en el perfil local, con TLS 1.2 y 1.3.

## Tecnologías

- Java 17 y Maven Wrapper.
- Spring Boot 4.1.1 (versión declarada en `pom.xml`).
- Spring Security, OAuth2 Resource Server/Nimbus, Spring MVC, Validation y Actuator.
- JUnit y MockMvc para pruebas de seguridad y controladores.

## Arquitectura

El paquete raíz es `com.carlos.backend_auth_tls`, conservado del proyecto generado.

```text
src/main/java/com/carlos/backend_auth_tls/
  config/       JWT, CORS y SecurityFilterChain
  controller/   endpoints de autenticación, público, USER y ADMIN
  dto/          peticiones y respuestas JSON
  exception/    errores de API
  security/     entry point 401 y handler 403
  service/      autenticación y emisión de JWT
```

Las cuentas `user` y `admin` se almacenan en memoria y sus contraseñas se obtienen de `DEMO_USER_PASSWORD` y `DEMO_ADMIN_PASSWORD`. El administrador posee `USER` y `ADMIN`; no hay registro público ni roles definidos por el cliente.

## Preparar HTTPS y secretos locales

El certificado autofirmado y sus secretos no se versionan. `.gitignore` excluye keystores, claves privadas, la carpeta `certs/` y los archivos `.env` locales. Los scripts crean `.env` con secretos aleatorios locales para conservarlos entre sesiones y exportan sus valores al proceso; el archivo está ignorado por Git y debe mantenerse privado. `.env.example` solo documenta nombres y valores marcadores; Spring no carga ese archivo automáticamente.

### PowerShell

Desde la raíz del proyecto, carga el script en la sesión actual (el punto inicial es importante para conservar las variables de entorno):

```powershell
. .\scripts\prepare-local.ps1
.\mvnw.cmd spring-boot:run
```

El script genera (o valida si ya existe) `certs\localhost.p12` con `keytool`, RSA 3072, PKCS12 y SAN `DNS:localhost`/`IP:127.0.0.1`. En la primera preparación crea una clave JWT aleatoria de 256 bits, contraseñas demo y contraseña del keystore y las guarda en el archivo local ignorado `.env`. No sobrescribe certificados existentes. Para consultar localmente las credenciales demo antes de probar el login:

```powershell
$env:DEMO_USER_PASSWORD
$env:DEMO_ADMIN_PASSWORD
```

### Git Bash

Desde la raíz, carga el script en el shell actual:

```bash
source ./scripts/prepare-local.sh
./mvnw spring-boot:run
```

Requiere JDK 17 (`keytool`) y `openssl` en el PATH. Se puede consultar la contraseña de demo en ese shell mediante `printf '%s\n' "$DEMO_USER_PASSWORD"` y `printf '%s\n' "$DEMO_ADMIN_PASSWORD"`.

La aplicación falla al arrancar si falta el secreto JWT, alguna contraseña de demo o, al iniciar HTTPS, la contraseña/keystore PKCS12. No se usan contraseñas predeterminadas.

Para un proceso independiente, configura estas variables en el entorno antes del inicio:

| Variable | Uso |
|---|---|
| `JWT_SECRET_BASE64` | Secreto HMAC Base64 que decodifique a 32 bytes o más |
| `JWT_ISSUER` | Emisor validado en cada JWT (predeterminado `backend-auth-tls`) |
| `JWT_EXPIRATION_SECONDS` | Vigencia (predeterminado 900 segundos) |
| `DEMO_USER_PASSWORD` / `DEMO_ADMIN_PASSWORD` | Contraseñas de las cuentas demo |
| `TLS_KEYSTORE_PASSWORD` | Contraseña del keystore PKCS12 |
| `TLS_KEYSTORE` | Ubicación (predeterminado `file:./certs/localhost.p12`) |
| `SERVER_PORT` | Puerto (predeterminado 8443) |
| `CORS_ALLOWED_ORIGIN` | Único origen permitido (predeterminado `http://localhost:4200`) |

No guardes secretos reales en `.env.example`, en el código fuente ni en el control de versiones.

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| `POST` | `/api/auth/login` | Público |
| `GET` | `/api/public/info` | Público |
| `GET` | `/api/user/profile` | `ROLE_USER` |
| `GET` | `/api/admin/dashboard` | `ROLE_ADMIN` |
| `GET` | `/actuator/health` | Público, detalle restringido |

Ejemplo de login:

```json
{"username":"user","password":"<DEMO_USER_PASSWORD>"}
```

Respuesta (`accessToken` es sensible; no lo registres ni compartas):

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "user",
  "roles": ["USER"]
}
```

Envía `Authorization: Bearer <JWT>` a rutas protegidas. El token está firmado HS256 mediante `JwtEncoder`/`JwtDecoder` de Spring Security y Nimbus; se validan firma, issuer, `exp` y `nbf`. El claim `roles` proviene exclusivamente de las autoridades internas de la cuenta.

### Respuestas de autorización

- **401 Unauthorized**: no se envió un token Bearer válido, el token está vencido/malformado o las credenciales de login son incorrectas.
- **403 Forbidden**: autenticación válida, pero el rol no autoriza el recurso (por ejemplo, `user` en el dashboard admin).
- **400 Bad Request**: JSON inválido o campos requeridos vacíos.
- **404 Not Found**: ruta inexistente.
- **500 Internal Server Error**: error inesperado; la respuesta no contiene trazas ni detalles internos.

Los errores 401/403 tienen formato JSON con timestamp, status, error, message y path. Se registran autenticaciones fallidas y accesos denegados, sin contraseñas ni JWT.

## Pruebas con curl en Windows

`curl -k` es **solo para pruebas locales**: omite la validación de confianza y no es adecuado para producción.

```powershell
curl.exe -k -i https://localhost:8443/api/public/info
curl.exe -k -i https://localhost:8443/api/user/profile
curl.exe -k -i --tlsv1.2 --tls-max 1.2 https://localhost:8443/api/public/info
curl.exe -k -i --tlsv1.3 --tls-max 1.3 https://localhost:8443/api/public/info
```

Login y acceso protegido:

```powershell
$body = @{ username = "user"; password = $env:DEMO_USER_PASSWORD } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri https://localhost:8443/api/auth/login `
  -ContentType "application/json" -Body $body -SkipCertificateCheck
$token = $login.accessToken
curl.exe -k -i -H "Authorization: Bearer $token" https://localhost:8443/api/user/profile
curl.exe -k -i -H "Authorization: Bearer $token" https://localhost:8443/api/admin/dashboard
```

`Invoke-RestMethod -SkipCertificateCheck` requiere PowerShell 7. En Windows PowerShell 5.1, usa `curl.exe` para el login:

```powershell
$body = '{"username":"user","password":"' + $env:DEMO_USER_PASSWORD + '"}'
curl.exe -k -s -H "Content-Type: application/json" -d $body https://localhost:8443/api/auth/login
```

Para confiar en vez de omitir validación, importa **solo el certificado público** como CA/certificado confiable local (o configura un trust store aislado para desarrollo), y confirma SAN `localhost`/`127.0.0.1`, sujeto y huella por un canal local confiable. Nunca confíes en un certificado desconocido solo porque el cliente lo presenta.

## TLS/SSL

El servidor escucha en HTTPS `8443` y limita protocolos a TLS 1.2 y TLS 1.3; TLS permanece habilitado y no se ofrece una opción de configuración para desactivarlo. TLS 1.2 es la versión anterior aún ampliamente compatible, mientras TLS 1.3 simplifica el handshake y elimina algoritmos criptográficos obsoletos. No se configura un conector HTTP adicional: HTTP claro no tiene servicio en el puerto de la aplicación. El certificado de desarrollo es autofirmado, por lo que clientes estándar no confiarán en él de forma predeterminada.

Los errores de handshake ocurren antes de que exista una solicitud HTTP; el servidor no puede devolver un JSON HTTP en esos casos. El registro de handshake depende del proveedor TLS/JVM y la configuración de logging del servidor, y no sustituye la validación del certificado del cliente.

## Validación automatizada

Ejecuta:

```powershell
.\mvnw.cmd test
```

La suite MockMvc verifica endpoint público, ausencia de JWT, login válido e inválido, JWT válido/malformado/expirado, roles USER/ADMIN, JSON inválido, rutas desconocidas y CORS. **Resultado real de `.\mvnw.cmd test`: 14 pruebas, 0 fallos, 0 errores, 0 omitidas.**

Se generó y verificó el certificado local: PKCS12, RSA 3072, SAN `DNS:localhost` e `IP:127.0.0.1`. Se intentó iniciar la aplicación con el depurador Java, pero el proceso no permaneció activo; una consulta posterior confirmó que no había sesión de depuración y `curl.exe` no pudo conectar a `localhost:8443`. Por tanto, HTTPS funcional, negociación TLS 1.2/1.3, rechazo HTTP claro y los flujos JWT por red quedan **pendientes de verificación en ejecución real**; no se presentan como aprobados. Las pruebas anteriores sí verifican autenticación/roles en el entorno simulado MockMvc.

## Buenas prácticas y límites

- Genera una clave HMAC aleatoria de al menos 256 bits; no uses claves demo o compartidas entre entornos.
- Usa TLS con certificados emitidos por una CA confiable fuera del desarrollo local; rota certificados y secretos.
- Mantén tokens fuera de logs, URLs, capturas y almacenamiento inseguro; reduce vigencia si el riesgo lo requiere.
- Las cuentas demo en memoria no son persistentes y no sustituyen un proveedor de identidad ni una base de usuarios de producción.
- El certificado autofirmado no valida identidad ante clientes externos; `-k` elimina esa validación y solo es aceptable en pruebas locales controladas.
