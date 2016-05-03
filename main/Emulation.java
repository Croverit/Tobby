package main;

import java.io.File;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import controller.characters.Character;
import messages.Message;
import messages.connection.HelloConnectMessage;
import messages.connection.IdentificationSuccessMessage;
import messages.security.RawDataMessage;
import utilities.ByteArray;
import utilities.Processes;

public class Emulation {
	private static Connection.Client launcherCo;
	private static Connection.Server clientDofusCo;
	private static Reader reader = new Reader();
	private static Lock lock = new ReentrantLock();
	private static Process launcherProcess;
	private static Thread securityThread;

	public static void runLauncher() {
		if(!Processes.inProcess(Main.BYPASS_EXE))
			try {
				if(!Processes.fileExists(Main.BYPASS_PATH))
					throw new FatalError("Emulation launcher not found.");
				else {
					Log.info("Running emulation launcher.");
					launcherProcess = Runtime.getRuntime().exec(Main.BYPASS_PATH, null, new File(Main.CLIENT_PATH));
				}
			} catch(Exception e) {
				throw new FatalError(e);
			}
		else
			Log.info("Emulation launcher already in process.");
		
		clientDofusCo = new Connection.Server(Main.SERVER_PORT);
		
		// thread qui "secoue" le client toutes les 10 minutes
		securityThread = new Thread() {
			@Override
			public synchronized void run() {
				while(true)
					try {
						wait(60000 * 10); // 10 minutes
						lock.lock();
						shakeUpClient();
						clientDofusCo.waitClient();
						clientDofusCo.closeClient();
						lock.unlock();
					} catch(InterruptedException e) {
						Log.info("Security thread terminated.");
						return;
					}
			}
		};
		securityThread.start();
	}
	
	public static void killLauncher() {
		// on coupe la connexion avec le launcher
		if(securityThread != null) {
			securityThread.interrupt();
			securityThread = null;
		}
		if(launcherCo != null) {
			launcherCo.close();
			launcherCo = null;
			Log.info("Connection to emulation launcher closed.");
		}
		
		// et on tue le processus via l'objet Process ou via une commande Windows
		if(launcherProcess != null)
			launcherProcess.destroy();
		else if(Processes.inProcess(Main.BYPASS_EXE))
			Processes.killProcess(Main.BYPASS_EXE);
		else { // processus inexistant
			Log.info("Emulation launcher process does not exist");
			return;
		}
		
		// on attend que le processus se termine
		while(Processes.inProcess(Main.BYPASS_EXE))
			try {
				Thread.sleep(500);
			} catch(InterruptedException e) {
				e.printStackTrace();
				return;
			}
		Log.info("Emulation launcher process killed.");
	}
	
	public static Message emulateServer(String login, String password, HelloConnectMessage HCM, IdentificationSuccessMessage ISM, RawDataMessage RDM, int characterId) {
		lock.lock();
		if(launcherCo == null)
			connectToLauncher();
		
		// simulation de l'authentification
		int requestSize = 1 + 2 + login.length() + 2 + password.length();
		ByteArray array = new ByteArray(4 + requestSize);
		array.writeInt(requestSize);
		array.writeByte(1);
		array.writeUTF(login);
		array.writeUTF(password);
		Character.log("Sending credentials to emulation launcher.");
		launcherCo.send(array.bytes());
		
		// simulation du serveur officiel
		try {
			/*
			clientDofusCo = new Connection.Server(serverPort);
			Character.log("Running emulation server. Waiting Dofus client connection...");
			*/
			
			clientDofusCo.waitClient();
			Character.log("Dofus client connected.");
			
			clientDofusCo.send(HCM.pack(characterId));
			Character.log("HCM sent to Dofus client");
			
			byte[] buffer = new byte[ByteArray.BUFFER_DEFAULT_SIZE];
			int bytesReceived = clientDofusCo.receive(buffer);
			if(bytesReceived == -1)
				throw new Exception();
			Character.log(bytesReceived + " bytes received from Dofus client.");
			array.setArray(buffer, bytesReceived);
			processMsgStack(reader.processBuffer(array));
			
			clientDofusCo.send(ISM.pack(characterId));
			Character.log("ISM sent to Dofus client");
			clientDofusCo.send(RDM.pack(characterId));
			Character.log("RDM sent to Dofus client");
			
			bytesReceived = clientDofusCo.receive(buffer);
			if(bytesReceived == -1)
				throw new Exception();
			Character.log(bytesReceived + " bytes received from Dofus client.");
			array.setArray(buffer, bytesReceived);
			Message CIM = processMsgStack(reader.processBuffer(array));
			
			Character.log("Asking hash function to emulation launcher.");
			byte[] bytes = {0, 0, 0, 2, 2, (byte) characterId}; // taille (int) + id + characterId
			launcherCo.send(bytes);
			
			Character.log("Deconnection from Dofus client.");
			clientDofusCo.closeClient();
			Thread.sleep(2000);
			lock.unlock();
			return CIM;
		} catch(Exception e) {
			Character.log("Interaction with Dofus client has failed, deconnection.");
			clientDofusCo.closeClient();
			lock.unlock();
			throw new FatalError(e);
		}
	}
	
	public static void hashMessage(ByteArray msg, int characterId) {
		lock.lock();
		ByteArray array = new ByteArray(msg.getSize() + 2);
		array.writeInt(1 + 1 + msg.getSize());
		array.writeByte(3); 
		array.writeByte(characterId);
		array.writeBytes(msg);
		launcherCo.send(array.bytes());
		byte[] buffer = new byte[ByteArray.BUFFER_DEFAULT_SIZE];
		int size;
		while(true) {
			try {
				size = launcherCo.receive(buffer, 10000); // timeout de 10 secondes
				if(size <= 0) {
					lock.unlock();
					throw new FatalError("Connection lost with the emulation launcher.");
				}
				array.setArray(buffer, size);
				if(size - 2 != array.readShort()) {
					lock.unlock();
					throw new FatalError("Missing bytes !"); // erreur qui n'est encore jamais arriv�e
				}
				break;
			} catch(SocketException e) {
				lock.unlock();
				throw new FatalError(e);
			}
		}
		lock.unlock();
		msg.setArray(array.bytesFromPos());
	}
	
	private static void shakeUpClient() {
		if(launcherCo == null) {
			Log.err("Impossible to shake up the launcher, connection lost or not initialized.");
			return;
		}
		Log.info("Shaking up the client.");
		byte[] array = {0, 0, 0, 1, 4}; // nombre d'octets � envoyer (int) + identifiant de l'action � effectuer
		lock.lock();
		launcherCo.send(array);
		lock.unlock();
	}
	
	private static void connectToLauncher() {
		Character.log("Connection to emulation launcher.");
		launcherCo = new Connection.Client(Main.LOCALHOST, Main.LAUNCHER_PORT);
		byte[] buffer = new byte[1]; // bool�en d'injection
		launcherCo.receive(buffer);
		if(buffer[0] == 0)
			Processes.injectDLL(Main.LIB_PATH, Main.BYPASS_EXE);
	}
	
	private static Message processMsgStack(LinkedList<Message> msgStack) {
		Message msg;
		while((msg = msgStack.poll()) != null) {
			Character.log("r", msg);
			if(msg.getId() == 6372)
				return msg;
		}
		return null;
	}
}