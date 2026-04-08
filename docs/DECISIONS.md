# DECISIONS.md

Registro de decisiones persistentes del proyecto.

## Cuando registrar aqui

Registrar una decision cuando cambie:

- la arquitectura
- una convencion importante
- una tecnologia base
- un tradeoff que otros agentes deben respetar

## Formato sugerido

### DEC-0001 - Titulo de la decision

- Fecha: YYYY-MM-DD
- Estado: proposed | accepted | deprecated | replaced
- Contexto: que problema obligo la decision
- Decision: que se decidio exactamente
- Consecuencias: costos, beneficios y limites
- Reemplaza: DEC-XXXX o `none`

### DEC-0001 - SQLite como persistencia inicial del PoC

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  el sistema se construira como un conjunto de microservicios con base de
  datos propia. Para el PoC hacia falta elegir una opcion embebida y simple
  que permitiera persistir eventos de chat, agregarlos por palabra y
  consultarlos desde endpoints HTTP sin incorporar complejidad innecesaria.
- Decision:
  usar SQLite como persistencia inicial del PoC. Cada microservicio mantiene
  ownership de su propia base de datos SQLite. No se adopta H2, DuckDB,
  RocksDB ni MapDB en esta etapa.
- Consecuencias:
  se simplifica la puesta en marcha, el modelo de consultas agregadas y la
  evolucion posterior hacia PostgreSQL;
  se acepta la limitacion de concurrencia de escritura de SQLite como
  suficiente para el escenario esperado de una conferencia o demo;
  la informacion puede perderse al reiniciar los contenedores porque la
  persistencia del PoC es efimera;
  si el volumen real de escrituras concurrentes supera lo previsto, esta
  decision debera revisarse.
- Reemplaza: `none`

### DEC-0002 - Microservicios separados desde el dia uno

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  el PoC necesita cubrir ingestion, consulta, moderacion, usuarios y
  estadisticas con responsabilidades claras, integracion por webhook y REST
  y ownership de datos por servicio.
- Decision:
  iniciar el sistema con cinco microservicios separados:
  `insightbloom-ingest`, `insightbloom-query`, `insightbloom-moderation`,
  `insightbloom-users` e `insightbloom-stats`.
- Consecuencias:
  la separacion de responsabilidades queda clara desde el inicio y alinea el
  PoC con la direccion futura del sistema;
  aumenta la complejidad de arranque, despliegue y coordinacion entre
  servicios;
  obliga a mantener contratos HTTP pequenos y ownership bien definido.
- Reemplaza: `none`

### DEC-0003 - Identificadores externos con UUID y tablas internas con serial

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  el sistema necesita exponer identificadores estables entre peticiones y
  microservicios, pero tambien simplificar relaciones y rendimiento en las
  tablas SQLite internas del PoC.
- Decision:
  usar UUID para identificadores externos compartidos entre APIs y
  microservicios. Las tablas internas usaran ids seriales incrementales.
- Consecuencias:
  mejora la estabilidad del contrato externo y evita exponer ids internos;
  simplifica joins y almacenamiento interno;
  obliga a mantener ambos identificadores en las entidades que crucen
  fronteras de servicio.
- Reemplaza: `none`

### DEC-0004 - RelevanceScore visible y ponderado

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  la nube necesita una regla observable para que otro agente pueda
  implementarla sin inventar el ranking de palabras.
- Decision:
  usar `relevanceScore = visibleCount * typeWeight * averageIntentWeight`,
  con pesos de tipo e intencion definidos en `SPEC.md`, calculado solo sobre
  mensajes visibles.
- Consecuencias:
  la implementacion del ranking queda cerrada para el PoC;
  el sistema puede explicar por que una palabra crece o baja;
  la formula es simple y probablemente deba revisarse tras validar uso real.
- Reemplaza: `none`

### DEC-0005 - FriendlyId derivado del nombre con slug estable

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  la conferencia necesita un acceso rapido tipo Kahoot y hacia falta definir
  una regla estable para generar el identificador amigable.
- Decision:
  generar `friendlyId` automaticamente desde el nombre usando slug en
  minusculas, sin acentos, con guiones, longitud de 4 a 24 y sufijo
  incremental en caso de colision.
- Consecuencias:
  el acceso rapido queda predecible y facil de compartir;
  se evita depender de edicion manual para resolver colisiones;
  el identificador queda estable despues de creado.
- Reemplaza: `none`
