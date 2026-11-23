# PavoKick TV

Aplicación Android nativa para ver directos y emisiones anteriores de KICK en televisores Android antiguos.

## Versión 0.5.0

- Interfaz horizontal pensada para televisor y control remoto.
- Búsqueda de canales por nombre.
- Reproducción de directos públicos mediante HLS y ExoPlayer.
- Opción «VER CANAL» para consultar emisiones anteriores.
- Historial de VOD con título, fecha, duración y visualizaciones.
- Reproducción de emisiones anteriores en el reproductor nativo.
- Favoritos persistentes y últimos canales visitados.
- Controles del reproductor compatibles con el mando: las barras superior e inferior se ocultan juntas y se pueden recuperar con el mando.

## Compatibilidad

- Android mínimo: 5.1 (API 22).
- Java 8.
- Interfaz horizontal; referencia de prueba: 1280x720 y 1 GB de RAM.
- No requiere iniciar sesión para consultar canales y videos públicos.
- El historial y la reproducción VOD dependen de endpoints web no documentados de KICK; pueden cambiar o dejar de funcionar sin aviso. Los videos privados o exclusivos para suscriptores no se pueden reproducir sin autorización.

## Compilación

GitHub Actions compila el APK debug y lo publica como artefacto descargable.
