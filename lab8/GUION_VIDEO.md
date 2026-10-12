# Guion del video — Laboratorio #8: Link State (Dijkstra)

> Objetivo del video: explicar (1) la **lógica del programa**, (2) los **cálculos** del algoritmo,
> y (3) la **ejecución de las topologías** (6 conocidas + topo7 argumentada).
> Duración sugerida: 8–12 min. Graba la terminal + tú explicando.

---

## 0. Intro (30 s)
- "Hola, soy [nombre]. Este es el Laboratorio 8: enrutamiento **Link State** con el algoritmo de **Dijkstra**."
- Idea clave en una frase: *en Link State cada router conoce TODA la topología (el costo de todos los
  enlaces) y con Dijkstra calcula el árbol de rutas más cortas hacia los demás nodos.*
- Diferencia vs Distance Vector: aquí hay **visión global** de la red → converge más rápido y evita bucles.

---

## 1. Lógica del programa (2–3 min)
Muestra la estructura de archivos y explica el flujo. Abre `LinkState.py` y sigue el orden:

1. **`LinkState.py`** = punto de entrada. Hace 3 cosas:
   - `validations()`: valida que sea Python 3, que se pase un archivo y que exista.
   - `read.routingFile(file_path)` → lee la topología.
   - `show.routeStruct(...)` → imprime la tabla de costos.
   - `dijkstra.init(...)` → ejecuta el algoritmo y devuelve los pasos.
   - `show.stepsCalculatingRoute(...)` → imprime la tabla paso a paso.

2. **`read.py`** → convierte el TXT en un **grafo** (diccionario de adyacencia).
   - La **primera línea** es el nodo inicial.
   - Cada línea `A-B:costo` se guarda en ambos sentidos: `routeDict[A][B]=costo` y `routeDict[B][A]=costo`
     (enlaces **no dirigidos**).
   - Ejemplo: `a-c:3` → `routeDict['a']['c']=3` y `routeDict['c']['a']=3`.

3. **`dijkstra.py`** → el algoritmo (lo explico en la sección 2). Devuelve una **lista de filas**;
   cada fila es `(paso, "D(v),p(v)", ..., nodo_agregado_a_N')`, en orden alfabético de nodos.

4. **`show.py`** → solo presentación: dibuja las dos tablas con bordes ASCII. No calcula nada.

> Frase para decir: *"La separación es clara: `read` arma el grafo, `dijkstra` calcula, `show` muestra,
> y `LinkState` orquesta."*

---

## 2. Los cálculos: cómo funciona Dijkstra (3–4 min)
Explica las 3 variables del algoritmo (están definidas como funciones en `dijkstra.py`):

- **`c(w,v)`** = costo del enlace directo entre vecinos `w` y `v` (∞ si no son vecinos).
- **`D(v)`** = menor costo conocido **hasta ahora** desde el origen hasta `v`.
- **`p(v)`** = nodo **previo** (vecino anterior) en esa ruta más corta.
- **`N'`** = conjunto de nodos cuya ruta mínima ya es **definitiva**.

### Pasos del algoritmo (dilo mientras muestras `init` en `dijkstra.py`)
1. **Inicialización:** `N' = {origen}`. Para cada nodo `v`:
   - si es vecino del origen → `D(v)=c(origen,v)`, `p(v)=origen`.
   - si no → `D(v)=∞`.
2. **Repetir** hasta que `N' == N` (todos los nodos):
   - Elegir el nodo `w` **fuera de N'** con **menor `D(w)`** y agregarlo a `N'`.
   - **Relajar** a sus vecinos `v` que aún no están en `N'`:
     `D(v) = min( D(v), D(w) + c(w,v) )`. Si baja, se actualiza `p(v)=w`.

### Políticas de empate (¡esto es lo que pide el PDF, recálcalo!)
- **Al elegir el nodo mínimo**, si hay empate en `D`, se toma el **menor en orden alfabético**.
  (En el código: recorro los nodos ya ordenados alfabéticamente y me quedo con el primero del mínimo.)
- **Al relajar `D(v)`**, si el costo nuevo **empata** con el actual, se toma el **nodo nuevo `w`**
  como previo. (En el código: uso `if newCost <= D(v)` → el `<=` hace que el empate se quede con `w`.)
  Ejemplo del PDF (Ej. 2): `D(h)=min(6,6)` → se elige `g` (el nuevo) en vez de `f`.

> Tip para el video: haz **un** paso a mano en pantalla (ej. topo3) para que se vea que coincide
> con lo que imprime el programa.

---

## 3. Ejecución de las topologías (3–4 min)
Comando general:
```bash
python LinkState.py test/topoN.txt
```

### topo1 — Ejemplo 1 del documento (origen u)
- Grafo: u-v:1, u-w:1, w-x:1, x-v:2.
- Resultado: **v=1 (u), w=1 (u), x=2 (w)**.
- Punto a destacar: en el paso 1 hay **empate** entre v y w (ambos D=1) → se elige **v** por orden
  alfabético. Luego x mejora de 3 (vía v) a 2 (vía w).

### topo2 — Ejemplo 2 (origen f)
- i **no es vecino** de f → arranca en ∞.
- Resultado: **g=5 (f), h=6 (g), i=8 (h)**.
- Destaca el **empate de relajación**: `D(h)=min(6 vía f, 5+1 vía g)=6` → se toma **g** (nodo nuevo).

### topo3 — Ejemplo 3 (origen a)
- Resultado: **b=6 (d), c=3 (a), d=5 (c)**.
- La ruta a `b` no es la directa (7) sino **a→c→d→b = 3+2+1 = 6**. Buen ejemplo de que la ruta
  más corta no es el enlace directo.

### topo4 — Ejercicio 1 (origen a)
- Resultado: **c=2(a), b=3(c), d=8(b), e=10(d), f=12(e)**.
- Nota: a `b` se llega mejor por `c` (a→c→b = 2+1=3) que directo (4).

### topo5 — Ejercicio 2 (origen a)
- Resultado: **b=2(a), c=3(a), e=4(b), d=5(e), f=7(d)**.
- `d` mejora: en vez de a→b→d (7), es a→b→e→d (2+2+1=5).

### topo6 — Ejercicio 3 (origen a)
- Resultado: **g=2(a), b=3(g), d=3(g), e=5(d), f=6(e), c=7(f), h=8(c)**.
- A `b` se llega mejor por `g` (a→g→b = 2+1=3) que directo (4). A `h` convergen varias rutas de
  costo 8 (vía c, vía e, vía f) → el programa resuelve el empate de forma determinista.

---

## 4. topo7 — Topología libre: argumenta por qué es correcto (2 min)
- Red de **13 nodos** (h…t), origen **h**. Es grande a propósito, para mostrar que el algoritmo
  escala y converge igual.
- **Rutas más cortas desde h (resultado del programa):**
  | destino | costo | ruta |
  |---|---|---|
  | o | 1 | h→o |
  | p | 2 | h→o→p |
  | j | 3 | h→j (directo) |
  | i | 3 | h→o→p→i |
  | t | 4 | h→o→p→i→t |
  | m | 5 | h→o→p→i→t→m |
  | n | 6 | h→j→n (o →m→n, empatan en 6) |
  | s | 6 | h→…→m→s |
  | r | 7 | h→…→s→r |
  | q | 8 | h→…→r→q |
  | k | 9 | h→…→q→k |
  | l | 10 | h→…→k→l |

- **Cómo argumentar que es correcto (dilo en el video):**
  1. *Propiedad de Dijkstra:* cuando un nodo entra a `N'`, su `D` ya es **definitivo** porque siempre
     se saca el de **menor costo pendiente** y todos los costos son **positivos** → ningún camino
     futuro puede mejorarlo.
  2. *Verificación manual de un caso:* `i` da 3 por h→o→p→i (1+1+1), que es menor que h→j→p→i
     (3+1+1=5) o h→o→j→p→i. El programa elige la de 3. ✔
  3. *Empates resueltos por política:* `n` puede llegar por `j` (3+3=6) o por `m` (5+1=6); el programa
     mantiene un criterio determinista (el último nodo que relaja con empate), así el resultado es
     **reproducible**.
  4. *Convergencia:* `N'` crece de 1 en 1 hasta incluir los 13 nodos (`N' == N`), sin nodos en ∞
     → la red es **conexa** y todas las rutas quedan calculadas.

> Cierre: *"El programa reproduce exactamente los 3 ejemplos del documento, resuelve los 3 ejercicios
> de la hoja y escala a una red de 13 nodos manteniendo las políticas de empate. Por la propiedad
> voraz de Dijkstra con costos positivos, cada ruta añadida a N' es óptima."*

---

## 5. Checklist antes de grabar
- [ ] `python --version` → mostrar que es 3.x.
- [ ] Correr las 7 topologías en orden.
- [ ] Explicar lógica (sección 1), cálculos (sección 2), topologías (3 y 4).
- [ ] Subir el video a YouTube y pegar el link en el README.
- [ ] Entregar el ZIP por el GES.
