# Makefile - Lab04 (Cliente NTP en C, RFC 5905)
# ---------------------------------------------
#   make          -> compila el cliente NTP -> binario ./ntp_client
#   make run      -> ejecuta con la configuracion por defecto
#   make demo     -> demo: reloj interno arranca el 1-sep-2020 y se sincroniza
#   make clean    -> elimina el binario y los logs
#
# Se pueden pasar argumentos con ARGS, por ejemplo:
#   make run ARGS="-date \"2020-09-01 21:00:17.123\" -interval 10 -rounds 3"

CC      = gcc
CFLAGS  = -O2 -Wall -Wextra -std=c11 -D_GNU_SOURCE
LDFLAGS = -lm

OUT     = ntp_client
OBJS    = main.o ntp.o vclock.o

ARGS ?=

all: $(OUT)

$(OUT): $(OBJS)
	$(CC) $(CFLAGS) -o $(OUT) $(OBJS) $(LDFLAGS)

# Cada .o depende del header comun
%.o: %.c ntpclient.h
	$(CC) $(CFLAGS) -c $< -o $@

run: $(OUT)
	./$(OUT) $(ARGS)

# Demostracion: reloj atrasado ~6 anios que se sincroniza en pocas rondas.
demo: $(OUT)
	./$(OUT) -date "2020-09-01 21:00:17.123" -interval 8 -retry 3 -rounds 3 $(ARGS)

clean:
	rm -f $(OUT) $(OBJS)
	rm -f logs/*.log

.PHONY: all run demo clean
