import java.util.Map;
import java.util.HashMap;

/**  
* ChipknipEmulator.java - Chipknip emulator
*  To read out balance of Chipknip.
*    
* @author  Gerhard de Koning Gans
*/class ChipknipEmulator extends ProtocolEmulator {
	private Map<String, String> protocol;

	public ChipknipEmulator() {
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
		protocol.put("00 A4 04 00 07 A0 00 00 00 04 80 02", "6A 82");
		protocol.put("00 A4 04 00 07 A0 00 00 00 03 80 02", "6A 82");
		protocol.put("BC A4 00 00 02 2F FD", "6A 82");
		protocol.put("BC A4 00 00 02 2F 00", "6A 82");
		protocol.put("00 A4 04 00 07 A0 00 00 00 04 10 10", "6A 82");
		protocol.put("00 A4 04 00 07 A0 00 00 00 04 30 60", "6A 82");
		protocol.put("00 A4 04 00 07 A0 00 00 00 03 20 10", "6A 82");
		protocol.put("00 A4 04 00 07 A0 00 00 00 03 10 10", "6A 82");
		protocol.put("BC A4 00 00 02 29 01", "90 00");
		protocol.put("BC B0 00 1A 11",
				"67 30 10 11 11 11 11 11 00 0D 11 11 62 05 00 00 00 90 00");
		protocol.put("E1 B4 00 01 05", "00 04 79 09 78 90 00");
		protocol
				.put(
						"E1 B6 00 01 24",
						"04 80 00 04 79 FF 00 01 68 09 78 00 45 25 91 01 03 D2 26 FF 00 06 FF FF 07 9F E0 D7 64 43 00 00 00 00 00 00 90 00");
		protocol
				.put(
						"E1 B6 00 03 24",
						"04 80 00 05 E1 FF 00 00 A5 09 78 21 04 63 58 01 00 06 D5 FF 00 05 FF FF 04 E0 71 50 64 40 FF FF FF FF FF FF 90 00");
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
