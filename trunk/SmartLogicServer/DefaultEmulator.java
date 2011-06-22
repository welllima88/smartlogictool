import java.util.Map;
import java.util.HashMap;

/**  
* DefaultEmulator.java - Smart card emulator (does nothing yet)
*     
* @author  Gerhard de Koning Gans
*/
class DefaultEmulator extends ProtocolEmulator {
	private Map<String, String> protocol;

	public DefaultEmulator() {
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
		// At default say: ERROR
		byte[] reply = { (byte) 0x6A, (byte) 0x82 };
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
