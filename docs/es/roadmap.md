# Hoja de ruta

Los elementos describen trabajo previsto, sin prometer funciones disponibles ni fechas.

## Base actual

Receta y activación del buzón; guías de plata/oro; vínculo único; desvinculación explícita; nombre y radio; retrato, salud, etapa de crecimiento y armadura; información de collar/colgante; persistencia y red validadas en el servidor. El núcleo común y ambos adapters tienen comprobaciones automáticas de build y contratos.

## Próximos hitos

1. Validar la versión actual en cliente y servidor dedicado por objetivo: guardado/reinicio, propiedad, entidades descargadas y buzones superpuestos.
2. Estabilizar API y límites conservando los IDs persistidos.
3. Implementar acciones tipadas y autorizadas por el servidor para llamar, volver y seguir, además de modos Quedarse en Casa / Recorrer el Área / Libre.
4. Mejorar descubrimiento con búsqueda, filtros, ubicación y accesibilidad.

## Expansiones opcionales

- Field Guide: escáner de criaturas de solo lectura y después diario de campo.
- Habitats: caseta de perro, cama de gato, percha de loro y establo.
- Accessories: collares, etiquetas, rastreadores, cosméticos y brújula con última ubicación conocida.
- Care: cuencos, agua, premios, cepillado y juguetes; la falta de cuidado no debe matar mascotas.
- Training: silbato, clicker y objetivos, tras definir permisos y límites de comportamiento.

## Política de versiones

Mantener paridad entre 1.21 y 26.2. Las versiones nuevas requieren adapter, dependencias, CI y aceptación de juego propios. Las extensiones mantienen datos propios y deben poder eliminarse sin dañar los vínculos principales.
