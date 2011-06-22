import javax.smartcardio.Card;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import net.sourceforge.scuba.smartcards.CardService;

/**  
* ProtocolMitm.java - Man-in-the-middle
*  All smartcard access is relayed through function getResponse()
*  Any MitM intervention should be implemented there (see ProtocolMitmDefault.java)
*    
* @author  Gerhard de Koning Gans
*/
public abstract class ProtocolMitm {
	private boolean activated = false;

	public abstract void reset();

	public abstract byte[] getResponse(CardService card, byte[] readerMessage);

	public void setActivated(boolean activated) {
		this.activated = activated;
	}

	public boolean isActivated() {
		return activated;
	}
}
