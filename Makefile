all:
	g++ -o rastemp rastemp.cpp pi_dht_read.c common_dht_read.c pi_mmio.c -lrt -pthread -std=c++0x -fpermissive
