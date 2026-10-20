# Instalación y operación en red local (sin Internet)

## 1. Topología
```
 [PC servidor del almacén]  ← API :3000 + panel web + PostgreSQL (solo local)
        │  mismo router / Wi-Fi
 ├── PCs y tabletas del almacén  → navegador: http://IP-SERVIDOR:3000
 └── Teléfonos Android (restaurantes) → app APK → http://IP-SERVIDOR:3000
```
Solo el puerto de la API (3000 por defecto) debe ser alcanzable por los clientes. **PostgreSQL (5432) no se expone**: escucha solo en `localhost` (o en la red interna de Docker).

## 2. IP fija para el servidor
Reserva una IP en el router (reserva DHCP por dirección MAC) para la PC servidor, p. ej. `192.168.1.100`; así la app no deja de conectarse cuando el router reinicie. Alternativa: IP estática en el sistema operativo fuera del rango DHCP. Obtén la IP actual con `ipconfig` (Windows) / `ip addr` (Linux); el servidor también la imprime al arrancar.

## 3. Instalación — opción A: Docker Compose
Requisitos: Docker. (Instala las imágenes mientras haya Internet; después funciona sin conexión.)
```bash
cp .env.docker.example .env        # edita POSTGRES_PASSWORD y JWT_SECRET (openssl rand -base64 48)
docker compose up -d --build
docker compose exec app node -e "require('./dist/scripts/bootstrapAdmin.js')"   # crea el administrador (muestra una contraseña temporal)
```
Las migraciones se aplican solas al arrancar.

## 4. Instalación — opción B: sin Docker
Requisitos: Node.js 20+, PostgreSQL 16.
```bash
createuser -P almacen && createdb -O almacen almacen
cd server && cp .env.example .env     # DATABASE_URL, JWT_SECRET (32+ caracteres), NODE_ENV=production
npm ci && npm run build
(cd ../web && npm ci && npm run build)           # el servidor sirve web/dist
ADMIN_USERNAME=admin npm run bootstrap-admin     # imprime la contraseña temporal una sola vez (o define ADMIN_PASSWORD)
npm start                                        # escucha en 0.0.0.0:3000
```
Para ejecutarlo como servicio usa systemd (Linux), NSSM o el Programador de tareas (Windows) o `pm2`.

### Datos de demostración (solo pruebas)
`npm run seed:demo` en `server/` carga productos, restaurantes, pedidos y usuarios `*.demo` con la contraseña **temporal** `Demo-Temporal-2026`. Se niega a ejecutarse con `NODE_ENV=production` (salvo `ALLOW_DEMO_SEED=true`) o si la base ya tiene productos. **Nunca** lo uses en producción.

## 5. Firewall
Permite únicamente el puerto TCP de la API desde la red local.
- Windows (PowerShell admin): `New-NetFirewallRule -DisplayName "Almacen API" -Direction Inbound -Protocol TCP -LocalPort 3000 -Profile Private -Action Allow` (marca la red Wi-Fi como "Privada").
- Ubuntu/ufw: `sudo ufw allow from 192.168.1.0/24 to any port 3000 proto tcp`
- No abras 5432 ni redirijas puertos del router hacia Internet.
El puerto se cambia con `PORT` en `.env` (o `APP_PORT` en Docker); la app Android pide la dirección completa (`http://IP:PUERTO`).

## 6. Seguridad en HTTP local
Sin HTTPS, el tráfico (incluido el token de sesión) viaja sin cifrar dentro de la red: cualquiera conectado a ese Wi-Fi podría espiarlo.
Mitigaciones: Wi-Fi con WPA2/WPA3 y contraseña robusta; red de invitados separada de la del almacén; no usar el servidor con Internet público; contraseñas robustas (hash bcrypt, bloqueo tras 5 intentos fallidos y límite de intentos por IP). La aplicación Android permite HTTP en claro únicamente porque es necesario en LAN. Si más adelante se requiere HTTPS: certificado interno (p. ej. mkcert/CA propia) detrás de un proxy inverso (Caddy/nginx) y la app apuntando a `https://`.

## 7. Respaldo y restauración
- Manual: `cd server && npm run backup` (usa `pg_dump`, conserva los últimos `BACKUP_KEEP`=14 en `BACKUP_DIR`). Con Docker: `./scripts/backup.sh`.
- Automático: cron `0 2 * * * /ruta/scripts/backup.sh` o Programador de tareas de Windows con `npm run backup`.
- Copia periódicamente la carpeta `backups/` a un disco externo.
- Restaurar (detén el servidor primero): `cd server && npm run restore -- ../backups/almacen-FECHA.dump --yes`.
- **Prueba la restauración** al menos una vez en un equipo de pruebas.

## 8. Zona horaria
Ajústala en *Configuración* (por defecto `America/Mexico_City`). La base guarda UTC; las pantallas muestran la hora configurada.

## 9. Compilar la app Android
Ver `mobile/README.md` (APK con EAS o localmente con Gradle).

## 10. Pruebas automatizadas
`cd server && npm test` (requiere una base `almacen_test`; se recrea en cada ejecución — usa `TEST_DATABASE_URL`).
