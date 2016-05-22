package controller.characters;

import frames.Processor;
import gamedata.character.PlayerStatus;
import gamedata.d2p.ankama.Map;
import gamedata.enums.BreedEnum;

import java.util.Hashtable;

import controller.CharacterBehaviour;
import controller.CharacterState;
import controller.informations.CharacterInformations;
import controller.informations.FightContext;
import controller.informations.RoleplayContext;
import controller.informations.StorageContent;
import controller.modules.ExchangeManager;
import controller.modules.FightAPI;
import controller.modules.InteractionAPI;
import controller.modules.MovementAPI;
import controller.modules.PartyManager;
import controller.modules.SalesManager;
import main.Controller;
import main.FatalError;
import main.Log;
import main.Main;
import main.NetworkInterface;
import messages.NetworkMessage;
import messages.character.BasicWhoIsRequestMessage;
import messages.character.PlayerStatusUpdateRequestMessage;

public abstract class Character extends Thread {
	private Hashtable<CharacterState, Boolean> states;
	public int id; // identifiant du personnage
	public Log log; // gestion des logs (fichier + historique graphique)
	public Thread[] threads; // tableau contenant les 4 threads du personnage
	public NetworkInterface net; // gestion de la connexion r�seau
	public Processor processor; // entit� charg�e du traitement des messages
	public CharacterInformations infos;
	public StorageContent inventory;
	public StorageContent bank;
	public RoleplayContext roleplayContext;
	public FightContext fightContext;
	public ExchangeManager exchangeManager;
	public SalesManager salesManager;
	public PartyManager partyManager;
	public MovementAPI mvt;
	public FightAPI fight;
	public InteractionAPI interaction;
	
	public static Character create(int id, int behaviour, String login, String password, int serverId, int areaId, Log log) {
		switch(behaviour) {
			case CharacterBehaviour.LONE_WOLF : return new LoneFighter(id, login, password, serverId, BreedEnum.Cra, areaId, log);
			case CharacterBehaviour.CAPTAIN : return new Captain(id, login, password, serverId, BreedEnum.Cra, areaId, log);
			case CharacterBehaviour.SOLDIER : return new Soldier(id, login, password, serverId, BreedEnum.Cra, log);
			default : throw new FatalError("Unknown behaviour.");
		}
	}

	protected Character(int id, String login, String password, int serverId, int breed, int areaId, Log log) {
		super(login + "/controller");
		this.id = id;
		
		// initialisation des modules principaux
		this.log = log;
		this.net = new NetworkInterface(this, login);
		this.processor = new Processor(this, login);
		
		// initialisation des threads
		this.threads = new Thread[4];
		this.threads[0] = this.net;
		this.threads[1] = this.net.sender;
		this.threads[2] = this.processor;
		this.threads[3] = this;
		
		// initialisation des modules du contr�leur
		this.infos = new CharacterInformations(login, password, serverId, breed);
		this.inventory = new StorageContent();
		this.bank = new StorageContent();
		this.roleplayContext = new RoleplayContext(this);
		this.fightContext = new FightContext(this);
		this.exchangeManager = new ExchangeManager(this);
		this.salesManager = new SalesManager(this);
		this.partyManager = new PartyManager(this);
		this.mvt = new MovementAPI(this);
		this.fight = new FightAPI(this, areaId);
		this.interaction = new InteractionAPI(this);
		
		// initialisation de la table des �tats
		this.states = new Hashtable<CharacterState, Boolean>();
		for(CharacterState state : CharacterState.values())
			this.states.put(state, false);
		
		// lancement de la thread de traitement (qui va lancer les autres threads le moment venu)
		this.processor.start();
		
		Log.info("Character with id = " + this.id + " started.");
	}
	
	@Override
	public abstract void run(); // impl�ment�e par les diff�rents personnages
	
	public static void log(String msg) {
		Log log = Controller.getInstance().getLog();
		if(log != null)
			log.p(msg);
		else
			Log.info(msg);
	}
	
	public static void log(String direction, NetworkMessage msg) {
		Log log = Controller.getInstance().getLog();
		if(log != null)
			log.p(direction, msg);
		else
			Log.info(msg.toString());
	}
	
	// destruction des threads du personnage depuis la GUI (forc�e ou non)
	public void deconnectionOrder(boolean forced) {
		if(forced) {
			this.net.sender.interrupt(); // on interrompt d'abord le sender pour �viter une exception
			this.net.closeReceiver();
			this.processor.interrupt();
			interrupt();
		}
		else
			updateState(CharacterState.SHOULD_DECONNECT, true);
	}
	
	public boolean isActive() {
		for(Thread thread : this.threads)
			if(thread.isAlive())
				return true;
		return false;
	}

	public void updatePosition(Map map, int cellId) {
		this.mvt.updatePosition(map, cellId);
	}
	
	// change le statut du personnage
	public void changePlayerStatus(int status) {
		PlayerStatusUpdateRequestMessage PSURM = new PlayerStatusUpdateRequestMessage();
		PSURM.status = new PlayerStatus(status);
		this.net.send(PSURM);
		this.log.p("Passing in away mode.");
	}

	// envoie une requ�te WHOIS pour savoir si le mod�rateur du serveur est en ligne
	protected void checkIfModeratorIsOnline(String moderatorName) {
		BasicWhoIsRequestMessage BWIRM = new BasicWhoIsRequestMessage();
		BWIRM.verbose = true;
		BWIRM.search = moderatorName;
		this.net.send(BWIRM);
		this.log.p("Checking if moderator is online.");
		waitState(CharacterState.WHOIS_RESPONSE);
	}

	// seul le thread de traitement entre ici
	public synchronized void updateState(CharacterState state, boolean newState) {
		this.log.p("State updated : " + state + " = " + newState + ".");
		this.states.put(state, newState);
		notify();
	}

	public boolean inState(CharacterState state) {
		return this.states.get(state);
	}

	// seul le contr�leur entre ici
	public boolean waitState(CharacterState state, int timeout) {
		Condition condition = null;
		boolean isEvent = false;
		boolean forbiddenTimeout = false;
		switch(state) {
			case IN_GAME_TURN : // �tat avec contrainte
				this.log.p("Waiting for my game turn.");
				condition = new Condition(state, 0); // mort du perso dans le combat
				condition.addConstraint(CharacterState.IN_FIGHT, false);
				break;
			case IS_LOADED : // �tat simple
				this.log.p("Waiting for character to be loaded.");
				condition = new Condition(state, 60000);
				forbiddenTimeout = true;
				break;
			case PENDING_DEMAND : // �tat simple
				this.log.p("Waiting for exchange demand.");
				condition = new Condition(state, 0);
				condition.addConstraint(CharacterState.SHOULD_DECONNECT, true); // tant qu'on ne re�oit pas d'ordre de d�connexion
				break;
			case IN_FIGHT : // �tat simple
				this.log.p("Waiting for fight beginning.");
				condition = new Condition(state, 5000);
				break;
			case IN_EXCHANGE : // �tat simple
				this.log.p("Waiting for exchange acceptance.");
				condition = new Condition(state, 5000);
				break;
			case NOT_IN_EXCHANGE : // �tat inverse
				this.log.p("Waiting for leaving exchange.");
				condition = new Condition(CharacterState.IN_EXCHANGE, false, 5000);
				forbiddenTimeout = true;
				break;
			case IN_PARTY : // �tat simple
				this.log.p("Waiting for joining party.");
				condition = new Condition(state, 30000);
				forbiddenTimeout = true;
				break;
			case NOT_IN_PARTY : // �tat inverse
				this.log.p("Waiting for leaving party.");
				condition = new Condition(CharacterState.IN_PARTY, false, 30000);
				forbiddenTimeout = true;
				break;
			case DIALOG_DISPLAYED : // �tat simple
				this.log.p("Waiting for dialog to be displayed.");
				condition = new Condition(state, 30000);
				forbiddenTimeout = true;
				break;
			case EXCHANGE_VALIDATED_BY_PEER : // �tat simple
				this.log.p("Waiting for exchange validation by peer.");
				condition = new Condition(state, 60000);
				condition.addConstraint(CharacterState.SHOULD_DECONNECT, true); // tant qu'on ne re�oit pas d'ordre de d�connexion
				forbiddenTimeout = true;
				break;
			case IN_REGENERATION : // �tat inverse avec timeout donn� (on attend juste la fin du timeout)
				this.log.p("Waiting for regeneration to be completed.");
				this.states.put(state, true);
				condition = new Condition(state, false, timeout);
				condition.addConstraint(CharacterState.SHOULD_DECONNECT, true); // tant qu'on ne re�oit pas d'ordre de d�connexion
				isEvent = true; // pas un event en fait
				break;
			case NEW_ACTOR_ON_MAP : // event
				this.log.p("Waiting for new actor on the map.");
				condition = new Condition(state, 0);
				isEvent = true;
				break;
			case CAPTAIN_ACT : // event
				this.log.p("Waiting for captain act.");
				condition = new Condition(state, 0);
				condition.addConstraint(CharacterState.SHOULD_DECONNECT, true); // tant qu'on ne re�oit pas d'ordre de d�connexion
				isEvent = true;
				break;
			case SOLDIER_ACT : // event
				this.log.p("Waiting for soldier act.");
				condition = new Condition(state, 0);
				condition.addConstraint(CharacterState.SHOULD_DECONNECT, true); // tant qu'on ne re�oit pas d'ordre de d�connexion
				isEvent = true;
				break;
			case NEW_PARTY_MEMBER : // event
				this.log.p("Waiting for party invitation acceptation.");
				condition = new Condition(state, 30000);
				isEvent = true;
				forbiddenTimeout = true;
				break;
			case FIGHT_LAUNCHED : // event
				this.log.p("Waiting for fight be launched by captain.");
				condition = new Condition(state, 30000);
				isEvent = true;
				forbiddenTimeout = true;
				break;
			case WHOIS_RESPONSE : // event
				this.log.p("Waiting for WHOIS response.");
				condition = new Condition(state, 30000);
				isEvent = true;
				forbiddenTimeout = true;
				break;
			case EXCHANGE_ACTION_RESPONSE : // event
				this.log.p("Waiting for exchange action response.");
				condition = new Condition(state, 20000);
				isEvent = true;
				forbiddenTimeout = true;
				break;
			case EXCHANGE_DEMAND_OUTCOME : // event
				this.log.p("Waiting for exchange demand outcome.");
				condition = new Condition(state, 5000);
				isEvent = true;
				break;
			case SPELL_CASTED : // event avec contrainte
				this.log.p("Waiting for result of spell cast.");
				condition = new Condition(state, 10000);
				condition.addConstraint(CharacterState.IN_GAME_TURN, false); // tant que le tour de jeu n'est pas termin�
				isEvent = true;
				break;
			case NEW_ACTOR_IN_FIGHT : // event avec contrainte
				this.log.p("Waiting for soldier join fight.");
				condition = new Condition(state, 0);
				condition.addConstraint(CharacterState.IN_GAME_TURN, true);
				isEvent = true;
				break;
			case LEVEL_UP : // �tat ponctuel
			case NEED_TO_EMPTY_INVENTORY : // �tat ponctuel
			case SHOULD_DECONNECT : // �tat ponctuel
				throw new FatalError("Impossible to wait the one-time state : " + state + ".");
		}
		boolean result = waitFor(condition);
		if(isEvent)
			this.states.put(condition.expectedState, false);
		if(isInterrupted())
			return false;
		if(forbiddenTimeout && !result) // si le timeout est interdit pour cet �tat
			throw new FatalError("Timeout reached and forbidden for the state : " + state + ".");
		return result;
	}

	public boolean waitState(CharacterState state) {
		return waitState(state, 0);
	}

	// retourne false lors d'un timeout, d'une contrainte vraie ou d'une interruption
	private synchronized boolean waitFor(Condition condition) {
		boolean infiniteWaiting = condition.timeout == 0;
		if(infiniteWaiting)
			condition.timeout = 120000; // 2 minutes
		long startTime = System.currentTimeMillis();
		long currentTime;
		while(!isInterrupted()) { // n'entre m�me pas dans la boucle si le thread est en cours d'interruption
			while((currentTime = System.currentTimeMillis() - startTime) < condition.timeout) {
				if(!condition.type) {
					// condition � un �tat
					if(this.states.get(condition.expectedState) == condition.expectedValueForExpectedState)
						return true;
					// condition � un �tat avec une contrainte
					if(condition.otherState != null && this.states.get(condition.otherState) == condition.expectedValueForOtherState)
						return false;
				}
				else // condition � 2 �tats
					if(this.states.get(condition.expectedState) == condition.expectedValueForExpectedState && this.states.get(condition.otherState) == condition.expectedValueForOtherState)
						return true;
				try {
					wait(condition.timeout - currentTime);
				} catch(Exception e) {
					interrupt();
					return false;
				}
			}
			if(infiniteWaiting) {
				//sendPingRequest();
				if(this.infos.isInGame())
					checkIfModeratorIsOnline(Main.MODERATOR_NAME); // requ�te effectu�e toutes les 2 minutes
				startTime = System.currentTimeMillis();
			}
			else {
				this.log.p("TIMEOUT");
				return false; // si on ne l'a pas re�u � temps
			}
		}
		return false;
	}

	private static class Condition {
		private CharacterState expectedState;
		private boolean expectedValueForExpectedState;
		private CharacterState otherState;
		private boolean expectedValueForOtherState;
		private int timeout;
		private boolean type; // true = second state, false = simple state or constraint state

		private Condition(CharacterState state, boolean expectedValue, int timeout) {
			this.expectedState = state;
			this.expectedValueForExpectedState = expectedValue;
			this.timeout = timeout;
			this.otherState = null;
			this.type = false;
		}

		private Condition(CharacterState state, int timeout) {
			this(state, true, timeout);
		}

		@SuppressWarnings("unused")
		private void addSecondState(CharacterState state, boolean expectedValue) {
			this.otherState = state;
			this.expectedValueForOtherState = expectedValue;
			this.type = true;
		}

		private void addConstraint(CharacterState state, boolean expectedValue) {
			this.otherState = state;
			this.expectedValueForOtherState = expectedValue;
			this.type = false;
		}
	}
}