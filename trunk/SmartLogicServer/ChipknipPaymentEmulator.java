import java.util.Map;
import java.util.HashMap;

/**  
* ChipknipPaymentEmulator.java - Smart card emulator
*  Emulates the messages involved in a Chipknip payment.
*    
* @author  Gerhard de Koning Gans
*/
class ChipknipPaymentEmulator extends ProtocolEmulator {
	private Map<String, String> protocol;

	public ChipknipPaymentEmulator() {
		this.setActivated(false);
		protocol = new HashMap<String, String>();
		this.init();
	}

	public void reset() {
		// What should happen at a reset signal from the reader
	}

	public void init() {
		protocol.clear();
		protocol.put("ATR", "3B 67 00 00 2A 20 00 41 78 90 00");
		protocol.put("00 A4 00 0C 02 3F 00", "6E 00");
		protocol.put("A4 A4 00 0C 02 3F 00", "67 00");
		protocol.put("00 A4 04 00 07 D2 76 00 00 85 01 00", "6A 82");
		protocol
				.put("00 A4 04 00 0B 50 16 49 FF 00 01 00 00 00 66 11", "6A 82");
		protocol.put("00 C0 00 00 82", "69 85"); // direction is wrong (t=0)
		protocol.put("BC A4 00 00 02 29 01", "90 00");
		protocol.put("BC A4 00 00 02 17 FF", "90 00");
		protocol.put("BC B0 00 00 08", "00 00 00 00 00 00 00 00 90 00");
		protocol
				.put(
						"BC B0 00 00 64",
						"52 80 01 01 00 20 62 33 09 78 FF FF 01 00 FF FF 00 07 D0 FF 3F FF FF FF 00 07 D0 FF 11 11 30 FF 00 00 00 FF 00 00 00 FF 92 4C 76 E4 BD A9 EE 69 58 B0 94 F1 55 D5 E8 1C 10 B9 45 9E A3 9A FB CD 1E 6D F3 82 4E D0 65 51 18 CB 77 AB 1E 9E 7F 9D F2 71 7D B1 32 68 48 6D 57 01 A7 F4 EF 6F 83 00 04 05 92 DF 90 00");
		protocol
				.put(
						"BC B0 00 19 20",
						"2C 02 16 0D 67 30 10 11 11 11 11 11 00 0D 11 11 62 05 00 00 00 00 0F FF 01 01 00 03 20 FF 00 00 90 00");
		protocol.put("E1 B4 00 01 05", "00 04 78 09 78 90 00");
		protocol.put("BC B0 00 1F 02", "01 01 90 00");

		// Use same transaction counter as last transaction: 00 07
		// protocol.put("E1 50 02 00 0F",
		// "01 00 20 62 33 09 78 00 11 11 30 02 00 00 07 90 00");
		protocol.put("E1 50 02 00 0F",
				"01 00 20 62 33 09 78 00 11 11 30 02 00 00 08 90 00");

		// What is the content of the next message?
		// protocol.put("E1 5A 00 00 08 23 E0 67 6D A6 DA A6 BB", "90 00");

		// Remaining messages cannot be emulated... :-(
	}

	private byte[] stringToBytes(String str) {
		String[] strings = str.split(" ");
		byte[] bytes = new byte[strings.length];

		for (int i = 0; i < strings.length; i++) {
			bytes[i] = Integer.valueOf(strings[i], 16).byteValue();
		}

		return bytes;
	}

	public byte[] getResponse(String readerMessage) {
		byte[] reply = {};
		if (protocol.containsKey(readerMessage)) {
			reply = stringToBytes(protocol.get(readerMessage));
		}
		return reply;
	}

	public void setResponse(String readerMessage, String cardReply) {
		protocol.put(readerMessage, cardReply);
	}

	public void removeResponse(String readerMessage) {
		protocol.remove(readerMessage);
	}

	public void clear() {
		protocol.clear();
	}

}
