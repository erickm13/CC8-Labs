##############################
# Implementación del algoritmo
# de Dijkstra (Link State).
#####

# Infinito: costo de un nodo todavía no alcanzable desde el origen.
INF = float('inf')


'''
Función de costo desde w a v, donde w y v son vecinos.
Retorna c(w, v): el costo del enlace entre w y v, o INF si no son vecinos.
'''
def c(w, v, routeDict):
	return routeDict[w].get(v, INF)


'''
Función de Menor costo desde el nodo de origen hasta el destino v.
Retorna D(v): el costo actual conocido hacia v.
'''
def D(v, dist):
	return dist[v]


'''
Función de Nodo anterior vecino de v.
Retorna p(v): el nombre del nodo anterior (previo) en la ruta hacia v.
'''
def p(v, prev):
	return prev[v]


'''
Da formato a la celda "D(v), p(v)" de la tabla de pasos.
Si el nodo es inalcanzable (INF) se muestra solo el símbolo ∞.
'''
def cell(v, dist, prev):
	if dist[v] == INF:
		return "∞"
	return f"{dist[v]}, {prev[v]}"


'''
Construye la fila (tupla) de un paso con el formato que espera show.py:
(paso, celda nodo1, celda nodo2, ..., nodo agregado a N')
Las columnas van en orden alfabético, excluyendo el nodo inicial.
'''
def buildRow(step, cols, added, dist, prev):
	return (step,) + tuple(cell(v, dist, prev) for v in cols) + (added,)


'''
Función donde Inicia la Ejecución del Algoritmo de Dijkstra.
Retorna la lista de pasos (filas) para que show.py los imprima.
'''
def init(initNode, routeDict):
	nodes = sorted(routeDict.keys())
	# Columnas de la tabla: todos los nodos menos el inicial, en orden alfabético.
	cols = [v for v in nodes if v != initNode]

	# Inicialización:
	#   N' = {initNode}
	#   D(v) = c(initNode, v) si v es vecino del origen, de otro modo ∞
	#   p(v) = initNode si es vecino (de otro modo no definido aún)
	dist = {}
	prev = {}
	for v in nodes:
		if v == initNode:
			dist[v] = 0
			prev[v] = None
		else:
			cost = c(initNode, v, routeDict)
			dist[v] = cost
			prev[v] = initNode if cost != INF else None

	Nprime = {initNode}
	steps = []
	# Paso 0: fila de inicialización, el nodo agregado a N' es el inicial.
	steps.append(buildRow(0, cols, initNode, dist, prev))

	step = 1
	# Repetir hasta que N' contenga todos los nodos (N' == N).
	while len(Nprime) < len(nodes):
		# Selección del nodo w fuera de N' con D(w) mínimo.
		# Empate: se elige el siguiente en orden alfabético (cols ya está ordenado).
		w = None
		for v in cols:
			if v in Nprime:
				continue
			if w is None or dist[v] < dist[w]:
				w = v

		# Si el mínimo es ∞ la red no es conexa: no hay más nodos alcanzables.
		if w is None or dist[w] == INF:
			break

		Nprime.add(w)

		# Actualizar D(v) = min( D(v), D(w) + c(w, v) ) para los vecinos de w
		# que todavía no están en N'.
		for v in routeDict[w]:
			if v in Nprime:
				continue
			newCost = D(w, dist) + c(w, v, routeDict)
			# Empate en el costo: se toma el nuevo nodo w como previo (política del PDF).
			if newCost <= dist[v]:
				dist[v] = newCost
				prev[v] = w

		steps.append(buildRow(step, cols, w, dist, prev))
		step += 1

	return steps
