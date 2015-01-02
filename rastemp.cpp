#include <iostream>			//std::std::cerr, std::std::endl
#include <thread>				//std::thread
#include <cstdio>				//perror()
#include <cstring>				//memset()
#include <signal.h>			//signal, SIGPIPE
#include <unistd.h>			//write(), close()
#include <arpa/inet.h>		//sockaddr_in, INADDR_ANY, htonl
#include <sys/types.h>		//socket()
#include <sys/socket.h>	//socket()
#include "pi_dht_read.h"	//pi_dht_read()
#include "pi_mmio.h"		//pi_mmio_init(), pi_mmio_set_output(), pi_mmio_set_high(), pi_mmio_set_low()

#define PORT	4050		//서버 포트

bool run;

bool ledState = false;	//LED의 마지막 상태
void ledBlink(){
	if(ledState == false){
		pi_mmio_set_high(20);
		ledState = true;
	}else{
		pi_mmio_set_low(20);
		ledState = false;
	}
}

#define LED_FLASH_INTERVAL	10000000	//LED 깜빡이는 주기

void ledThread(){
	run = true;
	int counter = LED_FLASH_INTERVAL;
	while(run){
		if(counter == 0){
			counter = LED_FLASH_INTERVAL;
			ledBlink();
		}else
			counter--;
	}
}

bool sigpipe = false;
//클라이언트의 연결종료로 인한 SIGPIPE 발생시 알려주기 위한 함수
void sigpipe_handler(){
	sigpipe = true;
}

int main(){
	signal(SIGPIPE, sigpipe_handler);
	int r;
	/********** GPIO 입출력 준비 **********/
	if((r = pi_mmio_init()) != MMIO_SUCCESS){
		std::cerr << "Failed to setup GPIO: ";
		switch(r){
			case MMIO_ERROR_DEVMEM:
				std::cerr << "Cannot access to /dev/mem. Did you forgot sudo?" << std::endl;
				return 1;
			case MMIO_ERROR_MMAP:
				std::cerr << "Error occured while mapping /dev/mem to memory." << std::endl;
				return 2;
		}
	}
	/********** 서버 시작 **********/
	sockaddr_in servAddr;
	sockaddr_in clientAddr;
	int clientAddrSize, clientSock;
	int servSock = socket(PF_INET, SOCK_STREAM, 0);
	if(servSock == -1){
		perror("Failed to create socket");
		return 1;
	}
	memset( &servAddr, 0, sizeof(servAddr));
	servAddr.sin_family = AF_INET;
	servAddr.sin_port = htons(PORT);
	servAddr.sin_addr.s_addr = htonl(INADDR_ANY);
	if(bind(servSock, (struct sockaddr*)&servAddr, sizeof(servAddr)) == -1){
		perror("Failed to bind socket");
		return 2;
	}
	if(-1 == listen(servSock, 5)){
		perror("Failed to setup listening for connection");
		return 3;
	}
	/********** GPIO 설정, 변수 준비 **********/
	pi_mmio_set_output(20);
	float h, t;	//h는 습도, t는 온도
	char buf[2];	//데이터를 보내기 위해 사용할 버퍼.
	/********** 메인 루프 **********/
	while(true){
		/********** 연결 대기 **********/
		std::cout << "Waiting for connection..." << std::endl;
		clientAddrSize  = sizeof(clientAddr);
		std::thread blink(ledThread);	//연결을 대기할동안 쓰레드 구동
		clientSock = accept(servSock, (struct sockaddr*)&clientAddr, &clientAddrSize);
		run = false;	//작동 중지 신호
		blink.join();	//쓰레드 종료 대기
		if(clientSock == -1){
			perror("Failed to accept connection");
			continue;
		}
		/********** 연결됨, 센서값 전송 **********/
		std::cout << "Connected!" << std::endl;
		while(true){
			if((r = pi_dht_read(DHT11, 21, &h, &t)) == DHT_SUCCESS){
				std::cout << "h = " << h << ", t = " << t << std::endl;
				buf[0] = h;
				buf[1] = t;
				if(write(clientSock, buf, 2) == -1){
					perror("Failed to write");
					break;
				}
				//SIGPIPE 발생?
				if(sigpipe == true){
					std::cout << "Warning: SIGPIPE occur" << std::endl;
					sigpipe = false;
					//break;
				}
			}else{
				std::cerr << "Failed to read data: ";
				switch(r){
					case DHT_ERROR_TIMEOUT:
						std::cerr << "Timeout" << std::endl;
						break;
					case DHT_ERROR_CHECKSUM:
						std::cerr << "Checksum error" << std::endl;
						break;
					case DHT_ERROR_ARGUMENT:
						std::cerr << "Argument error" << std::endl;
						break;
					case DHT_ERROR_GPIO:
						std::cerr << "GPIO error" << std::endl;
						break;
				}
			}
			ledBlink();
		}
		/********** 연결 끊어짐 **********/
		close(clientSock);
		//close(servSock);
		//break;
	}
}
