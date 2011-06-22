import javax.smartcardio.Card;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import net.sourceforge.scuba.smartcards.CardEvent;
import net.sourceforge.scuba.smartcards.CardManager;
import net.sourceforge.scuba.smartcards.CardService;
import net.sourceforge.scuba.smartcards.CardServiceException;
import net.sourceforge.scuba.smartcards.TerminalCardService;
import net.sourceforge.scuba.smartcards.CardTerminalListener;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ClassNotFoundException;
import java.lang.Runnable;
import java.lang.Thread;
import java.net.ServerSocket;
import java.net.Socket;

// For date time
import java.util.Calendar;
import java.text.SimpleDateFormat;

/**  
* SmartLogicMessaging.java - SmartLogicMessaging connects to a reader and
* is responsible for all the messaging between the card(s) and reader
*  
* @author  Gerhard de Koning Gans
*/ 
class SmartLogicMessaging implements CardTerminalListener {
	private boolean cardPresent = false;
	private CardService cardService = null;
	private SmartLogicSIMFile sFile = null;
	private SmartLogicCache sCache = null;
	private ProtocolEmulator dpEmulator = null;
	private ProtocolMitm dpMitm = null;
	private byte ATR[];
	public static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";

	public enum ApplicationType {
		CHIPKNIP, EMV, SIM
	};

	public enum ProtocolType {
		T0, T1
	};

	public enum LogType {
		DEBUG, INFO, MARK, READER, CARD, CACHE, BAD_MESSAGE, SFILE_R, SFILE_C, SCONTEXT, CONNECT, DISCONNECT
	};

	// MEASURE TIME IN MS BETWEEN MESSAGES
	private long timems = 0;

	// ---------------------------------------------------------
	// INS bytes that indicate that the card is sending the data
	// This direction indication is needed for T=0 only
	// ---------------------------------------------------------

	// DUTCH CHIPKNIP (SMARTCARD PAYMENT)
	private byte chipknip_cardsends[] = { (byte) 0xB0, (byte) 0xB2,
			(byte) 0xB4, (byte) 0xB6, (byte) 0xC0, (byte) 0xC4, (byte) 0xCA,
			(byte) 0x50, (byte) 0x56, (byte) 0x5A };
	// EXCEPTIONS WHERE STILL READER SENDS DATA:
	// Example: 50 -> card sends, except when P1 = 01 (0xFF is separator)
	private byte chipknip_cardsends_exc[] = { (byte) 0x50, (byte) 0x01,
			(byte) 0xFF, (byte) 0x5A, (byte) 0x00, (byte) 0xFF };

	// EMV STANDARD
	private byte emv_cardsends[] = { (byte) 0x84, (byte) 0xCA, (byte) 0xB2,
			(byte) 0xC0 };
	private byte emv_cardsends_exc[] = { (byte) 0xFF }; // 0xFF is separator

	// SIMCARD
	private byte sim_cardsends[] = { (byte) 0xF2, (byte) 0xB0, (byte) 0xB2,
			(byte) 0xC0, (byte) 0x12 };
	private byte sim_cardsends_exc[] = { (byte) 0xFF }; // 0xFF is separator

	public SmartLogicMessaging() {
		System.setProperty("sun.security.smartcardio.t0GetResponse", "false");
		CardManager cm = CardManager.getInstance();
		sFile = new SmartLogicSIMFile();
		sCache = new SmartLogicCache();
		dpEmulator = new DefaultEmulator();
		dpMitm = new ProtocolMitmDefault();
		cm.addCardTerminalListener(this);
	}

	public static String now(String dateFormat) {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		return sdf.format(cal.getTime());
	}

	public boolean CardPresent() {
		return cardPresent;
	}

	public void setCardStatus(boolean present) {
		this.cardPresent = present;
	}

	public void setCard(CardService card) {
		this.cardService = card;
	}

	public CardService getCard() {
		return this.cardService;
	}

	protected static String toHex(byte[] array) {
		String hex = "";
		for (int i = 0; i < array.length; i++) {
			String s = Integer.toHexString(array[i] & 0xff).toString();
			hex += s.length() == 1 ? "0" + s : s;
		}
		return hex.toUpperCase();
	}

	protected static String toDisplayHex(byte[] array) {
		String hex = "";
		for (int i = 0; i < array.length; i++) {
			String s = Integer.toHexString(array[i] & 0xff).toString();
			hex += s.length() == 1 ? "0" + s + " " : s + " ";
		}
		if (hex.length() > 0) {
			hex = hex.substring(0, hex.length() - 1);
		}
		return hex.toUpperCase();
	}

	private byte[] stringToBytes(String str) {
		String[] strings = str.split(" ");
		byte[] bytes = new byte[strings.length];

		for (int i = 0; i < strings.length; i++) {
			bytes[i] = Integer.valueOf(strings[i], 16).byteValue();
		}

		return bytes;
	}

	private void logMessage(LogType lType, String lMessage, byte[] lBytes,
			boolean lShowTime) {
		logMessage(lType, 0, lMessage, lBytes, lShowTime);
	}

	private void logMessage(LogType lType, int lConnectionNr, String lMessage,
			byte[] lBytes, boolean lShowTime) {
		long timedif = 0;
		String strTime = "        ";
		String tmpTime;
		if (lShowTime) {
			if (timems != 0) {
				timedif = (System.currentTimeMillis() - timems);
				if (timedif > 9999) {
					timedif = 9999;
				}
				if (timedif > 1000) {
					strTime = "!";
				} else {
					strTime = "+";
				}
				strTime = "[" + strTime + "%04d] ";
				strTime = strTime.format(strTime, timedif);
			} else {
				strTime = "[START] ";
			}
			timems = System.currentTimeMillis();
		}

		String mess;
		if (lBytes != null && lBytes.length > 0) {
			mess = toDisplayHex(lBytes);
			if (mess.length() > 12) { // BRK
				if (mess.substring(3, 11).equals("42 52 4B")) {
					mess = mess.substring(0, 3)
							+ mess.substring(12, mess.length());
				}
			}
		} else {
			mess = lMessage;
		}

		switch (lType) {
		case DEBUG:
			System.out.println(">>>>DEBUG<<<<: " + mess);
		case INFO:
			System.out.println(">> " + mess);
			break;
		case MARK:
			System.out.print("==============================[");
			System.out.print(mess);
			System.out.print("][" + now(DATE_FORMAT_NOW) + "]");
			System.out.print("==============================\n");
			break;
		case CACHE:
			System.out.println(strTime + "CACHE     : " + mess);
			break;
		case CARD:
			System.out.println(strTime + "CARD      : " + mess);
			break;
		case READER:
			// System.out.println(strTime + "READER    : " + mess);
			System.out.format(strTime + "READER.%1d  : " + mess + "\n",
					lConnectionNr);
			break;
		case SFILE_C:
			System.out.println(strTime + "SFILE_C   : " + mess);
			break;
		case SFILE_R:
			System.out.println(strTime + "SFILE_R   : " + mess);
			break;
		case SCONTEXT:
			System.out.println(strTime + "-------- " + mess + " --------");
			break;
		case CONNECT:
			System.out.format(">> READER.%1d is connected...\n", lConnectionNr);
			break;
		case DISCONNECT:
			System.out.format(">> READER.%1d is disconnected...\n",
					lConnectionNr);
			break;
		default:
			break;
		}

	}

	public void clientConnectionMessage(int ConnectionNr, int connect) {
		LogType lt;
		if (connect == 1) {
			lt = LogType.CONNECT;
		} else {
			lt = LogType.DISCONNECT;
		}
		logMessage(lt, ConnectionNr, "", null, false);
	}

	private byte[] transmitBytes(CardService card, String bytes) {
		return transmitBytes(card, bytes, dpEmulator, dpMitm);
	}

	private byte[] transmitBytes(CardService card, String bytes,
			ProtocolEmulator pEmulator, ProtocolMitm pMitm) {
		byte answer[] = {};
		try {
			if (pEmulator.isActivated()) {
				answer = pEmulator.getResponse(bytes);
			} else if (pMitm.isActivated()) {
				answer = pMitm.getResponse(card, stringToBytes(bytes));
			} else {
				CommandAPDU command = new CommandAPDU(stringToBytes(bytes));
				// System.out.println("        APDU   : " +
				// toDisplayHex(command.getBytes()));
				ResponseAPDU response = card.transmit(command);
				// System.out.println(toHex(response.getBytes()));

				answer = response.getBytes();
			}
		} catch (CardServiceException e) {
			System.err.println("Caught exception: " + e);
		}

		return answer;
	}

	private void resetCard(CardService card) {
		try {
			card.close();
			card.open();
		} catch (CardServiceException e) {
			System.err.println("Caught exception: " + e);
		}
	}

	public void cardInserted(CardEvent ce) {
		try {
			CardService card = ce.getService();
			card.open();
			setCard(card);
			setCardStatus(true);
			timems = 0;
			ATR = ((TerminalCardService) card).getTerminal().connect("*")
					.getATR().getBytes();
			logMessage(LogType.INFO, "Smartcard inserted (ATR: "
					+ toDisplayHex(ATR) + ")", null, false);
		} catch (CardException e) {
			setCardStatus(false);
		} catch (CardServiceException e) {
			// e.printStackTrace();
			setCardStatus(false);
		}
	}

	public void cardRemoved(CardEvent ce) {
		setCardStatus(false);
		setCard(null);
		logMessage(LogType.INFO, "Smartcard removed", null, false);
	}

	public int handleCommand(ApplicationType aType, ProtocolType pType,
			ProtocolEmulator pEmulator, ProtocolMitm pMitm, int ConnectionNr,
			String readerMessage, ObjectInputStream istream,
			ObjectOutputStream ostream, SmartLogicSIMFile sFileClient) {
		byte reply[];
		byte empty[] = {};
		boolean unknown_file_size = false;

		// No reply yet
		reply = empty;

		// First check for control messages that do not need card access
		if (readerMessage.equals("#GETATR#")) {
			// SEND CARD ATR TO THE CLIENT
			reply = ATR;
		}

		try {

			// Define cache operation per application as some messages cannot be
			// cached
			if (aType == ApplicationType.SIM) {
				// Keep track of file changes...
				unknown_file_size = false;
				if (readerMessage.length() == 14) {
					// CHECK INS = A4 (SELECT)
					if (readerMessage.equals("A0 A4 00 00 02")) {
						logMessage(LogType.READER, ConnectionNr, readerMessage,
								null, true);
						// Repeat INS to get selector
						reply = new byte[1];
						reply[0] = Integer.valueOf(
								readerMessage.substring(3, 5), 16).byteValue();
						logMessage(LogType.CACHE, "", reply, false);
						ostream.writeObject(reply);
						readerMessage = ((String) istream.readObject())
								.toUpperCase();
						sFileClient.selectFile(readerMessage.substring(0, 2)
								+ readerMessage.substring(3, 5));

						// DO WE KNOW THE FILE SIZE??
						reply = sCache.getResponse(sFileClient.toString() + "|"
								+ readerMessage.toUpperCase());
						if (reply.length == 0) {
							// WE NEED TO ASK THE CARD
							sFile.reset();
							unknown_file_size = true;
							logMessage(LogType.READER, ConnectionNr,
									readerMessage, null, true);
						}
					}
				}

				// First check for a cached reply
				if (reply.length == 0) {
					reply = sCache.getResponse(sFileClient.toString() + "|"
							+ readerMessage.toUpperCase());
				}
			}

			// If we have a reply we do not need card access
			if (reply.length > 0) {
				logMessage(LogType.READER, ConnectionNr, readerMessage, null,
						true);
				logMessage(LogType.CACHE, "", reply, false);
				ostream.writeObject(reply);
				return 1;
			}

		} catch (Exception e) {
			System.out.println("Problem in first commandHandle() function...");
			e.printStackTrace();
			// reply = error;
		}

		// Call synchronized function for exclusive card access
		return handleCommand2(aType, pType, pEmulator, pMitm, ConnectionNr,
				readerMessage, istream, ostream, sFileClient, unknown_file_size);
	}

	public synchronized int handleCommand2(ApplicationType aType,
			ProtocolType pType, ProtocolEmulator pEmulator, ProtocolMitm pMitm,
			int ConnectionNr, String readerMessage, ObjectInputStream istream,
			ObjectOutputStream ostream, SmartLogicSIMFile sFileClient,
			boolean unknown_file_size) {

		CardService card = getCard();
		byte reply[];
		byte temp[] = null;
		byte parse[];
		byte empty[] = {};
		byte error[] = { (byte) 0x6A, (byte) 0x82 }; // FILE NOT FOUND
		byte t1_error[] = { (byte) 0x00, (byte) 0x81, (byte) 0x00, (byte) 0x81 };

		// To prevent protocol breaks due to message repititions
		String lastCommand;
		byte lastResponse[];

		byte cardsends[];
		byte cardsends_exc[];

		// Application determines direction of information flow in messaging
		// Who is sending the additional data?
		switch (aType) {
		case CHIPKNIP:
			cardsends = chipknip_cardsends;
			cardsends_exc = chipknip_cardsends_exc;
			break;
		case EMV:
			cardsends = emv_cardsends;
			cardsends_exc = emv_cardsends_exc;
			break;
		case SIM:
			cardsends = sim_cardsends;
			cardsends_exc = emv_cardsends_exc;
			break;
		default:
			cardsends = emv_cardsends;
			cardsends_exc = emv_cardsends_exc;
			break;
		}

		boolean cardanswer = false;
		int i = 0;

		lastCommand = "";
		lastResponse = new byte[0];

		try {
			String firstPart;
			byte CLA, INS, P1, P2, LC;
			int more_to_come;
			boolean mark = false;
			boolean display_response = true;

			// When A4 is received... to keep track of directory structure
			boolean file_select = false;
			boolean store_cache = false;

			// KEEP TRACK OF THE FIRST PART OF THE MESSAGE SENT TO THE CARD
			firstPart = "";

			// MANAGE FILE SELECTION FOR SIM APPLICATION
			if (aType == ApplicationType.SIM) {
				if (!sFile.equals(sFileClient)) {
					logMessage(LogType.SCONTEXT, "Change selected file ["
							+ sFile.toString() + "] => ["
							+ sFileClient.toString() + "]", null, false);

					String APDU = sFile.changeFile(sFileClient);
					while (APDU.length() > 0) {
						logMessage(LogType.SFILE_R, APDU, empty, true);
						temp = transmitBytes(card, APDU, pEmulator, pMitm);
						logMessage(LogType.SFILE_C, "", temp, false);
						APDU = sFile.changeFile(sFileClient);
					}

					logMessage(LogType.SCONTEXT, "SIM context changed", null,
							false);

					// HANDLE SPECIAL CASE WHERE FILE SIZE WAS NOT YET KNOWN
					if (unknown_file_size && temp != null) {
						sCache.setResponse(sFile.toString() + "|"
								+ readerMessage, toDisplayHex(temp));
						logMessage(LogType.CARD, "", temp, false);
						ostream.writeObject(temp);
						return 1;
					}
				}
			}

			// READER IS SENDING MORE BYTES RELATED TO THIS COMMAND (T=0)
			more_to_come = 0;

			do {
				if (more_to_come == 1) {
					// READ READERMESSAGE IN UPPER CASE (TEXTSTRING)
					readerMessage = ((String) istream.readObject())
							.toUpperCase();
				}
				more_to_come = 0;
				display_response = true;

				mark = false;
				if (readerMessage.length() > 6) {
					// ADD TEXT MARKER IN LOG FILE, DO NOT CONSTRUCT RESPONSE
					if (readerMessage.substring(0, 6).equals("#MARK#")) {
						mark = true;
						display_response = false;
					}
				}

				if (!mark) { // DISPLAY READER MESSAGE
					logMessage(LogType.READER, ConnectionNr, readerMessage,
							empty, true);
				} else { // RESET TIMER
					timems = 0;
				}

				if (readerMessage.equals("#RESET#")) {
					// WARM RESET
					if (!pEmulator.isActivated() && CardPresent()) {
						resetCard(card);
					} else {
						pEmulator.reset();
					}
					// Reset time measurement
					timems = 0;
					// Also reset SIM file management (only relevant for SIM
					// app)
					sFile.reset();
					// No answer from server side (Client sends ATR)
					reply = empty;
					// Do not print a response (Maybe change to print ATR)
					display_response = false;
				} else if (mark) {
					// PRINT TEXT MARKER IN LOG FILE
					reply = empty;
					logMessage(LogType.MARK, readerMessage.substring(6,
							readerMessage.length()), null, false);
				} else if (pType == ProtocolType.T1) {
					// WHEN PROTOCOL TYPE IS T=1
					try {
						if (readerMessage.equals("00 C1 01 FE 3E")) {
							byte defanswer[] = { (byte) 0x00, (byte) 0xE1,
									(byte) 0x01, (byte) 0xFE, (byte) 0x1E };
							reply = defanswer;
						} else {
							byte wrapper[] = { 0x00, 0x00, 0x00, 0x00 };
							temp = stringToBytes(readerMessage);
							int length = temp[2];
							if (length > 0) {
								wrapper[0] = temp[0];
								wrapper[1] = temp[1];
								temp = transmitBytes(card, readerMessage
										.substring(9, (length * 3) + 8),
										pEmulator, pMitm);
								wrapper[2] = (byte) temp.length;
								wrapper[3] = (byte) 0x00;
								reply = new byte[temp.length + 4];
								System
										.arraycopy(temp, 0, reply, 3,
												temp.length);
								reply[0] = wrapper[0];
								reply[1] = wrapper[1];
								reply[2] = wrapper[2];
								for (i = 0; i < (reply.length - 1); i++) {
									wrapper[3] ^= reply[i];
								}
								reply[3 + temp.length] = wrapper[3];
							} else {
								// Repeat request of reader
								byte reperror[] = { 0x00, (byte) 0x81, 0x00,
										(byte) 0x81 };
								if (temp[1] == (byte) 0x81
										|| temp[1] == (byte) 0x91) {
									reperror[1] = temp[1];
									reperror[3] = temp[1];
								}
								reply = reperror;
							}
						}
					} catch (Exception e) {
						System.out.println("#BAD MESSAGE#");
						reply = t1_error;
					}
				}
				// EVERYTHING BELOW IS FOR T=0
				else if (readerMessage.length() < 14) {
					// SOMEWHAT SHORT MESSAGE OF READER
					// ONLY VALID AFTER VALID COMMAND APDU
					// COULD BE ADDRESS OF FILE
					if (firstPart.length() > 0) {
						try {
							reply = transmitBytes(card, firstPart
									+ readerMessage, pEmulator, pMitm);
							if (file_select && reply.length == 2) {
								sFile.selectFile(toHex(reply));
								sFileClient.selectFile(toHex(reply));
							}
						} catch (Exception e) {
							// OR COULD BE WRONGLY INTERPRETED DATA
							System.out.println("#BAD MESSAGE#");
							reply = error;
						}
					} else {
						// OR IT IS JUST NOT A VALID APDU: DO NOTHING
						reply = empty;
					}
				} else {
					// APDU INTERPRETATION: CLA INS P1 P2 LC
					CLA = Integer.valueOf(readerMessage.substring(0, 2), 16)
							.byteValue();
					INS = Integer.valueOf(readerMessage.substring(3, 5), 16)
							.byteValue();

					P1 = 0;
					P2 = 0;
					LC = 0;
					if (readerMessage.length() == 14) {
						P1 = Integer.valueOf(readerMessage.substring(6, 8), 16)
								.byteValue();
						P2 = Integer
								.valueOf(readerMessage.substring(9, 11), 16)
								.byteValue();
						LC = Integer.valueOf(readerMessage.substring(12, 14),
								16).byteValue();
					}

					cardanswer = false;
					for (i = 0; i < cardsends.length; i++) {
						// CARD NEEDS TO GIVE AN ANSWER ACCORDING TO OUR
						// INS-LIST
						if (cardsends[i] == INS)
							cardanswer = true;
					}
					// HOWEVER THERE MIGHT BE SOME EXCEPTIONS DEPENDING ON P1
					for (i = 0; i < cardsends_exc.length - 1; i++) {
						if (cardsends_exc[i] == INS
								&& cardsends_exc[i + 1] == P1) {
							cardanswer = false;
						}
					}

					// FOR SIM APPLICATION: CLEAR COMPLETE CACHE FOR EVERY
					// UPDATE
					if (aType == ApplicationType.SIM) {
						// D6 = UPDATE BINARY, DC = UPDATE RECORD, 88 = RUN GSM
						// ALGORITHM (NEW KC)
						if (INS == (byte) 0xD6 || INS == (byte) 0xDC
								|| INS == (byte) 0x88) { // || INS == (byte)0x88
							// ONLY REMOVE RELEVANT ENTRIES FROM CACHE
							// 6F7E = EF_LOCI - Location information
							// 6F20 = KD - Session key
							// 6F74 = EF_BCCH - Broadcast Control Channels
							// NOTE: It might be that other phones use different
							// read commands and thus other
							// cahced data might need to be removed as well.
							if (sFile.getEF().equals("6F7E")) {
								sCache.removeResponse(sFile.toString()
										+ "|A0 B0 00 00 0B");
								sCache.removeResponse(sFile.toString()
										+ "|A0 C0 00 00 0F");
							} else if (sFile.getEF().equals("6F20")) {
								// KD: Session key
								sCache.removeResponse(sFile.toString()
										+ "|A0 B0 00 00 09");
								sCache.removeResponse(sFile.toString()
										+ "|A0 B0 00 00 08");
								// HEMA SIM
								sCache.removeResponse(sFile.toString()
										+ "|A0 C0 00 00 0C");
							} else if (sFile.getEF().equals("6F74")) {
								// EF_BCCH: Broadcast Control Channels
								sCache.removeResponse(sFile.toString()
										+ "|A0 B0 00 00 10");
							} else if (sFile.getDF().equals("7F20")
									&& INS == (byte) 0x88) {
								// HEMA SIM
								sCache.removeResponse(sFile.toString()
										+ "|A0 C0 00 00 0C");
							}
							// sCache.clear();
						}

						// B0 = READ BINARY, B2 = READ RECORD, C0 = GET
						// RESPONSE, F2 = GET STATUS
						if (INS == (byte) 0xB0 || INS == (byte) 0xB2
								|| INS == (byte) 0xC0 || INS == (byte) 0xF2) {
							store_cache = true;
						}
					}

					// WE EXPECT MORE DATA FROM THE READER (WITH LENGTH LC)
					if (!cardanswer && readerMessage.length() == 14 && LC != 0
							&& firstPart.length() == 0) {
						reply = new byte[1];
						reply[0] = INS;

						// Keep track of selected file for SIM application
						if (INS == (byte) 0xA4) {
							file_select = true;
						}

						firstPart = readerMessage + " ";
						more_to_come = 1;
					}
					// WE EXPECT DATA FROM THE CARD AND IMMEDIATLY ASK FOR IT
					// ANSWER IS "[INS]BRK[REPLY]"
					else if (cardanswer && readerMessage.length() == 14
							&& LC != 0 && firstPart.length() == 0) {
						// SEND COMMAND TO CARD
						try {
							temp = transmitBytes(card, readerMessage,
									pEmulator, pMitm);
						} catch (Exception e) {
							System.out.println("#BAD MESSAGE#");
							reply = error;
							temp = error;
						}

						if (LC != 2 && temp.length == 2) {
							// Probably an error message, send no INS byte
							reply = temp;
						} else {
							reply = new byte[temp.length + 4];
							System.arraycopy(temp, 0, reply, 4, temp.length);
							reply[0] = INS;
							reply[1] = (byte) 0x42; // B
							reply[2] = (byte) 0x52; // R
							reply[3] = (byte) 0x4B; // K
						}
					} else {
						// SEND COMMAND TO THE CARD, WE ONLY EXPECT STATUS BYTES
						// SW1 SW2
						try {
							reply = transmitBytes(card, firstPart
									+ readerMessage, pEmulator, pMitm);
						} catch (Exception e) {
							System.out.println("#BAD MESSAGE#");
							reply = error;
						}
					}
				}

				if (more_to_come == 0) {
					firstPart = "";
				}

				if (display_response) {
					logMessage(LogType.CARD, "", reply, false);
				}

				if (store_cache) {
					sCache.setResponse(sFile.toString() + "|" + readerMessage,
							toDisplayHex(reply));
				}

				ostream.writeObject(reply);
			} while (more_to_come == 1);

		} catch (IOException e) {
			// e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return 1;
	} // handleCommand()

}
