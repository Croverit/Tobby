package controller.informations;

import java.util.HashMap;
import java.util.Vector;

import gamedata.character.CharacterCharacteristicsInformations;
import gamedata.character.SpellItem;
import gamedata.d2p.ankama.Map;

public class CharacterInformations {
	public String login;
	public String password;
	public int serverId;
	public String characterName;
	public double characterId;
	public int currentCellId;
	public int currentDirection;
	public Map currentMap;
	public int regenRate;
	public int weight;
	public int weightMax;
	public int level;
	public int element;
	public CharacterCharacteristicsInformations stats;
	public HashMap<Integer,SpellItem> spellList; 
	public int spellToUpgrade;
	
	public CharacterInformations(String login, String password, int serverId, int element) {
		this.login = login;
		this.password = password;
		this.serverId = serverId;
		this.element = element;
		this.spellToUpgrade=161; //Fleche Magique par d�faut
	}
	
	public int missingLife() {
		if(stats == null)
			return -1;
		return this.stats.maxLifePoints - this.stats.lifePoints;
	}

	public boolean weightMaxAlmostReached() {
		if(this.weightMax - this.weight < this.weight * 0.05) // moins de 5% restant
			return true;
		return false;
	}
	
	public void loadSpellList(Vector<SpellItem> spells) {
		spellList = new HashMap<Integer, SpellItem>();
		for(SpellItem spellItem : spells)
			spellList.put(spellItem.spellId, spellItem);
	}
}