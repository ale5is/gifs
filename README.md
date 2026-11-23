# PavoKick TV

Aplicación Android nativa para ver directos, emisiones anteriores y clips públicos de KICK en televisores Android antiguos.

## Versión 0.6.0

- Interfaz horizontal pensada para televisor y control remoto.
- Búsqueda de canales por nombre.
- Reproducción de directos públicos mediante HLS y ExoPlayer.
- Opción «VER CANAL» para consultar emisiones anteriores y miniaturas.
- Apartado «CLIPS» para consultar y reproducir clips públicos del canal.
- Reproducción de emisiones anteriores y clips en el reproductor nativo.
- Calidad de video guardada globalmente y reutilizada en directos, VOD y clips.
- Controles de volumen en pantalla y compatibilidad con las teclas de volumen del mando.
- Selector de velocidad: 0,5x, 0,75x, 1x, 1,25x, 1,5x y 2x.
- Favoritos persistentes y últimos canales visitados.

## Compatibilidad

- Android mínimo: 5.1 (API 22).
- Java 8.
- Interfaz horizontal; referencia de prueba: 1280x720 y 1 GB de RAM.
- No requiere iniciar sesión para consultar canales y contenido público.
- Historial VOD y clips usan endpoints web no documentados de KICK; pueden cambiar o dejar de funcionar sin aviso. Los videos privados o exclusivos para suscriptores no se pueden reproducir sin autorización.

## Compilación

GitHub Actions compila el APK debug y lo publica como artefacto descargable.
