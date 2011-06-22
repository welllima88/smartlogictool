/**  
* SmartLogicCache.java - Caches SIM communication to limit SIM access times  
* @author  Gerhard de Koning Gans
*/ 
import java.util.Map;
import java.util.HashMap;

class SmartLogicCache {
	private Map<String, String> protocol;

	public SmartLogicCache() {
		protocol = new HashMap<String, String>();
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
