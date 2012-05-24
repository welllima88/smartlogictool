/*!
   SmartLogic -- smart card communication for ZTEX USB FPGA Module 1.2
   Copyright (C) 2010 Gerhard de Koning Gans

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License version 3 as
   published by the Free Software Foundation.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, see http://www.gnu.org/licenses/.
!*/

#include[ztex-conf.h]	// Loads the configuration macros, see ztex-conf.h for the available macros
#include[ztex-utils.h]	// include basic functions

// configure endpoints 2 and 4, both belong to interface 0 (in/out are from the point of view of the host)
EP_CONFIG(2,0,BULK,IN,512,2);	 
EP_CONFIG(4,0,BULK,OUT,512,2);	 

// select ZTEX USB FPGA Module 1.11 as target (required for FPGA configuration)
IDENTITY_UFM_1_11(10.12.0.0,0);	 

// give them a nice name
#define[PRODUCT_STRING]["SmartLogic"]

// this is called automatically after FPGA configuration
#define[POST_FPGA_CONFIG][POST_FPGA_CONFIG
	OEC = 255;
	IOC = 255;
	OED = 54;
	IOD = 0;
]

// include the main part of the firmware kit, define the descriptors, ...
#include[ztex.h]


void main(void)	
{
    WORD i,j,size;
	int startsending;
    BYTE b;
	int rst_prev;
	BYTE ATR[47];
	int ATR_LENGTH = 0;
	int report_reset = 0;
	int EP2BUSY = 0;
	int flip = 0;
	int ack = 0;
	int send = 0;
    
    init_USB();

    EP2CS &= ~bmBIT0;	// stall = 0
    SYNCDELAY; 
    EP4CS &= ~bmBIT0;	// stall = 0

    SYNCDELAY;		// first two packages are waste
    EP4BCL = 0x80;	// skip package, (re)arm EP4
    SYNCDELAY;
    EP4BCL = 0x80;	// skip package, (re)arm EP4

	//				   56
	// IOD0	= unused		// 0  
	IOD1 = 0; // SET ATR		// 1
	IOD2 = 0; // GET BAUD		// 1
	//IOD3 = 0; // RESET PIN	// 0
	IOD4 = 0; // USB CLOCK		// 1
	IOD5 = 0; // USB SEND		// 1
	//IOD6 = 0; // FPGA CLOCK	// 0
	//IOD7 = 0; // FPGA SEND	// 0

	i = 0; j = 0;
	rst_prev = 0;
	report_reset = 1;
	EP2BUSY = 0;
	ack = 1;

	while(1) {

		if(IOD3 == 1 && rst_prev == 0 && report_reset == 1 && IOD7 == 0) {
		//if(IOD3 == 1 && rst_prev == 0) {
			// REPORT RESET TO CLIENT
			report_reset = 0;
			EP2FIFOBUF[0] = 0x52; // R
			EP2FIFOBUF[1] = 0x53; // S
			EP2FIFOBUF[2] = 0x54; // T
			size = 3;
			EP2BCH = size >> 8;
			SYNCDELAY; 
			EP2BCL = size & 255;		// arm EP2
			SYNCDELAY;
		}
		rst_prev = IOD3;

		if ( !(EP4CS & bmBIT2) && IOD7 == 0) {	// EP4 is not empty, and FPGA is ready to receive
			size = (EP4BCH << 8) | EP4BCL;
			if (size > 0 && size <= 512) {
				j = 0;
				send = 1;
				if (size >= 3) {
					if (EP4FIFOBUF[0] == 65 && EP4FIFOBUF[1] == 84 && EP4FIFOBUF[2] == 82) { // ATR
						for(j = 3; j < size; j++) {
							ATR[j-3] = EP4FIFOBUF[j];
						}
						ATR_LENGTH = size - 3;
						j = 3;
						IOD1 = 1;
					}
					else if (EP4FIFOBUF[0] == 65 && EP4FIFOBUF[1] == 67 && EP4FIFOBUF[2] == 75) { // ACK
						ack = 1;
						send = 0;
					}
					else if (EP4FIFOBUF[0] == 66 && EP4FIFOBUF[1] == 68 && EP4FIFOBUF[2] == 82) { // BDR
						ack = 1;
						send = 0;
						// OBTAIN BAUDRATE
						IOD2 = 1; IOD4 = 1; // put clock already in 1 state
						SYNCDELAY;
						SYNCDELAY;
						while(!IOD6) { SYNCDELAY; } // wait till measurement is ready
						IOD5 = 1;
						SYNCDELAY;
						EP2FIFOBUF[0] = 0x42; // B
						EP2FIFOBUF[1] = 0x44; // D
						EP2FIFOBUF[2] = 0x52; // R
						while(j < 8) {
							if (IOD6 != IOD4) {
								EP2FIFOBUF[j+3] = IOB;
								SYNCDELAY;
								IOD4 = IOD6;
								j++;
							}
						}
						EP2BCH = 0;
						SYNCDELAY; 
						EP2BCL = 11;		// arm EP2
						i = 0;
						EP2BUSY = 1;
					}
				}
				if(send) {
					startsending = 1;
					while( j < size ) {
						if(IOD4 == IOD6 || startsending) {
							IOC = EP4FIFOBUF[j];
							IOD4 = IOD6 ^ 1;
							startsending = 0;
							IOD5 = 1; 
							j++;
						}
						SYNCDELAY;
					}
				}
				IOD5 = 0;
				IOD2 = 0;
				IOD1 = 0;
			}
			SYNCDELAY; 
			EP4BCL = 0x80;			// skip package, (re)arm EP4
		}
		else {
			// When FPGA is sending and clock bit 7 changed, then next bit is coming in...
			// 4 = clock usb, 5 = send usb, 6 = clock fpga, 7 = send fpga
			if(EP2CS & 4) { EP2BUSY = 0; } // WHEN EP2 IS EMPTY
			
			if (IOD7 && IOD6 != IOD4 && !EP2BUSY) { // IOD7
				EP2FIFOBUF[i] = IOB;
				SYNCDELAY;
				IOD4 = IOD6;
				if(i < 512) { i++; } else { i = 0; }
			}
			
			if(IOD7 == 0 && i > 0 && i < 512) { // ack
				size = i;
				EP2BCH = size >> 8;
				SYNCDELAY; 
				EP2BCL = size & 255;		// arm EP2
				report_reset = 1;
				i = 0;
				EP2BUSY = 1;
				ack = 0;
			}
			SYNCDELAY;
		}
	} // end while

}

