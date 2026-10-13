# ADR-0003: JWT de acceso en memoria y refresh token rotativo en cookie

**Estado:** Aceptada
**Fecha:** 2026-10-13

## Contexto

El frontend es una SPA en Angular (Vercel) y la API corre en otro proveedor (Render). Los tokens tienen que resistir XSS y CSRF, las sesiones tienen que poder revocarse, y todo debe funcionar en Safari, que bloquea las cookies de terceros.

## Decisión

- **Access token:** JWT HS256 de 15 minutos, devuelto en el cuerpo. La SPA lo guarda en memoria, nunca en `localStorage`, y lo envía como `Authorization: Bearer`. La API lo valida por firma, sin consultar la base de datos.
- **Refresh token:** valor aleatorio opaco de 256 bits en una cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, con 7 días de vida. En la base de datos solo se guarda su hash SHA-256.
- **Rotación con detección de reutilización:** cada refresh entrega un token nuevo y marca el anterior como usado. Si llega un token ya usado, se revoca toda la familia: o lo robaron o se repitió una petición.
- **Mismo origen:** la SPA llama a `/api/*` en su propio dominio. En producción un rewrite de Vercel lo envía a Render, y en desarrollo lo hace el proxy de Angular. La cookie es de primera parte, así que no hace falta CORS y Safari no la bloquea.
- **Contraseñas:** BCrypt mediante `DelegatingPasswordEncoder`, lo que permite migrar de algoritmo sin invalidar los hashes existentes. El login hace el mismo trabajo exista o no el usuario, para no revelar qué gamertags existen.

## Opciones consideradas

| | JWT en `localStorage` | Sesión de servidor con cookie | Proveedor externo (Keycloak, Auth0) | JWT en memoria + refresh en cookie (elegida) |
|---|---|---|---|---|
| XSS | Un script puede robar el token | Protegida | Depende | El refresh es inaccesible; el access token dura 15 minutos |
| CSRF | No aplica | Requiere tokens CSRF | Depende | `SameSite=Strict` en la única cookie |
| Revocación | No hay | Inmediata | Sí | Por familia, en el próximo refresh |
| Costo y operación | Nulo | Estado en el servidor | Otro servicio que mantener o pagar | Nulo |

## Consecuencias

- Revocar una sesión tarda como máximo la vida del access token (15 minutos) en surtir efecto.
- Dos pestañas que renuevan a la vez pueden disparar la detección de reutilización. La SPA debe centralizar el refresh en una sola promesa compartida.
- El login con Discord (fase siguiente) emitirá estos mismos tokens después del flujo OAuth.
