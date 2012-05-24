/*!
   SmartLogic v1.0 -- Gerhard de Koning Gans -- 2011

   For ZTEX USB FPGA Module 1.2
   Copyright (C) 2008-2009 ZTEX e.K.
   http://www.ztex.de

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

import java.io.*;
import java.util.*;

import ch.ntb.usb.*;

import ztex.*;

// =============== CLIENT PART ==================
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ClassNotFoundException;
import java.lang.Math;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

//import java.util.Calendar;
//import java.text.SimpleDateFormat;

// ================ ZTEX PART ====================

// *****************************************************************************
// ******* ParameterException **************************************************
// *****************************************************************************
// Exception the prints a help message
class ParameterException extends Exception {
	public final static String helpMsg = new String("Parameters:\n"
			+ "    -c                Determine reader clock and baudrate\n"
			+ "    -d <number>       Device Number (default: 0)\n"
			+ "    -f                Force uploads\n"
			+ "    -p                Print bus info\n"
			+ "    -s                Connect to SmartLogicServer\n"
			+ "    -w                Enable certain workarounds\n"
			+ "    -h                This help");

	public ParameterException(String msg) {
		super(msg + "\n" + helpMsg);
	}
}

// *****************************************************************************
// ******* Thread to intercept key presses *************************************
// *****************************************************************************
// 
class traceMarker {
	private boolean markerReceived = true;
	private String marker = "";

	public traceMarker() {
	}

	public synchronized void setMarker(String marker) {
		this.marker = marker;
	}

	public synchronized String getMarker() {
		return marker;
	}

	public synchronized boolean getMarkerReceived() {
		return markerReceived;
	}

	public synchronized void setMarkerReceived(boolean markerReceived) {
		this.markerReceived = markerReceived;
	}
}

class KeyEvents implements Runnable {

	Thread keThread;
	traceMarker mrk;

	public KeyEvents(traceMarker mrk) {
		this.mrk = mrk;
	}

	public KeyEvents(String threadName) {
		keThread = new Thread(this, threadName);
		keThread.start();
	}

	public void run() {
		// Listen on standard input
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			while (true) {
				String markMessage = br.readLine();
				if (markMessage.length() > 0) {
					mrk.setMarker(markMessage);
					mrk.setMarkerReceived(false);
					Thread.currentThread().sleep(1);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("Error: " + e.getLocalizedMessage());
		}
	}
}

// *****************************************************************************
// ******* Test0 ***************************************************************
// *****************************************************************************
class SmartLogic extends Ztex1v1 {

	public static final int usb_timeout = 4000;

	// Global variable
	private boolean useServer = false;
	private ObjectOutputStream ostream;
	private ObjectInputStream istream;
	private boolean determineClock = false;

	// ******* SCEmulator
	// **************************************************************
	// constructor
	public SmartLogic(ZtexDevice1 pDev) throws UsbException {
		super(pDev);
	}

	// ******* releaseInterface
	// ****************************************************
	// releases interface 0
	public void releaseInterface() {
		LibusbJava.usb_release_interface(handle(), 0);
	}

	static final String HEXES = "0123456789ABCDEF";

	public static String getHex(byte[] raw, int length) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(3 * raw.length);
		// for ( final byte b : raw ) {
		for (int i = 0; i < length; i++) {
			hex.append(HEXES.charAt((raw[i] & 0xF0) >> 4)).append(
					HEXES.charAt((raw[i] & 0x0F)));
			if (i < (length - 1)) {
				hex.append(" ");
			}
		}
		return hex.toString();
	}

	public static byte[] getBinaryMessage(byte[] message, int length) {
		if (message == null)
			return null;
		byte temp, bit, par;
		String bits;
		int i, j;
		int olength;

		olength = length;
		if (length % 2 == 1)
			i = length + 1;
		else
			i = length;
		length += (i / 2);
		length++;
		// length += 4;
		// final asshole
		byte mess[] = new byte[length];

		bits = "";
		for (i = 0; i < olength; i++) {
			temp = 0;
			par = 0;
			bits = bits + "0";
			for (j = 0; j < 8; j++) {
				bit = (byte) (((1 << j) & message[i]) >> j);
				par ^= bit;
				if (bit == 1)
					bits = bits + "1";
				else
					bits = bits + "0";
				temp ^= (bit << (7 - j));
			}
			if (par == 1)
				bits = bits + "1"; // even parity
			else
				bits = bits + "0"; // even parity
			bits = bits + "11";
		}
		while (bits.length() % 8 != 0) {
			bits = bits + "1";
		}
		for (i = 0; i < bits.length(); i += 8) {
			j = Integer.parseInt(bits.substring(i, i + 8), 2);
			mess[i / 8] = (byte) j;
		}
		mess[length - 1] = (byte) 0xFF;
		// mess[length-4] = (byte)0xFF;
		// mess[length-3] = (byte)0xFF;
		// mess[length-2] = (byte)0xFF;
		// mess[length-1] = (byte)0xFF;
		return mess;
	}

	public static byte[] decodeMessage(byte[] message, int length) {
		if (message == null)
			return null;

		String bits, sByte;
		int bitctr = 0;
		int startbit = 0;
		byte bit;
		int i, j;

		bits = "";
		sByte = "";
		bitctr = 0;
		startbit = 0;
		for (i = 0; i < length; i++) {
			for (j = 0; j < 8; j++) {
				bit = (byte) (((1 << (7 - j)) & message[i]) >> (7 - j));

				if (bit == 1) {
					System.out.print("1");
				} else {
					System.out.print("0");
				}

				if (startbit == 0 && bit == 0) {
					startbit = 1;
					bitctr = 1;
					sByte = "";
				} else if (startbit == 1) {
					if (bitctr > 0 && bitctr < 9) {
						if (bit == 1)
							sByte = "1" + sByte;
						else
							sByte = "0" + sByte;
					} else if (bitctr > 8) {
						startbit = 0;
						bits = bits + sByte;
					}
					bitctr++;
				}
			}
		}

		System.out.print("\n");

		byte mess[] = {};
		if (bits.length() > 0) {
			mess = new byte[bits.length() / 8];

			for (i = 0; i < bits.length(); i += 8) {
				j = Integer.parseInt(bits.substring(i, i + 8), 2);
				mess[i / 8] = (byte) j;
			}
		}

		return mess;
	}

	public void sendData(byte[] data) throws UsbException {
		byte banswer[];
		int blocksize = 32; // 16
		byte part[] = new byte[blocksize];
		int i = 0, j = 0;

		if (data.length > 0) {
			/*
			 * for(i = 0; i < Math.floor(data.length / blocksize); i++) {
			 * //System.out.println("IDIOT   : "+getHex(data,data.length) );
			 * System.arraycopy(data,(i * blocksize),part,0,blocksize); banswer
			 * = getBinaryMessage(part,part.length); j =
			 * LibusbJava.usb_bulk_write(handle(), 0x04, banswer,
			 * banswer.length, usb_timeout); if ( j<0 ) throw new
			 * UsbException("Error sending data: " + LibusbJava.usb_strerror());
			 * }
			 * 
			 * i = i * blocksize; if(i < data.length) {
			 * System.arraycopy(data,i,part,0,(data.length - i)); banswer =
			 * getBinaryMessage(part,(data.length - i)); j =
			 * LibusbJava.usb_bulk_write(handle(), 0x04, banswer,
			 * banswer.length, usb_timeout); if ( j<0 ) throw new
			 * UsbException("Error sending data: " + LibusbJava.usb_strerror());
			 * }
			 */
			banswer = getBinaryMessage(data, data.length);
			if (banswer.length > 40 && 1 == 0) {
				for (i = 0; i < banswer.length; i++) {
					banswer[i] = 0x55;
				}

			}
			j = LibusbJava.usb_bulk_write(handle(), 0x04, banswer,
					banswer.length, usb_timeout);
			if (j < 0)
				throw new UsbException("Error sending data: "
						+ LibusbJava.usb_strerror());

			System.out.println("CARD   : " + getHex(data, data.length));
		}
	}

	public byte[] getATR() throws IOException {
		byte answer[] = {};

		try {
			ostream.writeObject("#GETATR#");
			answer = (byte[]) istream.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return answer;
	}

	public void sendMarker(String message) throws IOException {
		byte answer[] = {};

		if (message.length() > 0) {
			try {
				ostream.writeObject("#MARK#" + message);
				answer = (byte[]) istream.readObject();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean processData() throws UsbException {
		String readerMessage;
		byte buf[];
		byte command[];
		byte answer[];
		byte delayed_answer[];
		byte empty[] = {};
		byte temp;
		int i = 0;
		int k = 0;
		buf = new byte[1024];
		byte ack[] = { (byte) 0x41, (byte) 0x43, (byte) 0x4B }; // ACK
		byte bdr[] = { (byte) 0x42, (byte) 0x44, (byte) 0x52 }; // BDR

		boolean is_command = true;

		byte card_9000[] = { (byte) 0x90, (byte) 0x00 };
		byte card_zero[] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		byte card_7037[] = { (byte) 0x70, (byte) 0x37, (byte) 0x70,
				(byte) 0x37, (byte) 0x70, (byte) 0x37, (byte) 0x70, (byte) 0x37 };
		byte card_6a82[] = { (byte) 0x6A, (byte) 0x82 };

		// READ DATA FROM READER
		i = LibusbJava.usb_bulk_read(handle(), 0x82, buf, 1024, usb_timeout);
		// long measure_starttime = System.currentTimeMillis(); // Start
		// measuring delay
		// long measure_stoptime = 0;
		// String strTime = "";

		// WHEN RECEIVING
		if (i > 0) {
			LibusbJava.usb_bulk_write(handle(), 0x04, ack, ack.length,
					usb_timeout);
			for (int j = 0; j < i; j++) {
				int b = new Integer(buf[j]).intValue();
				if (b < 0) {
					b = buf[j] & 0x80;
					b += buf[j] & 0x7F;
				}
				buf[j] = (byte) b;
			}

			// DISPLAY RAW READER MESSAGE
			readerMessage = getHex(buf, i);

			command = new byte[0];
			is_command = true;

			// CHECK FOR RST
			if (buf[0] == (byte) 0x52 && // R
					buf[1] == (byte) 0x53 && // S
					buf[2] == (byte) 0x54) { // T
				readerMessage = "#RESET#";

				System.out.println("READER : RESET");
			}
			// CHECK FOR BAUDRATE
			else if (buf[0] == (byte) 0x42 && // B
					buf[1] == (byte) 0x44 && // D
					buf[2] == (byte) 0x52) { // R
				long pt1 = ((long) (buf[3] & 0xFF) << 24)
						| ((long) (buf[4] & 0xFF) << 16)
						| ((long) (buf[5] & 0xFF) << 8)
						| (long) (buf[6] & 0xFF);
				long pt2 = ((long) (buf[7] & 0xFF) << 24)
						| ((long) (buf[8] & 0xFF) << 16)
						| ((long) (buf[9] & 0xFF) << 8)
						| (long) (buf[10] & 0xFF);
				long speed;
				double mhz = 0;
				double etu = 0.000372;
				if (pt1 > pt2) {
					speed = pt1 - pt2;
				} else {
					speed = pt2 - pt1;
				}
				if (speed != 0) {
					mhz = 48 / (1000000 / (double) speed);
					speed = (long) (mhz / etu);
				}
				readerMessage = "Clock: " + String.format("%.2f", mhz)
						+ "MHz / Baudrate: " + speed;
				if (pt2 == 0) {
					readerMessage += " [INVALID]";
				}
				is_command = false;

				System.out.println("         " + readerMessage);
			} else {
				command = decodeMessage(buf, i);
				readerMessage = getHex(command, command.length);

				if (determineClock) {
					LibusbJava.usb_bulk_write(handle(), 0x04, bdr, bdr.length,
							usb_timeout);
				}

				System.out.println("READER : " + readerMessage);
			}

			// WHEN WE RECEIVE SOME MESSAGE FROM THE READER
			answer = empty;
			delayed_answer = empty;
			if (readerMessage.length() > 0) { // &&
												// !readerMessage.equals("#RESET#")
				if (is_command && useServer) {
					// COMMUNICATE TO SMARTCARD SERVER AND OBTAIN RESPONSE
					try {
						ostream.writeObject(readerMessage);
						answer = (byte[]) istream.readObject();
						if (answer.length > 4) {
							if (answer[1] == (byte) 0x42
									&& answer[2] == (byte) 0x52
									&& answer[3] == (byte) 0x4B) {
								// Combine signal byte with response message
								// Workaround for problem sending 1-byte
								// messages
								delayed_answer = new byte[answer.length - 3];
								System.arraycopy(answer, 4, delayed_answer, 1,
										(answer.length - 4));
								delayed_answer[0] = answer[0];
								answer = empty;

								/*
								 * delayed_answer = new byte[answer.length - 4];
								 * System
								 * .arraycopy(answer,4,delayed_answer,0,(answer
								 * .length - 4)); temp = answer[0]; answer = new
								 * byte[1]; answer[0] = temp;
								 */
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				} else if (is_command && !readerMessage.equals("#RESET#")) {
					// DECIDE ON REPLY MESSAGE
					switch (command[0]) {
					case (byte) 0x00:
						if (command[1] == (byte) 0xA4) {
							// answer = card_9000;
						}
						break;
					default:
						// answer = card_zero;
						break;
					}

					if (command[0] == (byte) 0xBC && command[1] == (byte) 0xA4) {
						answer = new byte[1];
						answer[0] = command[1];
					} else {
						answer = card_6a82;
					}
				}
			}

			// ROUND-TRIP TIME MEASUREMENT
			/*
			 * measure_stoptime =
			 * (System.currentTimeMillis()-measure_starttime);
			 * if(!readerMessage.equals("#RESET#") &&
			 * !readerMessage.equals("00 C1 01 FE 3E") && readerMessage.length()
			 * > 0) { do{ measure_stoptime =
			 * (System.currentTimeMillis()-measure_starttime); } while
			 * (measure_stoptime < 10); } if(measure_stoptime > 9999) {
			 * measure_stoptime = 9999; } strTime = "[" + strTime + "%04d] ";
			 * strTime = strTime.format(strTime,measure_stoptime);
			 * System.out.println("TIME: " + strTime);
			 */

			sendData(answer);
			sendData(delayed_answer);
		}

		return true;
	}

	public void setATR(byte ATR[]) throws UsbException {
		int i = 0;
		int j = 0;
		byte temp[];
		byte toUSB[];

		temp = getBinaryMessage(ATR, ATR.length);

		i = temp.length - 1;

		while (temp[i] == (byte) 0xFF && i > 0) {
			i--;
		}
		j = i * 8;
		if ((temp[i] & (byte) 0x01) == 0 || (temp[i] & (byte) 0x02) == 0) {
			j = j + 8;
		} else if ((temp[i] & (byte) 0x04) == 0) {
			j = j + 7;
		} else if ((temp[i] & (byte) 0x08) == 0) {
			j = j + 6;
		} else if ((temp[i] & (byte) 0x10) == 0) {
			j = j + 5;
		} else if ((temp[i] & (byte) 0x20) == 0) {
			j = j + 4;
		} else if ((temp[i] & (byte) 0x40) == 0) {
			j = j + 3;
		} else if ((temp[i] & (byte) 0x80) == 0) {
			j = j + 2;
		} else {
			j = j + 1;
		}

		toUSB = new byte[temp.length + 4];

		// Tell USB to set ATR
		toUSB[0] = (byte) 0x41; // A
		toUSB[1] = (byte) 0x54; // T
		toUSB[2] = (byte) 0x52; // R
		toUSB[3] = (byte) j; // number of bits

		System.arraycopy(temp, 0, toUSB, 4, temp.length);

		i = LibusbJava.usb_bulk_write(handle(), 0x04, toUSB, toUSB.length,
				usb_timeout);
		if (i < 0)
			throw new UsbException("Error sending data: "
					+ LibusbJava.usb_strerror());
		System.out.println("CARD ATR  : " + getHex(ATR, ATR.length));
		System.out.println("NR OF BITS: " + j);
	}

	// ******* echo
	// ****************************************************************
	// writes a string to Endpoint 4, reads it back from Endpoint 2 and writes
	// the output to System.out
	public void echo(String input) throws UsbException {
		byte buf[] = input.getBytes();
		int i = LibusbJava.usb_bulk_write(handle(), 0x04, buf, buf.length,
				usb_timeout);
		if (i < 0)
			throw new UsbException("Error sending data: "
					+ LibusbJava.usb_strerror());
		System.out.println("Send " + i + " bytes: `" + input + "'");

		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
		}

		buf = new byte[1024];
		i = LibusbJava.usb_bulk_read(handle(), 0x82, buf, 1024, usb_timeout);
		if (i < 0)
			throw new UsbException("Error receiving data: "
					+ LibusbJava.usb_strerror());
		System.out.println("Read " + i + " bytes: `" + new String(buf, 0, i)
				+ "'");
		System.out.format("First byte is: %d", buf[0]);
	}

	// ******* main
	// ****************************************************************
	public static void main(String args[]) {

		int devNum = 0;
		boolean force = false;
		boolean workarounds = false;

		try {
			// init USB stuff
			LibusbJava.usb_init();

			// scan the USB bus
			ZtexScanBus1 bus = new ZtexScanBus1(ZtexDevice1.ztexVendorId,
					ZtexDevice1.ztexProductId, true, false, 1);
			if (bus.numberOfDevices() <= 0) {
				System.err.println("No devices found");
				System.exit(0);
			}

			// scan the command line arguments
			boolean useServer = false;
			boolean determineClock = false;
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-d")) {
					i++;
					try {
						if (i >= args.length)
							throw new Exception();
						devNum = Integer.parseInt(args[i]);
					} catch (Exception e) {
						throw new ParameterException(
								"Device number expected after -d");
					}
				} else if (args[i].equals("-f")) {
					force = true;
				} else if (args[i].equals("-p")) {
					bus.printBus(System.out);
					System.exit(0);
				} else if (args[i].equals("-p")) {
					bus.printBus(System.out);
					System.exit(0);
				} else if (args[i].equals("-w")) {
					workarounds = true;
				} else if (args[i].equals("-s")) {
					useServer = true;
				} else if (args[i].equals("-c")) {
					determineClock = true;
				} else if (args[i].equals("-h")) {
					System.err.println(ParameterException.helpMsg);
					System.exit(0);
				} else
					throw new ParameterException("Invalid Parameter: "
							+ args[i]);
			}

			// create the main class
			SmartLogic ztex = new SmartLogic(bus.device(devNum));
			ztex.certainWorkarounds = workarounds;
			ztex.useServer = useServer;
			ztex.determineClock = determineClock;

			// upload the firmware if necessary
			if (force || !ztex.valid()
					|| !ztex.dev().productString().equals("SmartLogic")) {
				System.out.println("Firmware upload time: "
						+ ztex.uploadFirmware("SmartLogic.ihx", force) + " ms");
			}

			// upload the bitstream if necessary
			if (force || !ztex.getFpgaConfiguration()) {
				System.out.println("FPGA configuration time: "
						+ ztex.configureFpga("fpga/SmartLogic.bin", force)
						+ " ms");
			}

			// claim interface 0
			ztex.trySetConfiguration(1);
			ztex.claimInterface(0);

			// ATR:
			// ===========================================================
			// Dutch Rabobank
			byte ATR[] = { (byte) 0x3B, (byte) 0x67, (byte) 0x00, (byte) 0x00,
					(byte) 0x29, (byte) 0x20, (byte) 0x00, (byte) 0x6F,
					(byte) 0x78, (byte) 0x90, (byte) 0x00 };

			// Chipknip SAM
			// byte ATR[] = {(byte)0x3F, (byte)0x67, (byte)0x00, (byte)0x00,
			// (byte)0x29, (byte)0x20, (byte)0x00, (byte)0x6F, (byte)0x78,
			// (byte)0x90, (byte)0x00};
			// 3F 67 25 00 29 24 30 01 F9 90 00

			// Swiss UBS Maestro/CASH Bank Card (11 instead of 13 for speed
			// reasons)
			// byte ATR[] = {(byte)0x3B, (byte)0x37, (byte)0x11, (byte)0x00,
			// (byte)0x80, (byte)0x62, (byte)0x11, (byte)0x04, (byte)0x82,
			// (byte)0x90, (byte)0x00 };

			// German geldkarte v2 -- works with ingenico terminal!
			// byte ATR[] = {(byte)0x3B, (byte)0xE2, (byte)0x00, (byte)0xFF,
			// (byte)0x81, (byte)0x31, (byte)0x50, (byte)0x45, (byte)0x65,
			// (byte)0x63, (byte)0xBE };

			// Swiss ZKB-Bancomat-Card
			// byte ATR[] = {(byte)0x3B, (byte)0x67, (byte)0x00, (byte)0x00,
			// (byte)0x00, (byte)0x31, (byte)0x80, (byte)0x71, (byte)0x86,
			// (byte)0x90, (byte)0x00 };

			// Egg (bank) VISA
			// First Direct (bank) Maestro card
			// First Direct Gold VISA
			// UK Barclaycard Platinum VISA
			// UK Barclaycard VISA
			// UK Halifax Platinum VISA
			// UK HSBC MasterCard
			// byte ATR[] = {(byte)0x3B, (byte)0x6D, (byte)0x00, (byte)0x00,
			// (byte)0x00, (byte)0x31, (byte)0xC0, (byte)0x71, (byte)0xD6,
			// (byte)0x64, (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x01,
			// (byte)0x83, (byte)0x90, (byte)0x00 };

			// HSBC Visa/MasterCard credit card
			// Barclay Card MasterCard
			// byte ATR[] = {(byte)0x3B, (byte)0x6D, (byte)0x00, (byte)0x00,
			// (byte)0x00, (byte)0x31, (byte)0xC0, (byte)0x71, (byte)0xD6,
			// (byte)0x64, (byte)0x38, (byte)0xD0, (byte)0x03, (byte)0x00,
			// (byte)0x84, (byte)0x90, (byte)0x00 };

			if (ztex.useServer) {
				InetAddress host = InetAddress.getLocalHost();
				Socket socket = new Socket("10.0.2.2", 7777); // host.getHostName()
				ztex.ostream = new ObjectOutputStream(socket.getOutputStream());
				ztex.istream = new ObjectInputStream(socket.getInputStream());
				// Soms uitzetten...
				//ATR = ztex.getATR(); // ophalen voor SAM
			}

			traceMarker mrk = new traceMarker();
			Thread keyEvents = new Thread(new KeyEvents(mrk), "keyevents");
			keyEvents.start();

			Thread.currentThread().sleep(1000);
			// System.out.println(Thread.currentThread());

			// First program ATR into memory
			ztex.setATR(ATR);
			while (true) {
				// Card communication
				ztex.processData();
				// CHECK FOR KEY INPUT
				if (!mrk.getMarkerReceived()) {
					ztex.sendMarker(mrk.getMarker());
					mrk.setMarkerReceived(true);
				}
			}

			// close conection
			// ztex.istream.close();
			// ztex.ostream.close();

			// release interface 0
			// ztex.releaseInterface();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("Error: " + e.getLocalizedMessage());
		}
	}

}
