# PavoKick TV

Aplicación Android nativa para ver directos de KICK en televisores Android antiguos.

## Versión 0.3.0

- Interfaz nativa horizontal pensada para televisor y control remoto.
- Entrada directa de nombre de canal.
- Favoritos persistentes (hasta 12 canales).
- Historial local de los últimos 8 canales abiertos.
- Mantener pulsado un canal con el control remoto para quitarlo de favoritos o del historial.
- Abre únicamente el reproductor oficial `player.kick.com`, no la página completa del canal.
- Botón de pantalla completa y modo inmersivo.
- Cierra y libera el WebView al volver a la pantalla principal.

## Compatibilidad

- Android mínimo: 5.1 (API 22).
- Java 8.
- Interfaz horizontal; referencia de prueba: 1280x720 y 1 GB de RAM.
- No se garantiza que el reproductor moderno de KICK funcione en el WebView antiguo del televisor; debe probarse en el dispositivo físico.

## Compilación

GitHub Actions compila el APK debug y lo publica como artefacto descargable.


Current version: 0.4.1. The player controls now hide together and can be restored with the TV remote.
