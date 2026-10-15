# Arquitectura

## Leer los fuentes

| Componente | Responsabilidad |
|---|---|
| pet-post-common/application | Reglas de vínculo, desvinculación, nombre, radio y consulta autorizada |
| contract/persistence | Snapshots inmutables y versión del esquema |
| contract/network | Datos de solicitud/respuesta independientes de la versión |
| ui/model | Estado de consulta, selección y desplazamiento |
| PetPost | Entrada Fabric, registros, comandos e interacciones |
| PetPostSavedData | NBT en 1.21; Codec en 26.2; esquema lógico compartido |
| PetPostContractMapper | Conversión explícita entre juego y núcleo |
| PetListPayloads | Consulta versionada, nonce, autorización y respuesta |
| PetActionPayloads | Solicitudes de nombre, radio y desvinculación por posición |
| PetHomeFollowGuard / FollowOwnerGoalMixin | Suspensión de seguimiento y recuperación de mascotas cargadas |
| PostalPetClient / PostalPetScreen | Ciclo del menú, snapshots autorizados y retratos reales |
| TerritoryPreview | Partículas temporales del área |

## Del clic a la persistencia

El servidor recibe la interacción con guía especial, valida al dueño de la mascota vanilla domesticada y elige su buzón más cercano a ocho bloques o menos de la mascota. BindPetUseCase crea un snapshot o rechaza un vínculo existente. Solo un cambio persistido con éxito consume una guía. La desvinculación es explícita.

El menú consulta posición y nonce exactos. El servidor comprueba chunk cargado, bloque, distancia y dueño. El cliente acepta solo buzón/dimensión/nonce correspondientes y distingue lista no disponible de vacía. Los retratos usan entidades seguidas por el cliente; la lista no garantiza disponibilidad.

## Cambiar el proyecto

Usa el Java correspondiente y ejecuta gradlew.bat --no-daemon build (Windows) o bash gradlew --no-daemon build dentro de cada adapter. Ambas builds incluyen pruebas comunes y comprobaciones del adapter. El código específico de API queda en adapters. Conserva IDs petpost y esquema. Prueba guardado/reinicio y autorización de red en mundos desechables.

La edad cronológica se omite porque no se registra el nacimiento. La etapa usa el estado cría/adulto de la entidad seguida. Un collar o colgante mostrado en la interfaz no implica un nuevo renderer de equipo en el mundo.

El adapter 1.21 usa el nombre persistido petpost_pet_posts; 26.2 usa el identificador petpost:pet_posts. Conserva la identidad persistida de cada adapter, salvo migración probada. Un cambio de protocolo requiere builds correspondientes en cliente/servidor.

El núcleo Java 21 comparte reglas, contratos, casos de uso y estado de interfaz sin imports Minecraft/Fabric. Los adapters gestionan registro, persistencia, red, eventos, mixins y renderizado. El servidor valida propiedad y distancia. Cada objetivo nuevo requiere adapter propio, dependencias fijadas, manifiesto/CI y pruebas en el juego. Conserva IDs petpost y datos guardados; un JAR no soporta todas las versiones.

