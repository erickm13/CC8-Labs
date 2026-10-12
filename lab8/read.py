''' #####################
Función para leer el archivo y crea estructura {Nodo1, Nodo2, Costo}
Retorna el nodo inicial y la estructura.
'''
def routingFile(file_path):
    routeDict = {}
    initNode = None
    
    with open(file_path, 'r') as file:
        lines = file.readlines()
        # La primera línea siempre será el nodo inicial.
        initNode = lines[0].strip()
        
        # Procesar enlaces en el formato: Nodo1-Nodo2:Costo
        for line in lines[1:]:
            partes = line.strip().split(':')
            nodes = partes[0].split('-')
            node1, node2 = nodes[0], nodes[1]
            cost = int(partes[1])
            
            # Definir el nodo en la estructura si no existe.
            if node1 not in routeDict:
                routeDict[node1] = {}
            if node2 not in routeDict:
                routeDict[node2] = {}
            
            # Asignar el costo entre nodos en ambos sentidos
            # Nodo1 -> Nodo2   o   Nodo2 -> Nodo1
            routeDict[node1][node2] = cost
            routeDict[node2][node1] = cost

    # Retorno: nodo inicial y la estructura
    return initNode, routeDict











