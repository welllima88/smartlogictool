import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ClassNotFoundException;
import java.lang.Runnable;
import java.lang.Thread;
import java.net.ServerSocket;
import java.net.Socket;

/**  
* SmartLogicServer.java - Accepts connections from SmartLogic clients and
* defines whether any Man-in-the-Middle or emulator setup is used.
* Manages smartcard access for all connected SmartLogic clients.
*   
* @author  Gerhard de Koning Gans
*/ 
public class SmartLogicServer {
	private SmartLogicMessaging SLMessaging;
	private ServerSocket server;
	private int port = 7777;
	private static final String VERSION = "v1.2";

	public SmartLogicServer() {
		SLMessaging = new SmartLogicMessaging();
		try {
			server = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		System.out.println(">> SmartLogic Server " + VERSION
				+ " - Gerhard de Koning Gans - Copyright 2011");
		SmartLogicServer SLServer = new SmartLogicServer();
		SLServer.handleConnection();
	}

	public void handleConnection() {
		int ConnectionNr = 0;
		while (true) {
			try {
				Socket socket = server.accept();
				new ConnectionHandler(socket, ConnectionNr++, SLMessaging);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

class ConnectionHandler implements Runnable {
	private SmartLogicMessaging SLMessaging;
	private Socket socket;
	private int ConnectionNr;
	String readerMessage = "";

	public ConnectionHandler(Socket socket, int ConnectionNr,
			SmartLogicMessaging SLMessaging) {
		this.SLMessaging = SLMessaging;
		this.socket = socket;
		this.ConnectionNr = ConnectionNr;

		Thread t = new Thread(this);
		t.start();
	}

	public void run() {
		try {
			ObjectInputStream istream = new ObjectInputStream(socket
					.getInputStream());
			ObjectOutputStream ostream = new ObjectOutputStream(socket
					.getOutputStream());
			SmartLogicSIMFile sFile = new SmartLogicSIMFile();
			// ProtocolEmulator pEmulator = new DefaultEmulator();
			// ProtocolEmulator pEmulator = new ChipknipEmulator();
			ProtocolEmulator pEmulator = new ChipknipPaymentEmulator();
			ProtocolMitm pMitm = new ProtocolMitmDefault();

			// Protocol Emulation?
			pEmulator.setActivated(false);

			// Man-in-the-middle Active?
			pMitm.setActivated(false);

			// Display connected message
			SLMessaging.clientConnectionMessage(ConnectionNr, 1);

			while (!readerMessage.equals("#CLOSE#")) {
				readerMessage = ((String) istream.readObject()).toUpperCase();
				// Application type is only important for T=0, not for T=1
				SLMessaging.handleCommand(
						SmartLogicMessaging.ApplicationType.CHIPKNIP,
						SmartLogicMessaging.ProtocolType.T0, pEmulator, pMitm,
						ConnectionNr, readerMessage, istream, ostream, sFile);
			}

			istream.close();
			ostream.close();
			socket.close();

		} catch (IOException e) {
			// e.printStackTrace();
			// Disconnected...
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			// Display disconnected message
			SLMessaging.clientConnectionMessage(ConnectionNr, 0);
		}
	}
}
