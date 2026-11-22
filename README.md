# PavoKick TV

Aplicación Android nativa para probar KICK en televisores Android antiguos (Android 5.1 / API 22).

## Estado

Prototipo experimental 0.1.0. Permite introducir un canal y abrir el reproductor web de KICK. La compatibilidad del reproductor con WebView de Android 5.1 debe comprobarse en el televisor físico.

## Compilación

El workflow de GitHub Actions compila el APK debug y lo publica como artefacto descargable.

## Requisitos de referencia

- Android mínimo: 5.1 (API 22)
- Interfaz horizontal, control remoto y resolución 1280x720
- Java 8 en el código de la aplicación

No se garantiza que el reproductor moderno de KICK funcione en el WebView antiguo del televisor.
