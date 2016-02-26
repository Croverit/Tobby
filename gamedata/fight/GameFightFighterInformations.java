package gamedata.fight;

import gamedata.currentmap.GameContextActorInformations;

import java.util.Vector;

import utilities.ByteArray;

public class GameFightFighterInformations extends GameContextActorInformations{
	public int teamId = 2;

	public int wave = 0;

	public boolean alive = false;

	public GameFightMinimalStats stats;

	public Vector<Integer> previousPositions;

	public GameFightFighterInformations(ByteArray buffer) {

		super(buffer);
		int loc5 = 0;
		this.previousPositions = new Vector<Integer>();
		this.teamId = buffer.readByte();
		this.wave = buffer.readByte();
		this.alive = buffer.readBoolean();
		int type = buffer.readShort();
		switch(type){
		case 31: 
			this.stats=new GameFightMinimalStats(buffer);
			break;
		case 360:
			this.stats=new GameFightMinimalStatsPreparation(buffer);
			break;
		}
		int loc3 = buffer.readShort();
		int loc4 = 0;
		while(loc4 < loc3)
		{
			loc5 = buffer.readVarShort();
			this.previousPositions.add(loc5);
			loc4++;
		}
	}

}