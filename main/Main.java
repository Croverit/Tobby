package main;

import gamestarting.ClientKeyMessage;
import gamestarting.InterClientKeyManager;

import java.util.Hashtable;
import java.util.LinkedList;

import utilities.ByteArray;
import utilities.Log;
import messages.AuthenticationTicketMessage;
import messages.CharacterSelectionMessage;
import messages.CharactersListMessage;
import messages.CharactersListRequestMessage;
import messages.CheckIntegrityMessage;
import messages.EmptyMessage;
import messages.HelloConnectMessage;
import messages.IdentificationFailedMessage;
import messages.IdentificationMessage;
import messages.IdentificationSuccessMessage;
import messages.Message;
import messages.RawDataMessage;
import messages.SelectedServerDataMessage;
import messages.ServerSelectionMessage;

public class Main {
	protected static final int BUFFER_SIZE = 8192;
	private static final String authServerIP = "213.248.126.39";
	private static final int port = 5555;
	private static Connection serverCo = null; // temporaire bien s�r
	private static Hashtable<Integer, Message> sentMsgList = new Hashtable<Integer, Message>();
	private static Hashtable<Integer, Message> receivedMsgList = new Hashtable<Integer, Message>(); 
	
	public static void main(String[] args) {
		Emulation.runASLauncher();
		byte[] buffer = new byte[BUFFER_SIZE];
		int bytesReceived = 0;
		
		Log.p("Connecting to authentification server, waiting response...");
		serverCo = new Connection.Client(authServerIP, port);
		while((bytesReceived = serverCo.receive(buffer)) != -1) {
			Log.p(bytesReceived + " bytes received from server.");
			processMsgStack(Reader.processBuffer(new ByteArray(buffer, bytesReceived)));
		}
		serverCo.close();
		Log.p("Deconnected from authentification server.");
		
		SelectedServerDataMessage SSDM = (SelectedServerDataMessage) receivedMsgList.get(42);
		if(SSDM != null) {
			Log.p("Connecting to game server, waiting response...");
			serverCo = new Connection.Client(SSDM.getAddress(), port);
			while((bytesReceived = serverCo.receive(buffer)) != -1) {
				Log.p(bytesReceived + " bytes received from server.");
				processMsgStack(Reader.processBuffer(new ByteArray(buffer, bytesReceived)));
			}
			serverCo.close();
			Log.p("Deconnected from game server.");
		}
	}
	
	public static void processMsgStack(LinkedList<Message> msgStack) {
		Message msg;
		while((msg = msgStack.poll()) != null) {
			Log.p("r", msg);
			switch(msg.getId()) {
				case 3 :
					HelloConnectMessage HCM = new HelloConnectMessage(msg);
					receivedMsgList.put(3, HCM);
					IdentificationMessage IM = new IdentificationMessage();
					IM.serialize(HCM);
					sendMessage(IM);
					break;
				case 22 :
					IdentificationSuccessMessage ISM = new IdentificationSuccessMessage(msg);
					receivedMsgList.put(22, ISM);
					break;
				case 20 :
					IdentificationFailedMessage IFM = new IdentificationFailedMessage(msg); 
					IFM.deserialize();
					break;
				case 30 : 
					ServerSelectionMessage SSM = new ServerSelectionMessage();
					SSM.serialize();
					sendMessage(SSM);
					break;
				case 42 : 
					SelectedServerDataMessage SSDM = new SelectedServerDataMessage(msg);
					receivedMsgList.put(42, SSDM);
					break;
				case 101 :
					SSDM = (SelectedServerDataMessage) receivedMsgList.get(42);
					AuthenticationTicketMessage ATM = new AuthenticationTicketMessage();
					ATM.serialize(SSDM);
					sendMessage(ATM);
					break;
				case 6253 :
					HCM = (HelloConnectMessage) receivedMsgList.get(3);
					ISM = (IdentificationSuccessMessage) receivedMsgList.get(22);
					RawDataMessage RDM = new RawDataMessage(msg);
					Emulation.sendCredentials();
					Emulation.createServer(HCM, ISM, RDM);
					break;
				case 6372 : 
					Message CIM = new CheckIntegrityMessage(msg);
					sendMessage(CIM);
					break;
				case 6267 :
					CharactersListRequestMessage CLRM = new CharactersListRequestMessage();
					sendMessage(CLRM);
					break;
				case 151 :
					CharactersListMessage CLM = new CharactersListMessage(msg);
					CharacterSelectionMessage CSM = new CharacterSelectionMessage();
					CSM.serialize(CLM);
					sendMessage(CSM);
					break;
				case 6471 :
					InterClientKeyManager ICKM = InterClientKeyManager.getInstance();
					ICKM.getKey();
					EmptyMessage EM1 = new EmptyMessage("FriendsGetListMessage");
					EmptyMessage EM2 = new EmptyMessage("IgnoredGetListMessage");
					EmptyMessage EM3 = new EmptyMessage("SpouseGetInformationsMessage");
					ClientKeyMessage CKM = new ClientKeyMessage();
					CKM.serialize(ICKM);
					EmptyMessage EM4 = new EmptyMessage("GameContextCreateRequestMessage");
					sendMessage(EM1);
					sendMessage(EM2);
					sendMessage(EM3);
					sendMessage(CKM);
					sendMessage(EM4);
					break;
			}
		}
	}
	
	private static void sendMessage(Message msg) {
		serverCo.send(msg.makeRaw());
		Log.p("s", msg);
		sentMsgList.put(new Integer(msg.getId()), msg);
	}
}