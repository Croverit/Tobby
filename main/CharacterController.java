package main;

import game.character.PlayerStatusEnum;
import game.currentmap.GameRolePlayGroupMonsterInformations;
import game.d2o.modules.MapPosition;
import game.d2p.MapsCache;
import game.d2p.ankama.MapPoint;
import game.d2p.ankama.MovementPath;
import game.fight.GameFightMonsterInformations;
import game.pathfinding.AreaRover;
import game.pathfinding.CellsPathfinder;
import game.pathfinding.Path;
import game.pathfinding.Pathfinder;
import informations.CharacterInformations;
import informations.FightContext;
import informations.RoleplayContext;

import java.util.Vector;

import utilities.Log;
import messages.EmptyMessage;
import messages.character.PlayerStatusUpdateRequestMessage;
import messages.context.ChangeMapMessage;
import messages.context.GameMapMovementRequestMessage;
import messages.context.GameRolePlayAttackMonsterRequestMessage;
import messages.fight.GameActionFightCastRequestMessage;
import messages.fight.GameFightReadyMessage;
import messages.fight.GameFightTurnFinishMessage;

public class CharacterController extends Thread {
	private static final boolean DEBUG = true;
	private Instance instance;
	public CharacterInformations infos;
	public RoleplayContext roleplayContext;
	public FightContext fightContext;
	public String currentPathName;
	public CellsPathfinder pathfinder;
	private int fightsCounter;
	
	private class State {
		private boolean state;
		
		public State(boolean state) {
			this.state = state;
		}
	}

	private State isLoaded; // entr�e en jeu et changement de map
	private State inMovement;
	private State inFight;
	private State inGameTurn;
	private State inRegeneration;
	private State dying;
	
	public CharacterController(Instance instance, String login, String password, int serverId) {
		this.instance = instance;
		this.infos = new CharacterInformations(login, password, serverId);
		this.roleplayContext = new RoleplayContext(this);
		this.fightContext = new FightContext(this);
		
		this.isLoaded = new State(false); // d�but du jeu et � chaque changement de map
		this.inMovement = new State(false); 
		this.inFight = new State(false); 
		this.inGameTurn = new State(false); 
		this.inRegeneration = new State(false); 
		this.dying = new State(false);
	}

	public void setCurrentMap(int mapId) {
		this.infos.currentMap = MapsCache.loadMap(mapId);
		this.pathfinder = new CellsPathfinder(this.infos.currentMap);
	}
	
	private boolean isFree() {
		return this.isLoaded.state && !this.inMovement.state && !this.inFight.state && !this.inRegeneration.state;
	}
	
	// seul le thread principal entre ici
	public synchronized void emit(Event event) {
		if(DEBUG)
			this.instance.log.p("Event emitted : " + event);
		switchEvent(event);
		notify();
	}
	
	private void switchEvent(Event event) {
		switch(event) {
			case CHARACTER_LOADED : this.isLoaded.state = true; break;
			case FIGHT_START : this.inFight.state = true; break;
			case FIGHT_END : this.inFight.state = false; break;
			case GAME_TURN_START : this.inGameTurn.state = true; break;
			case MONSTER_GROUP_RESPAWN : break; // juste une stimulation
			default : new FatalError("Unexpected event caught : " + event); break;
		}
	}
	
	// seul le CC entre ici
	private synchronized boolean waitSpecificState(State condition, boolean expectedState, int timeout) {
		long startTime = System.currentTimeMillis();
		long currentTime;
		while(condition.state != expectedState && (currentTime = System.currentTimeMillis() - startTime) < timeout) {
			try {
				wait(timeout - currentTime);
			} catch (Exception e) {
				this.dying.state = true;
				return false;
			}
		}
		if(condition.state == expectedState)
			return true;
		this.instance.log.p("TIMEOUT");
		return false; // si on ne l'a pas re�u � temps
	}
	
	// seul le CC entre ici
	private synchronized void waitAnyEvent() {
		try {
			wait();
		} catch(Exception e) {
			this.dying.state = true;
		}
	}

	private void waitState(int stateId) {
		switch(stateId) {
			case 0 : // free
				if(DEBUG)
					this.instance.log.p("Waiting for character to be free.");
				while(!this.dying.state && !isFree())
					waitAnyEvent();
				return;
			case 1 : // start fight
				if(DEBUG)
					this.instance.log.p("Waiting for fight beginning.");
				while(!this.dying.state && !this.inFight.state)
					if(!waitSpecificState(this.inFight, true, 2000))
						return;
				return;
			case 2 : // start game turn
				if(DEBUG)
					this.instance.log.p("Waiting for my game turn.");
				while(!this.dying.state && !this.inGameTurn.state && this.inFight.state)
					waitAnyEvent();	
				return;
			/*
			case 3 : // monster group respawn
				if(DEBUG)
					this.instance.log.p("Waiting for monster group respawn.");
				waitAnyEvent();	
				return;
			*/
		}
	}

	public void moveTo(int cellId, boolean changeMap) {
		waitState(0);

		if(this.infos.currentCellId == cellId) { // d�j� sur la cellule cible
			this.instance.log.p("Already on the target cell id.");
			return;
		}
		
		this.instance.log.p("Moving from " + this.infos.currentCellId + " to " + cellId + ".");

		pathfinder = new CellsPathfinder(this.infos.currentMap);
		Path path = pathfinder.compute(this.infos.currentCellId, cellId);
		MovementPath mvPath = CellsPathfinder.movementPathFromArray(path.toVector());
		mvPath.setStart(MapPoint.fromCellId(this.infos.currentCellId));
		mvPath.setEnd(MapPoint.fromCellId(cellId));
		
		this.instance.log.p("Sending movement request.");
	
		GameMapMovementRequestMessage GMMRM = new GameMapMovementRequestMessage();
		GMMRM.serialize(mvPath.getServerMovement(), this.infos.currentMap.id, instance.id);
		instance.outPush(GMMRM);
		this.inMovement.state = true;
		
		int duration = path.getCrossingDuration();

		try {
			Thread.sleep((long) (duration + (duration * 0.4))); // on attend d'arriver � destination
		} catch(Exception e) {
			this.dying.state = true;
			return;
		}
		
		this.instance.log.p("Target cell reached.");

		EmptyMessage EM = new EmptyMessage("GameMapMovementConfirmMessage");
		instance.outPush(EM);
		this.inMovement.state = false;
	}

	public void moveTo(int x, int y, boolean changeMap) {
		Vector<Integer> mapIds = MapPosition.getMapIdByCoord(x, y);
		if(mapIds.size() == 0)
			throw new Error("Invalid map coords.");
		moveTo(mapIds.get(0), changeMap);
	}

	public void changeMap(int direction) {
		waitState(0);

		this.instance.log.p("Moving to " + Pathfinder.directionToString(direction) + " map.");

		moveTo(pathfinder.getChangementMapCell(direction), true);
		
		if(this.dying.state)
			return;
		
		ChangeMapMessage CMM = new ChangeMapMessage();
		CMM.serialize(this.infos.currentMap.getNeighbourMapFromDirection(direction));
		instance.outPush(CMM);

		this.isLoaded.state = false; // on attend la fin du changement de map
	}

	public void regenerateLife() {
		waitState(0);
		
		int missingLife = this.infos.missingLife();
		this.instance.log.p("Missing life : " + missingLife + " life points.");
		if(missingLife > 0) {
			this.inRegeneration.state = true;
			this.instance.log.p("Break for life regeneration.");
			try {
				Thread.sleep(this.infos.regenRate * 100 * missingLife); // on attend de r�cup�rer toute sa vie
			} catch(Exception e) {
				this.dying.state = true;
				return;
			}
			this.inRegeneration.state = false;
		}
	}
	
	public boolean lookForFight() {
		//waitState(3);
		
		this.instance.log.p("Searching for monster group to fight.");
		Vector<GameRolePlayGroupMonsterInformations> monsterGroups;
		int monsterGroupsSize;
		while(true) {
			monsterGroups = this.roleplayContext.getMonsterGroups();
			monsterGroupsSize = monsterGroups.size();
			if(monsterGroupsSize > 0) {
				GameRolePlayGroupMonsterInformations monsterGroup = this.roleplayContext.getMonsterGroups().get((int) Math.random() * monsterGroupsSize);
				this.instance.log.p("Monster group on cell id " + monsterGroup.disposition.cellId + ".");
				if(launchFight(monsterGroup))
					return true;
				
				if(this.dying.state)
					return false;
			}
			else {
				this.instance.log.p("None monster group available on the map.");
				return false;
			}
		}
	}
	
	public boolean launchFight(GameRolePlayGroupMonsterInformations monsterGroup) {
		waitState(0);
		
		this.instance.log.p("Trying to take this monster group.");
		moveTo(monsterGroup.disposition.cellId, false);
		
		if(this.dying.state)
			return false;
		
		if(this.roleplayContext.getMonsterGroupCellId(monsterGroup) == this.infos.currentCellId) {
			this.instance.log.p("Monster group taken.");
			GameRolePlayAttackMonsterRequestMessage GRPAMRM = new GameRolePlayAttackMonsterRequestMessage();
			GRPAMRM.serialize(monsterGroup.contextualId);
			instance.outPush(GRPAMRM);
			return true;
		}
		return false;
	}
	
	public void fight(boolean fightRecovery) {
		this.instance.startFight(); // lancement de la FightFrame (� mettre en premier)
		waitState(1);
		if(!fightRecovery) { // si c'est un combat tout frais
			try {
				Thread.sleep(1000); // pour para�tre plus naturel lors du lancement du combat
			} catch(Exception e) {
				this.dying.state = true;
				return;
			}
			GameFightReadyMessage GFRM = new GameFightReadyMessage();
			GFRM.serialize();
			this.instance.outPush(GFRM);
		}
		while(!this.dying.state && this.inFight.state) {
			waitState(2); // attente du d�but du prochain tour ou de la fin du combat
			if(!this.inFight.state)
				break;
			launchSpell();
			
			if(this.dying.state)
				return;
			
			concludeGameTurn();
		}
		this.instance.log.p("Number of fights done : " + ++this.fightsCounter);
	}

	public void launchSpell() {
		Vector<GameFightMonsterInformations> aliveMonsters = this.fightContext.getAliveMonsters();
		for(GameFightMonsterInformations aliveMonster : aliveMonsters) {
			if(this.fightContext.self.stats.actionPoints >= 4) {
				this.instance.log.p("Launching a spell.");
				GameActionFightCastRequestMessage GAFCRM = new GameActionFightCastRequestMessage();
				GAFCRM.serialize(161, (short) aliveMonster.disposition.cellId, this.instance.id);
				instance.outPush(GAFCRM);	
			}
			else
				break;
			try {
				Thread.sleep(1000); // important pour le moment sinon bug
			} catch(InterruptedException e) {
				this.dying.state = true;
				return;
			}
		}
	}
	
	public void concludeGameTurn() {
		GameFightTurnFinishMessage GFTFM = new GameFightTurnFinishMessage();
		GFTFM.serialize();
		this.instance.outPush(GFTFM);
		this.inGameTurn.state = false;
	}
	
	private void changePlayerStatus() {
		PlayerStatusUpdateRequestMessage PSURM = new PlayerStatusUpdateRequestMessage();
		PSURM.serialize(PlayerStatusEnum.PLAYER_STATUS_AFK);
		this.instance.outPush(PSURM);
		this.instance.log.p("Passing in away mode.");
	}
	
	public void run() {
		waitState(0);
		
		changePlayerStatus();
		
		if(this.inFight.state) // reprise de combat � la reconnexion
			fight(true);
		
		AreaRover areaRover = null;
		if(!this.dying.state)
			areaRover = new AreaRover(445, this); // bouftous d'incarnam
		
		while(!this.dying.state) { 
			regenerateLife();
			
			if(this.dying.state)
				break;
			
			if(lookForFight()) {
				waitState(1);
				
				if(this.dying.state)
					break;
				
				if(this.inFight.state) // on v�rifie si le combat a bien �t� lanc�
					fight(false);
				else
					changeMap(areaRover.nextMap(this));
			}
			else {
				if(this.dying.state)
					break;
				
				changeMap(areaRover.nextMap(this));
			}
		}
		this.instance.log.p(Log.Status.CONSOLE, "Thread controller of instance with id = " + this.instance.id + " terminated.");
	}
}