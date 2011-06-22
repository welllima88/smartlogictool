/**  
* ProtocolEmulator.java - Abstract class for smart card emulation.
*  See DefaultEmulator.java for example implementation.
*    
* @author  Gerhard de Koning Gans
*/
public abstract class ProtocolEmulator {
	private boolean activated = false;

	public abstract void reset();

	public abstract byte[] getResponse(String readerMessage);

	public void setActivated(boolean activated) {
		this.activated = activated;
	}

	public boolean isActivated() {
		return activated;
	}
}
