# Makefile - Lab03 (Servidor DNS sobre UDP en Java)
# --------------------------------------------------
# Objetivos principales (Java):
#   make            -> compila el servidor DNS (todos los .java)
#   make run        -> ejecuta el servidor DNS (puerto 53, requiere sudo)
#   make runtest    -> ejecuta en el puerto 5353 (sin privilegios, para pruebas)
#   make clean      -> elimina .class, el binario C y los logs
#
# Puedes pasar argumentos con ARGS, por ejemplo:
#   make run ARGS="-port 5353 -threads 8 -upstream 1.1.1.1"
#
# Objetivos del ejemplo en C que venia en el laboratorio base:
#   make c          -> compila lab_3.c -> binario udp_test
#   make runc       -> ejecuta el binario udp_test
#   make killudp    -> libera el puerto UDP 9999 si esta ocupado

# ---------- Java (servidor DNS) ----------
JC    = javac
JVM   = java
MAIN  = Lab03
JSRC  = $(wildcard *.java)

# ARGS por defecto para 'make run' (puerto 53 necesita privilegios)
ARGS ?=

all: classes

classes: $(JSRC)
	$(JC) -g $(JSRC)

run: classes
	sudo $(JVM) $(MAIN) $(ARGS)

runtest: classes
	$(JVM) $(MAIN) -port 5353 $(ARGS)

# ---------- C (ejemplo base UDP) ----------
CC     = gcc
CFLAGS = -O2 -Wall -Wextra
CSRC   = lab_3.c
COUT   = udp_test

c: $(COUT)

$(COUT): $(CSRC)
	$(CC) $(CFLAGS) -o $(COUT) $(CSRC)

runc: $(COUT)
	./$(COUT)

killudp:
	sudo kill -9 $$(sudo lsof -t -iUDP:9999)

# ---------- Limpieza ----------
clean:
	rm -f *.class $(COUT)
	rm -f logs/*.log logs/*.log.lck

.PHONY: all classes run runtest c runc killudp clean
