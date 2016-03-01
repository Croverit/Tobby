package controller;

import gamedata.character.Elements;
import gamedata.currentmap.GameRolePlayGroupMonsterInformations;
import gamedata.fight.GameFightMonsterInformations;

import java.util.Vector;

import controller.informations.FightContext;
import main.FatalError;
import main.Instance;
import main.Log;
import messages.EmptyMessage;
import messages.character.SpellUpgradeRequestMessage;
import messages.character.StatsUpgradeRequestMessage;
import messages.context.GameRolePlayAttackMonsterRequestMessage;
import messages.exchange.ExchangeObjectMoveKamaMessage;
import messages.exchange.ExchangePlayerRequestMessage;
import messages.exchange.ExchangeReadyMessage;
import messages.fight.GameActionFightCastRequestMessage;
import messages.fight.GameFightReadyMessage;
import messages.fight.GameFightTurnFinishMessage;

public class FighterController extends CharacterController {
	private MovementController.AreaRover areaRover;
	private MuleController mule;
	public FightContext fightContext;

	public FighterController(Instance instance, String login, String password, int serverId) {
		super(instance, login, password, serverId);
		this.fightContext = new FightContext(this);
	}
	
	public void setMule(MuleController mule) {
		this.mule = mule;
	}
	
	public void regenerateLife() {
		waitState(CharacterState.IS_FREE);
		
		int missingLife = this.infos.missingLife();
		this.instance.log.p("Missing life : " + missingLife + " life points.");
		if(missingLife > 0) {
			updateState(CharacterState.IN_REGENERATION, true);
			this.instance.log.p("Break for life regeneration.");
			try {
				sleep(this.infos.regenRate * 100 * missingLife); // on attend de r�cup�rer toute sa vie
			} catch(Exception e) {
				interrupt();
				return;
			}
			updateState(CharacterState.IN_REGENERATION, false);
		}
	}
	
	
	
	
	//
	
	
	
	private void upgradeSpell() {
		waitState(CharacterState.IS_FREE);
		int id=infos.spellToUpgrade;
		if(inState(CharacterState.LEVEL_UP) && infos.spellList.get(id) != null && canUpgradeSpell(id)) {
			infos.spellList.get(id).spellLevel++;
			SpellUpgradeRequestMessage SURM = new SpellUpgradeRequestMessage();
			SURM.serialize(id, infos.spellList.get(id).spellLevel);
			instance.outPush(SURM);
			this.instance.log.p("Increase spell \"Fl�che Magique\" to level " + infos.spellList.get(161).spellLevel);
		}
	}

	private boolean canUpgradeSpell(int idSpell) {
		int lvl=infos.spellList.get(idSpell).spellLevel;
		if(lvl<5){
			return infos.stats.spellsPoints>=lvl;
		}
		return false;
	}
	
	private void upgradeStats() {
		waitState(CharacterState.IS_FREE);
		
		if(inState(CharacterState.LEVEL_UP)) {
			StatsUpgradeRequestMessage SURM = new StatsUpgradeRequestMessage();
			SURM.serialize(this.infos.element, calculateMaxStatsPoints());
			instance.outPush(SURM);
			updateState(CharacterState.LEVEL_UP, false);
			this.instance.log.p("Increase stat : " + Elements.intelligence + " of " + this.infos.stats.statsPoints + " points.");
		}
	}
	
	private int calculateMaxStatsPoints() {
		int stage=(getElementInfoById()/100)+1;
		return infos.stats.statsPoints-(infos.stats.statsPoints%stage);
	}

	private int getElementInfoById() {
		switch(infos.element){
			case 10: return infos.stats.strength.base;
			case 13: return infos.stats.chance.base ;
			case 14: return infos.stats.agility.base;
			case 15: return infos.stats.intelligence.base;
		}
		return 0;
	}
	
	
	
	//
	
	
	
	private boolean lookForAndLaunchFight() {
		waitState(CharacterState.IS_FREE);
		
		this.instance.log.p("Searching for monster group to fight.");
		Vector<GameRolePlayGroupMonsterInformations> monsterGroups;
		int monsterGroupsSize;
		while(true) {
			monsterGroups = this.roleplayContext.getMonsterGroups();
			monsterGroupsSize = monsterGroups.size();
			if(monsterGroupsSize > 0) {
				GameRolePlayGroupMonsterInformations monsterGroup = this.roleplayContext.getMonsterGroups().get((int) Math.random() * monsterGroupsSize);
				this.instance.log.p("Going to take a monster group on cell id " + monsterGroup.disposition.cellId + ".");
				this.mvt.moveTo(monsterGroup.disposition.cellId, false);
				if(isInterrupted())
					return false;
				this.instance.log.p("Sending attack request.");
				GameRolePlayAttackMonsterRequestMessage GRPAMRM = new GameRolePlayAttackMonsterRequestMessage();
				GRPAMRM.serialize(monsterGroup.contextualId);
				instance.outPush(GRPAMRM);
				return true;
			}
			else {
				this.instance.log.p("None monster group available on the map.");
				return false;
			}
		}
	}
	
	private void fight(boolean fightRecovery) {
		this.instance.startFight(); // lancement de la FightFrame (� mettre en premier)
		if(!fightRecovery) { // si c'est un combat tout frais
			try {
				sleep(1000); // pour para�tre plus naturel lors du lancement du combat
			} catch(Exception e) {
				interrupt();
				return;
			}
			GameFightReadyMessage GFRM = new GameFightReadyMessage();
			GFRM.serialize();
			this.instance.outPush(GFRM);
		}
		while(!isInterrupted() && inState(CharacterState.IN_FIGHT)) {
			waitState(CharacterState.IN_GAME_TURN); // attente du d�but du prochain tour ou de la fin du combat
			if(!inState(CharacterState.IN_FIGHT))
				break;
			launchSpell();
			
			if(isInterrupted())
				return;
			
			if(inState(CharacterState.IN_FIGHT)) {
				GameFightTurnFinishMessage GFTFM = new GameFightTurnFinishMessage();
				GFTFM.serialize();
				this.instance.outPush(GFTFM);
				updateState(CharacterState.IN_GAME_TURN, false);
			}
		}
		this.infos.fightsCounter++;
	}

	private void launchSpell() {
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
				sleep(1000); // important pour le moment sinon bug
			} catch(InterruptedException e) {
				interrupt();
				return;
			}
		}
	}
	
	private void selectAreaRoverDependingOnLevel() {
		this.instance.log.p("Character level : " + this.infos.level + ".");
		if(this.infos.level < 8)
			this.areaRover = new MovementController.AreaRover(450, this); // route des �mes d'Incarnam
		//else if(this.infos.level < 10)
			//this.areaRover = new MovementAPI.AreaRover(442, this); // lac d'Incarnam
		else
			this.areaRover = new MovementController.AreaRover(445, this); // p�turages d'Incarnam
		//this.areaRover = new AreaRover(95, this); // pious d'Astrub
	}
	
	private void goToExchangeWithMule(boolean giveKamas) {
		waitState(CharacterState.IS_FREE);
		if(isInterrupted())
			return;
		
		if(this.infos.currentMap.id != this.mule.waitingMapId)
			this.mvt.goTo(this.mule.waitingMapId);
		
		while(!isInterrupted() && !this.roleplayContext.actorIsOnMap(this.mule.infos.characterId)) {
			waitState(CharacterState.NEW_ACTOR_ON_MAP); // attendre que la mule revienne sur la map
			updateState(CharacterState.NEW_ACTOR_ON_MAP, false);
		}
		if(isInterrupted())
			return;
		
		ExchangePlayerRequestMessage EPRM = new ExchangePlayerRequestMessage(); // demande d'�change
		EPRM.serialize(this.mule.infos.characterId, 1, this.instance.id);
		this.instance.outPush(EPRM);
		this.instance.log.p("Sending exchange demand.");
		waitState(CharacterState.IN_EXCHANGE); // attendre l'acceptation de l'�change
		if(isInterrupted())
			return;
		
		try {
			sleep(2000); // on attend un peu que la fen�tre d'�change apparaisse
		} catch (InterruptedException e) {
			interrupt();
			return;
		}
		
		EmptyMessage EM = new EmptyMessage("ExchangeObjectTransfertAllFromInvMessage"); // on transf�re tous les objets
		this.instance.outPush(EM);
		this.instance.log.p("Transfering all objects.");
		ExchangeObjectMoveKamaMessage EOMKM = new ExchangeObjectMoveKamaMessage(); // et les kamas
		EOMKM.serialize(this.infos.stats.kamas);
		this.instance.outPush(EOMKM);
		
		try {
			sleep(5000); // on attend de pouvoir valider l'�change
		} catch (InterruptedException e) {
			interrupt();
			return;
		}
		
		ExchangeReadyMessage ERM = new ExchangeReadyMessage();
		ERM.serialize(true, 2); // car il y a eu 2 actions lors de l'�change
		this.instance.outPush(ERM); // on valide de notre c�t�
		this.instance.log.p("Exchange validated from my side.");
		
		waitState(CharacterState.IS_FREE);
		if(this.roleplayContext.lastExchangeResult) {
			updateState(CharacterState.NEED_TO_EMPTY_INVENTORY, false);
			this.instance.log.p("Exchange with mule terminated successfully.");
		}
		else
			throw new FatalError("Exchange with mule has failed.");	
	}
	
	public void run() {
		waitState(CharacterState.IS_LOADED);
		
		changePlayerStatus();
		 
		if(waitState(CharacterState.IN_FIGHT)) // on attend 2 secondes de savoir si on est en combat ou pas
			fight(true); // reprise de combat
		
		while(!isInterrupted()) { // boucle principale 
			selectAreaRoverDependingOnLevel(); // se rend � l'aire de combat
			
			while(!isInterrupted() && !inState(CharacterState.NEED_TO_EMPTY_INVENTORY)) { // boucle recherche & combat
				
				upgradeSpell();  //On upgrade le sort avant les stats
				upgradeStats();
				if(isInterrupted())
					break;

				regenerateLife();
				if(isInterrupted())
					break;
				
				if(lookForAndLaunchFight()) {
					if(isInterrupted())
						break;
					
					if(waitState(CharacterState.IN_FIGHT)) // on v�rifie si le combat a bien �t� lanc� (avec timeout)
						fight(false);
					else
						this.mvt.changeMap(this.areaRover.nextMap());
				}
				else {
					if(isInterrupted())
						break;
					
					this.mvt.changeMap(this.areaRover.nextMap());
				}
			}
			if(isInterrupted())
				break;
			
			if(waitState(CharacterState.MULE_AVAILABLE))
				goToExchangeWithMule(true);
			else
				sendPingRequest();
		}
		this.instance.log.p(Log.Status.CONSOLE, "Thread controller of instance with id = " + this.instance.id + " terminated.");
	}
}